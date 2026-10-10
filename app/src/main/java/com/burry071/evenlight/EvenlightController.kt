package com.burry071.evenlight

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.res.ResourcesCompat
import com.burry071.evenlight.card.CardRenderer
import com.burry071.evenlight.card.headerFor
import com.burry071.evenlight.capture.FrameSource
import com.burry071.evenlight.capture.Still
import com.burry071.evenlight.core.BandSampler
import com.burry071.evenlight.core.EveningRecord
import com.burry071.evenlight.core.GeoPoint
import com.burry071.evenlight.core.Matcher
import com.burry071.evenlight.core.Pixels
import com.burry071.evenlight.core.SunWindow
import com.burry071.evenlight.data.Store
import com.burry071.evenlight.wording.EngineHolder
import com.burry071.evenlight.wording.Wording
import com.burry071.evenlight.wording.WordingEngine
import com.burry071.evenlight.wording.applyWording
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * Two clocks, injected rather than called directly. `SystemClock.elapsedRealtime()` was the obvious monotonic
 * source and is the wrong one: with `unitTests.isReturnDefaultValues = true` it reads 0 in every JVM test, so a
 * drift test would pass by proving nothing. System.nanoTime() is monotonic, JVM-visible, and only its deltas are
 * used.
 */
class Timebase(val wallClock: () -> LocalDateTime, val monotonicMillis: () -> Long) {
    companion object {
        val SYSTEM = Timebase({ LocalDateTime.now() }, { System.nanoTime() / 1_000_000 })
    }
}

