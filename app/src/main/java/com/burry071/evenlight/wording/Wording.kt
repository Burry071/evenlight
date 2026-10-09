package com.burry071.evenlight.wording

import com.burry071.evenlight.core.EveningRecord
import com.burry071.evenlight.core.Verdict
import com.burry071.evenlight.core.Verifier

object Wording {
    const val SYSTEM =
        "You write one short line of plain English about an evening sky. " +
        "Use the name you are given. Do not use any digit, hash sign, bracket, or newline. " +
        "Do not invent a colour, an object, a person, or a place. " +
        "Reply with one lowercase line of four to eight words and nothing else."

    /** The name is handed over, never asked for: choosing measured 1/6 consistent (spec section 7). */
    fun prompt(name: String, bands: List<String>, altitudeDeg: Int): String = buildString {
        appendLine("name: $name")
        appendLine("bands: ${bands.joinToString(" ")}")
        appendLine("sun: ${altitudeDeg} degrees")
        append("Write one line of four to eight words that contains the word $name. ")
        append("Do not use any digit and do not add a colour that is not listed.")
    }

    /**
     * The fallback line, and tonight's only line. It carries the name the matcher chose and claims nothing else,
     * so it passes Verifier.check by construction (spec section 7).
     */
    fun template(name: String): String = "$name light, measured from this sky"
}

/**
 * The one place the three wording provenances are decided, kept as a pure function over `:core` types so all three
 * branches and the "nothing else changes" promise are testable on a plain JVM - no Android, no Bitmap, no engine.
 * The controller calls it after the card is filed; whatever it returns is what the rewritten record must say.
 *
 * A rejected answer is still an answer: it lands on the template line but with `wording_source: "template"` and
 * `wording_rejected: true`, so the rejection counts in `Store.wordingRejected() / wordingTotal()`, the ratio the
 * README reports. Only a model that was never consulted (or never replied) produces `"none"`.
 */
fun applyWording(record: EveningRecord, candidate: String?, name: String): EveningRecord {
    val verdict = candidate?.let { Verifier.check(it, name) }
    return when {
        candidate != null && verdict is Verdict.Accept ->
            record.copy(wording = candidate, wordingSource = "model", wordingRejected = false)
        candidate != null ->
            record.copy(wording = Wording.template(name), wordingSource = "template", wordingRejected = true)
        else ->
            record.copy(wording = Wording.template(name), wordingSource = "none", wordingRejected = false)
    }
}
