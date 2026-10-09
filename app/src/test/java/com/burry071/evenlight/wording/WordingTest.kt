package com.burry071.evenlight.wording

import com.burry071.evenlight.core.EveningRecord
import com.burry071.evenlight.core.Verdict
import com.burry071.evenlight.core.Verifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The prompt, the fallback and the provenance mapping are pure strings and pure data, so all of Task 11's logic is
 * testable without 1.02 GB of weights, without an Engine, and without Android: `applyWording` takes and returns
 * `:core` types only (R17), which is what moves the three-way branch out of the controller and into this file.
 */
class WordingTest {
    private val bands = listOf("#1E2438", "#2A3350", "#3C4A67", "#5C6B82", "#8A8375", "#B08A5E", "#C2854A", "#3A322A")

    @Test fun templateCarriesTheNameAndPassesTheVerifier() {
        assertEquals(Verdict.Accept, Verifier.check(Wording.template("brassy"), "brassy"))
    }

    @Test fun templateContainsNoDigit() {
        assertTrue(Wording.template("low").none { it.isDigit() })
    }

    @Test fun promptHandsOverTheNameAndForbidsDigits() {
        val p = Wording.prompt("brassy", bands, 9)
        assertTrue(p.contains("brassy"))
        assertTrue(p.lowercase().contains("do not use any digit"))
    }

    @Test fun promptNeverAsksTheModelToChooseOrMeasure() {
        // Choosing and measuring are the app's jobs, already done before the prompt is built (spec section 7);
        // asking the model to redo them is how 1/6-consistent output happened in the spike.
        val p = Wording.prompt("brassy", bands, 9).lowercase()
        for (forbidden in listOf("choose", "pick", "rank", "which", "estimate", "measure")) {
            assertFalse("prompt says '$forbidden'", p.contains(forbidden))
        }
    }

    @Test fun systemForbidsTheSameThingsTheVerifierChecks() {
        // Not redundancy for its own sake: every rejection is a wasted 1.2 s, so SYSTEM must name the same set
        // of rules the gate enforces. If one rule drifts out of SYSTEM, rejections silently rise.
        val s = Wording.SYSTEM.lowercase()
        assertTrue(s.contains("do not use any digit"))
        assertTrue(s.contains("newline"))
        assertTrue(s.contains("one lowercase line"))
        // The verifier's rule is now an allowlist, so the guard asks SYSTEM for the allowlist clause rather
        // than for examples of banned characters: naming `#` and brackets was exactly what missed `**ash**`.
        assertTrue(s.contains("use only lowercase letters"))
        assertTrue(s.contains("the marks . , - '"))
    }

    private fun filed(
        wording: String = Wording.template("brassy"),
        source: String = "none",
        rejected: Boolean = false
    ) = EveningRecord(
        date = "2026-10-08",
        shutter = "2026-10-08--1740",
        bandsHex = bands,
        winnerIndex = 3,
        name = "brassy",
        sunAltitudeDeg = 9,
        meanLuminance = 37.2,
        wording = wording,
        wordingSource = source,
        wordingRejected = rejected,
        attempt = 2,
        clockSuspect = true
    )

    @Test fun applyWordingAdoptsAConformingLineAsModel() {
        val out = applyWording(filed(), "brassy light fades over the rooftops", "brassy", consulted = true)
        assertEquals("brassy light fades over the rooftops", out.wording)
        assertEquals("model", out.wordingSource)
        assertFalse(out.wordingRejected)
    }

    @Test fun applyWordingCountsARejectedLineAgainstTheTemplate() {
        // The candidate fails the verifier (a digit), so the line stays the template - but the attempt must be
        // counted, or the README's rejection rate divides by a total that only accepted answers made into it.
        val out = applyWording(filed(), "48 shades of brassy at dusk", "brassy", consulted = true)
        assertEquals(Wording.template("brassy"), out.wording)
        assertEquals("template", out.wordingSource)
        assertTrue("a rejection must be counted", out.wordingRejected)
    }

    @Test fun applyWordingSaysNoneWhenTheModelWasNeverConsulted() {
        // The input deliberately carries stale provenance: "never consulted" must mean "none" whatever the record
        // claimed before, and the line must be the template again, not the earlier model's sentence.
        val out = applyWording(filed("an old model line", "model", true), null, "brassy", consulted = false)
        assertEquals(Wording.template("brassy"), out.wording)
        assertEquals("none", out.wordingSource)
        assertFalse(out.wordingRejected)
    }

    @Test fun applyWordingSaysNoAnswerWhenTheConsultedEngineFellSilent() {
        // Measured: the device filed seven evenings as "none" while the weights were present and the engine warm,
        // because a 10 s timeout inside line() looked exactly like "no model installed". That conflated pair made
        // the README's failure rate uncomputable; "no-answer" is the consulted-and-silent half of it.
        // wordingRejected stays false - the verifier never saw a line, so nothing was rejected.
        val out = applyWording(filed("an old model line", "model", false), null, "brassy", consulted = true)
        assertEquals(Wording.template("brassy"), out.wording)
        assertEquals("no-answer", out.wordingSource)
        assertFalse(out.wordingRejected)
    }

    @Test fun theTwoNullsLandOnDifferentProvenances() {
        // The guard against collapsing `consulted` into one branch: same record, same null candidate, only the
        // flag differs, and the provenance must differ with it. Without this the seven-evenings bug is one
        // refactor away from returning.
        val consulted = applyWording(filed(), null, "brassy", consulted = true)
        val never = applyWording(filed(), null, "brassy", consulted = false)
        assertEquals("no-answer", consulted.wordingSource)
        assertEquals("none", never.wordingSource)
        assertNotEquals(consulted.wordingSource, never.wordingSource)
    }

    @Test fun applyWordingRewritesNothingButTheWordingAndItsProvenance() {
        // "The bands and the name never change" (brief Step 5), stated as the mutation it guards: a future refactor
        // of this mapping that also touched a measured field would change what the card claims was observed.
        // All four provenances, including the consulted flag that distinguishes the two nulls.
        val cases = listOf(
            "brassy light fades over the rooftops" to true,
            "48 shades of brassy at dusk" to true,
            null to true,
            null to false,
        )
        for ((candidate, consulted) in cases) {
            val before = filed()
            val after = applyWording(before, candidate, "brassy", consulted)
            assertEquals(before.date, after.date)
            assertEquals(before.shutter, after.shutter)
            assertEquals("the eight bands survive: " + candidate, before.bandsHex, after.bandsHex)
            assertEquals(8, after.bandsHex.size)
            assertEquals(before.winnerIndex, after.winnerIndex)
            assertEquals(before.name, after.name)
            assertEquals(before.sunAltitudeDeg, after.sunAltitudeDeg)
            assertEquals(before.meanLuminance, after.meanLuminance, 0.0)
            assertEquals(before.attempt, after.attempt)
            assertEquals(before.clockSuspect, after.clockSuspect)
        }
    }
}