class EvenlightController(
    private val store: Store,
    // The card is a byte producer here rather than a CardRenderer: the pixels of a 1080x1440 Bitmap cannot be
    // made on a JVM, but the ORDER this file guarantees - record, raw, card, and card-then-record again on the
    // upgrade - is testable when the render step is a function the test can observe. forDevice binds it to
    // CardRenderer plus PNG compression; nothing about what is drawn changed.
    private val card: (List<Int>, CardRenderer.Header) -> ByteArray,
    private val frame: suspend (rotationDegrees: Int) -> Still,
    private val time: Timebase,
    private val displayRotation: () -> Int,
    private val engine: WordingEngine
) {

    /**
     * Two reads of one wall clock around one shutter press, so what this catches is a jump that lands *during* the
     * capture - an NTP correction at 17:59 - and what it cannot catch is a clock rewound before the app started.
     *
     * It catches less than that, and the earlier version of this comment was wrong to imply otherwise: it said the
     * stored shutter name is what defends the stack's order, but readRecords() orders by the filename, and the
     * filename is built from this same wall clock. A clock rewound last Tuesday therefore really does move that
     * evening's row in the stack, and nothing in the app notices. So this flag is a mark on one row saying "the
     * two clocks disagreed here" (spec section 11) - not a guard on the stack, and not a diagnosis either.
     *
     * A third thing trips it: System.nanoTime() is CLOCK_MONOTONIC and does not advance while the device sleeps,
     * while LocalDateTime.now() does, so a phone that dozed between the two reads shows wall time gone by and
     * monotonic time not, and the row is marked for a reason that is not a clock fault at all.
     */
    private fun clockSuspect(now: LocalDateTime, monoBefore: Long): Boolean {
        val wallAdvance = Duration.between(now, time.wallClock()).toMillis()
        val monoAdvance = time.monotonicMillis() - monoBefore
        return wallAdvance < 0 || abs(wallAdvance - monoAdvance) > 5_000
    }

    /**
     * The filing half: capture -> decode -> sample -> match -> write record -> write raw -> write card, then hand
     * the record back. Everything in this block is blocking work - the JPEG read and bitmap decode inside the
     * frame capture, `BandSampler.sample`, the 893-row render, the PNG compress and three file writes - and it
     * all used to run on the thread that draws (finding 1.3); the whole body now runs on `Dispatchers.IO`.
     *
     * The order inside is load-bearing and unchanged: the record leads, because it is the datum and no later
     * failure may lose it (a render that OOMs cannot take the evening's only JSON with it), and nothing here
     * consults the engine - spec 8's reveal shows the card the shutter just filed, and the model's line is
     * `upgradeWording` arriving afterwards, not a gate in front of the reveal (finding 1.4). Settings are read
     * from the store per shot - the path `loadSettings` already serves the UI - so a place saved after this
     * controller was built is live on the next shutter without rebuilding (and re-closing) anything.
     */
    suspend fun fileShot(): Result<EveningRecord> {
        val outcome = runCatching {
            withContext(Dispatchers.IO) {
                // No coordinates, no card. The sun line would otherwise print an altitude computed for the Gulf of Guinea
                // over a sky in Lahore: a number the app did not measure, which this project does not put on a card.
                val settings = store.loadSettings()
                check(settings.isSet) { "no place set yet" }

                val monoBefore = time.monotonicMillis()
                val now = time.wallClock()

                val still = frame(FrameSource.rotationFor(displayRotation()))
                require(still.pixels.isNotEmpty()) { "capture produced no pixels" }

                val bands = BandSampler.sample(still.pixels, still.width, still.height)
                val match = Matcher.match(bands.ordered)
                val altitude = SunWindow.altitudeForDisplay(
                    now.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),
                    GeoPoint(settings.lat, settings.lon)
                )
                val date = now.toLocalDate().toString()
                val attempt = store.attemptsFor(date) + 1
                val suspect = clockSuspect(now, monoBefore)

                val record = EveningRecord(
                    date = date,
                    shutter = store.nextShutterName(now),
                    bandsHex = bands.ordered.map { Pixels.hex(it) },
                    winnerIndex = match.winnerIndex,
                    name = match.name,
                    sunAltitudeDeg = altitude,
                    meanLuminance = bands.meanLuminance,
                    wording = Wording.template(match.name),
                    wordingSource = "none",     // written unconsulted; the upgrade below rewrites it once the engine is consulted
                    wordingRejected = false,
                    attempt = attempt,
                    clockSuspect = suspect
                )
                store.writeRecord(record, attempt)          // the record leads: it is the datum, and no later failure may lose it
                store.writeRaw(still.jpeg, record.shutter, attempt)
                store.writeCard(date, card(bands.ordered, headerFor(record, match)))
                record
            }
        }
        // runCatching catches Throwable, which is what a shutter crash needs - but that includes the coroutine's own
        // cancellation, which would otherwise become a failed Result a destroyed window is then asked to draw into.
        // So cancellation travels back up and everything else is reported. Task 9 inherits this file, and a
        // coroutine that swallows its own cancellation stops being obvious once it is load-bearing.
        outcome.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        return outcome
    }

    /**
     * The best-effort upgrade half, run only after `fileShot` has revealed and filed the card (R16). The measured
     * engine cost - 4,566 ms init, 1,269 MB peak RSS, a 30 s give-up marker - means a model call ahead of the
     * write could lose the evening entirely to an LMK kill; behind it, the worst case is that tonight keeps the
     * template line the card was already filed with. Every throw in this block lands back on that filed record:
     * the rewrite is best-effort, and a failed best-effort is not a failed shot - except cancellation, which is
     * the caller's own and is rethrown.
     */
    suspend fun upgradeWording(filed: EveningRecord): EveningRecord = runCatching {
        withContext(Dispatchers.IO) {
            // `consulted` is ensure()'s actual return, not an assumption: "no model installed" and
            // "asked and got nothing back" are different facts, and the device run proved that
            // recording both as "none" makes the README's failure rate uncomputable (applyWording).
            val consulted = engine.ensure()
            val candidate = if (consulted) engine.line(filed.name, filed.bandsHex, filed.sunAltitudeDeg) else null
            val finalRecord = applyWording(filed, candidate, filed.name, consulted)
            // The reveal no longer waits for this call, so the shutter is live while it runs: a second shot filed
            // inside the model call (the README measures 1,080 / 1,247 / 3,502 ms per line) has already written
            // today's card and its own record. Rewriting over them from the FIRST shot's answer would leave
            // today's PNG showing an earlier attempt's bands and line - permanently, if that second attempt's own
            // upgrade turns out to be provenance-only and so never re-renders. Newest attempt wins the card; the
            // earlier record keeps its own (date, attempt) file and its own honest line.
            if (store.attemptsFor(filed.date) > filed.attempt) return@withContext filed
            if (finalRecord != filed) {
                if (finalRecord.wording != filed.wording) {
                    // Render into memory, then card, then record - the same order the pre-split shoot() kept.
                    // `card(...)` is the only step in this block that can throw from computation - OOM on a 3 GB
                    // device is not hypothetical, it is the memory regime the model itself creates - and doing it
                    // first means a throw leaves NEITHER write done: the evening keeps its template line and its
                    // template card, and the two agree. Rewriting the record before the card could instead leave
                    // a JSON claiming wording_source: model beside a PNG still showing the template. The remaining
                    // window - card written, record write lost - disagrees in the direction that understates the
                    // model, which is the side worth erring on.
                    // headerFor prints record.wording onto the card image, so a card and a record that disagree
                    // about the line is a visible bug: re-render, but only when the text changed. The bands and
                    // the Match are re-read from the stored record - the hexes are the same eight the filing half
                    // sampled, so the header's winner still comes from the RECORD, never from the recomputed match.
                    val bands = filed.bandsHex.map { Pixels.parseHex(it) }
                    val png = card(bands, headerFor(finalRecord, Matcher.match(bands)))
                    store.writeCard(filed.date, png)
                }
                // Same (date, attempt) path, so this overwrites the file the record write above created and
                // attemptsFor never sees two. A provenance-only change (no-answer, rejected) reaches here
                // without a re-render: the card already shows the template line the final record carries.
                store.writeRecord(finalRecord, filed.attempt)
            }
            finalRecord
        }
    }.getOrElse { if (it is CancellationException) throw it else filed }

    /**
     * Release the native engine when this controller is retired. It defers to EngineHolder's in-flight guard:
     * called while a shot is inside a native call it skips the close (a documented leak) rather than risk a
     * use-after-free, so this is always safe to call and not always effective.
     */
    fun close() {
        engine.close()
    }

    companion object {
        private fun pngBytes(bmp: Bitmap): ByteArray {
            val out = ByteArrayOutputStream()
            // Quality is meaningless for PNG; 100 says so rather than leaving a number that looks like a choice.
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            bmp.recycle()
            return out.toByteArray()
        }

        /**
         * The device-shaped controller. The strip name is laid out for Fraunces at 116f, and with `Typeface.DEFAULT`
         * it crowded the strip - which was the known cost of landing the nightly path one night, and is retired here.
         *
         * The display face comes from `R.font.fraunces_strip`, the `<font-family>` XML that pins Fraunces to weight
         * 400 at the design's strip variation, rather than from `R.font.fraunces_variable` directly: that file's
         * default instance is "Fraunces 9pt Black" (wght 900, opsz 9), so a raw load put every card name in Black.
         *
         * Loaded through `ResourcesCompat.getFont`, not `Resources.getFont`: the platform call throws
         * `Resources$NotFoundException: Font resource ID #0x7f070001` on a `<font-family>` XML, which is how this
         * crashed on first launch after the variation files landed. `ResourcesCompat` parses the family itself
         * (verified in core-1.15.0: no reference to `Resources.getFont` anywhere in the class) and is the same loader
         * Compose's `AndroidFontLoader` uses for `Font(R.font.…)`, so the card PNG and the screens resolve the one
         * variation rather than two.
         */
        fun forDevice(activity: Context, store: Store, frame: FrameSource): EvenlightController {
            val renderer = CardRenderer(
                // `ResourcesCompat.getFont` is @Nullable; a null here means the resource table is broken, and the
                // silent fallback to the system face is the exact bug this file exists to keep out of the card.
                requireNotNull(ResourcesCompat.getFont(activity, R.font.fraunces_strip)),
                requireNotNull(ResourcesCompat.getFont(activity, R.font.instrument_sans_variable))
            )
            return EvenlightController(
                store = store,
                card = { bands, header -> pngBytes(renderer.render(bands, header)) },
                frame = { rotation -> frame.take(rotation) },
                time = Timebase.SYSTEM,
                displayRotation = {
                    @Suppress("DEPRECATION")
                    (activity as? android.app.Activity)?.windowManager?.defaultDisplay?.rotation ?: 0
                },
                // Built here, not in MainActivity: the controller owns when the engine is consulted (after filing)
                // and that policy lives with the call. An uninitialised holder is one object plus a path check; the
                // 1.2 GB stays on disk until a shot finds weights present. A settings save no longer rebuilds this
                // controller at all (finding 1.5): the engine is not settings-dependent, so one holder lives as
                // long as the activity does and no second 1.269 GB engine can co-reside behind the in-flight guard.
                engine = EngineHolder(activity)
            )
        }
    }
}
