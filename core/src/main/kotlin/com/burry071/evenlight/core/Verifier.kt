package com.burry071.evenlight.core

sealed class Verdict {
    object Accept : Verdict()
    data class Reject(val reason: String) : Verdict()
}

object Verifier {
    private val banned = charArrayOf('#', '<', '{', '(')

    fun check(line: String, name: String): Verdict {
        if (line.isBlank()) return Verdict.Reject("blank")
        if (line.any { it == '\n' || it == '\r' }) return Verdict.Reject("multiline")
        if (line.any { it.isDigit() }) return Verdict.Reject("digit")
        if (line.any { banned.contains(it) }) return Verdict.Reject("markup")
        if (line != line.lowercase()) return Verdict.Reject("not-lowercase")
        // Two different splits, on purpose. `words` counts whitespace runs, which is what "4-8 words" means
        // to a reader and to the prompt. `tokens` strips every non-letter, which is what makes "brassy," the
        // word brassy without letting "brassiness" pass as it. Collapsing them into one split would either
        // count "brassy," as two words or accept a stem inside a longer word.
        val words = line.trim().split(Regex("\\s+"))
        if (words.size > 8) return Verdict.Reject("too-long")
        if (words.size < 4) return Verdict.Reject("too-short")
        if (line.split(Regex("[^a-z]+")).none { it == name }) return Verdict.Reject("name absent")
        return Verdict.Accept
    }
}
