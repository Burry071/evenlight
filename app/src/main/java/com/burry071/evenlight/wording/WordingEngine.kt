package com.burry071.evenlight.wording

/**
 * The seam `EvenlightController` consults the optional model through. `EngineHolder` is its only device
 * implementation; the controller takes this type so the JVM test (EvenlightControllerTest) can hand back a
 * fake that blocks, which is the only way to prove the reveal ordering finding 1.4 fixed: filing finishes and
 * the card is on disk while the engine is still inside `ensure()`.
 */
interface WordingEngine {
    /** True once an engine is initialised and askable; false means "no model installed", never "error". */
    suspend fun ensure(): Boolean

    /** Null when the engine gave no answer - threw or timed out. Cancellation propagates, it is not a no-answer. */
    suspend fun line(name: String, bands: List<String>, altitudeDeg: Int): String?

    /** Release the engine, deferring to the in-flight guard: always safe, not always effective. */
    fun close()
}
