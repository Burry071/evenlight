package com.burry071.evenlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifierTest {
    @Test fun acceptsALineThatCarriesTheName() {
        assertEquals(Verdict.Accept, Verifier.check("brassy light holding at the edge", "brassy"))
    }

    @Test fun rejectsAnyDigit() {
        assertTrue(Verifier.check("brassy light at 17 degrees", "brassy") is Verdict.Reject)
    }

    @Test fun rejectsAHashSoHexCannotLeakOntoACard() {
        assertTrue(Verifier.check("brassy under #c2854a", "brassy") is Verdict.Reject)
    }

    @Test fun rejectsMarkup() {
        for (s in listOf("<start> brassy", "brassy {light}", "brassy (light)")) {
            assertTrue(s, Verifier.check(s, "brassy") is Verdict.Reject)
        }
    }

    @Test fun rejectsMoreThanOneLine() {
        assertTrue(Verifier.check("brassy light\nand then", "brassy") is Verdict.Reject)
    }

    @Test fun rejectsMoreThanEightWords() {
        assertTrue(Verifier.check("brassy light holding at the edge of the wall tonight", "brassy") is Verdict.Reject)
    }

    @Test fun rejectsALineMissingTheGivenName() {
        assertEquals(Verdict.Reject("name absent"), Verifier.check("a quiet blue evening", "brassy"))
    }

    @Test fun rejectsFewerThanFourWords() {
        // The spec's wording task is "one lowercase line of 4-8 words". The ceiling alone would let a card
        // print a single word, so the floor is as much a contract as the ceiling - and it must be checked
        // before the name, since "brassy" on its own satisfies every other rule.
        assertEquals(Verdict.Reject("too-short"), Verifier.check("brassy light holds", "brassy"))
        assertEquals(Verdict.Accept, Verifier.check("brassy light holds low", "brassy"))
    }

    @Test fun theNameCountsAsAWordEvenWithPunctuationAttached() {
        // A 1B model writes commas and periods. Matching the name as a lowercase-letter token accepts
        // "brassy," without accepting "brassiness", so `wording_rejected` reports the model refusing the
        // contract rather than reporting a parsing artifact of ours.
        assertEquals(Verdict.Accept, Verifier.check("brassy, light holding low", "brassy"))
        assertEquals(Verdict.Reject("name absent"), Verifier.check("brassiness holds the low sky", "brassy"))
    }

    @Test fun rejectsBlankOutput() {
        assertEquals(Verdict.Reject("blank"), Verifier.check("   ", "brassy"))
    }

    @Test fun rejectsUpperCaseBecauseTheHouseStyleIsLowercase() {
        assertEquals(Verdict.Reject("not-lowercase"), Verifier.check("Brassy Light Holding Low", "brassy"))
    }
}
