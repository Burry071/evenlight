package com.burry071.evenlight.wording

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the optional on-device Gemma engine: absent by default, lazy, and every failure route lands on
 * `Wording.template`. The caller (T11's controller wiring) treats `false`/`null` as "no answer", never as an error.
 *
 * Not an interface with a fake: nothing in `app/src/test` constructs a controller, so an abstraction here would
 * exist for a test that is not being written (R18). This is the concrete holder; the JVM-testable part of Task 11
 * is `applyWording`, which needs no engine.
 */
class EngineHolder(private val context: Context) {
    private var engine: Engine? = null
    private var ready = false

    /**
     * Latched by a timed-out `initialize()`, never cleared for the holder's life. Two reasons:
     *  - a 30 s timeout means the blocking native `initialize()` may STILL be running when we give up, and calling
     *    `close()` on that engine is the use-after-free risk you reach by pressing the shutter; so on timeout the
     *    engine is left alone (one leaked native handle is the cheaper side of that trade);
     *  - re-initialising a 1.2 GB model at every evening's shot after it already failed once is the cost the latch
     *    prevents. The holder is rebuilt by `forDevice` on a settings save, which re-arms it - the rare path, and
     *    a fresh holder then starts from a clean slate, as the brief's per-controller ownership implies.
     */
    private var timedOut = false

    /**
     * Set for the duration of any blocking native call - initialize, sendMessage, close - so `close()` below can
     * refuse to yank an engine out from under a call still running inside it.
     */
    @Volatile private var inFlight = false

    /** Measured on an x86_64 API-35 AVD: init 4,566 ms, peak RSS 1,269 MB, ~1.2 s per line. */
    val modelFile: File
        get() = File(context.filesDir, "models/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm")

    /**
     * True only once an engine has initialised. `initialize()` is blocking, which is why it runs on
     * `Dispatchers.Default` - and why the 30 s is a give-up marker, not a hard bound: `withTimeoutOrNull` cannot
     * interrupt a synchronous native call, so a hung init surfaces here only after it returns or the process dies.
     */
    suspend fun ensure(): Boolean {
        if (ready) return true
        if (timedOut) return false
        if (!modelFile.exists()) return false
        val e = Engine(EngineConfig(modelPath = modelFile.absolutePath, backend = Backend.CPU()))
        // null = timed out, false = threw, true = initialised. The distinction drives which cleanup is safe.
        inFlight = true
        val outcome: Boolean? = try {
            withContext(Dispatchers.Default) {
                try { withTimeoutOrNull(30_000) { e.initialize(); true } }
                catch (t: Throwable) {
                    // Cancellation is not an init failure: rethrow it out of the worker block so it travels up
                    // through withContext to the caller, matching the contract the controller already keeps by
                    // rethrowing at EvenlightController's two cancellation sites. Anything else stays swallowed -
                    // every genuine failure route lands on the template.
                    if (t is CancellationException) throw t
                    false
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) {
                // `e` gets the same treatment as the timed-out engine below: left alone. A cancelled coroutine
                // cannot prove the blocking native initialize() has unwound, and close() against it is the
                // use-after-free the timedOut comment already prices. One leaked handle is the cheaper side.
                throw t
            }
            false
        } finally {
            inFlight = false
        }
        when (outcome) {
            true -> { engine = e; ready = true }
            null -> timedOut = true // leave `e` alone: it may still be inside native initialize()
            else -> runCatching { e.close() }
        }
        return outcome == true
    }

    /**
     * Null when the engine gave no answer - threw or timed out - which the caller maps through `consulted` to
     * "no-answer" or "none". Cancellation is a third thing and is not swallowed here: it propagates, because a
     * cancelled caller is not a model that failed to answer.
     */
    suspend fun line(name: String, bands: List<String>, altitudeDeg: Int): String? {
        val e = engine ?: return null
        inFlight = true
        return try {
            withContext(Dispatchers.Default) {
                withTimeoutOrNull(10_000) {
                    // The guard is released inside the block that runs the native call, not in this function's
                    // `finally`: a give-up here cannot prove `sendMessage` has unwound, because it is blocking
                    // native code no cancellation interrupts - measured on an x86_64 AVD, a 10.5 s call still
                    // delivered its line past the 10 s marker, so the marker bounds nothing. Releasing from the
                    // caller would let close() free the engine under a call still running, which is the
                    // use-after-free the guard exists for. If the block is cancelled before it starts, the flag
                    // stays set and close() is skipped for the holder's life: a leak, which is the safe side.
                    try {
                        val cfg = ConversationConfig(
                            systemInstruction = Contents.of(Wording.SYSTEM),
                            samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.7)
                        )
                        // Conversation is AutoCloseable, not Closeable, so stdlib's `use` is not available without a
                        // kotlin.jdk7 import; try/finally is the same guarantee with no import fight.
                        val conv = e.createConversation(cfg)
                        try {
                            conv.sendMessage(Wording.prompt(name, bands, altitudeDeg))
                                .contents.contents.filterIsInstance<Content.Text>()
                                .joinToString(" ") { it.text }.trim().lowercase()
                        } finally {
                            runCatching { conv.close() }
                        }
                    } finally {
                        inFlight = false
                    }
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            null
        }
    }

    /**
     * Release the engine, but only while it is provably not inside a native call: the guard is claimed here and
     * held by initialize/sendMessage for their whole duration, and a close it cannot claim is skipped - a
     * documented leak, the same priced trade the `timedOut` comment above already makes for a timed-out init,
     * because close() against a live native call is a use-after-free at shutter speed. The check-then-set on a
     * volatile flag is not atomic; it rests on every caller here (the shoot path, and both MainActivity close
     * sites) dispatching from the main thread, so a close interleaves with a shot only at suspension points,
     * each of which the guard already spans. Its strength as a guard is unproven: no JVM test constructs an
     * EngineHolder (R18), so this is reasoned code, not covered code.
     */
    fun close() {
        if (inFlight) return
        inFlight = true
        try {
            runCatching { engine?.close() }
            engine = null
            ready = false
        } finally {
            inFlight = false
        }
    }
}
