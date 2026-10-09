package com.burry071.evenlight.core

sealed class Verdict {
    object Accept : Verdict()
    data class Reject(val reason: String) : Verdict()
}

object Verifier {
    // An allowlist, not a denylist. The old rule banned `# < { (` by name and the device answered
    // `the **ash** sky glowed softly as dusk fell.` - literal markdown asterisks, unnamed, onto the card.
    // A denylist has to be right every time; this rule only has to be right about the punctuation we
    // actually want, and neither the vocabulary nor Wording.template needs anything beyond these four
    // marks. The priced cost, accepted up front: a curly apostrophe or an em dash now counts as markup,
    // so the rejection rate rises - and the rejection rate is a reported result, not a bug to hide.
    private val allowed = ('a'..'z').toSet() + setOf(' ', '.', ',', '-', '\'')

    fun check(line: String, name: String): Verdict {
        if (line.isBlank()) return Verdict.Reject("blank")
        if (line.any { it == '\n' || it == '\r' }) return Verdict.Reject("multiline")
        if (line.any { it.isDigit() }) return Verdict.Reject("digit")
        // Uppercase outranks the character set: 'B' is not in `allowed` either, but "not-lowercase" is
        // the truer reason, and it keeps that count separate from the markup count the README publishes.
        if (line != line.lowercase()) return Verdict.Reject("not-lowercase")
        if (line.any { it !in allowed }) return Verdict.Reject("markup")
        // Two different splits, on purpose. `words` counts whitespace runs, which is what "4-8 words" means
        // to a reader and to the prompt. `tokens` strips every non-letter, which is what makes "brassy," the
        // word brassy without letting "brassiness" pass as it. Collapsing them into one split would either
        // count "brassy," as two words or accept a stem inside a longer word.
        // The allowlist above does not soften this: by the time the splits run, the only non-letters a line
        // can still hold are space and the four marks, so `[^a-z]+` is exactly "space or . , - '", and the
        // "brassy," versus "brassiness" behaviour is unchanged.
        val words = line.trim().split(Regex("\\s+"))
        if (words.size > 8) return Verdict.Reject("too-long")
        if (words.size < 4) return Verdict.Reject("too-short")
        val tokens = line.split(Regex("[^a-z]+"))
        if (tokens.none { it == name }) return Verdict.Reject("name absent")
        return Verdict.Accept
    }
}
