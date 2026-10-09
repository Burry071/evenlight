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

    @Test fun markupIsTheReasonAndNotAnIncidentalOne() {
        // Every string in rejectsMarkup also fails a later gate ("<start> brassy" is two words), so deleting
        // the markup guard leaves them all rejected and the suite green - which would silently drop a reason
        // category from the wording_rejected/wording_total count Task 11 publishes. These two lines pass
        // every other rule, so only the guard can reject them.
        assertEquals(Verdict.Reject("markup"), Verifier.check("brassy light holding (low)", "brassy"))
        assertEquals(Verdict.Reject("markup"), Verifier.check("brassy light holding #low", "brassy"))
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

    @Test fun anEmbeddedStemIsNotTheName() {
        // "brassiness" proves the suffix side. This proves the prefix side, and is the only test that dies if
        // the token match is traded for line.contains(name) - a refactor that would accept "unbrassy" as
        // carrying the word brassy.
        assertEquals(Verdict.Reject("name absent"), Verifier.check("unbrassy holds the low sky", "brassy"))
    }

    @Test fun rejectsBlankOutput() {
        assertEquals(Verdict.Reject("blank"), Verifier.check("   ", "brassy"))
    }

    @Test fun rejectsUpperCaseBecauseTheHouseStyleIsLowercase() {
        assertEquals(Verdict.Reject("not-lowercase"), Verifier.check("Brassy Light Holding Low", "brassy"))
    }

    @Test fun rejectsAsterisksBecauseTheGateIsAnAllowlistNotADenylist() {
        // Measured, not hypothetical: the device answered `the **ash** sky glowed softly as dusk fell.`,
        // the old denylist of `# < { (` had no rule for `*`, and the card printed the literal asterisks.
        // This line passes every other rule - eight words, lowercase, no digits, "ash" is a whole token -
        // so only the character set can stop it.
        assertEquals(Verdict.Reject("markup"), Verifier.check("the **ash** sky glowed softly as dusk fell.", "ash"))
    }

    @Test fun rejectsUnderscoreAndBacktick() {
        // Markdown emphasis and code spans are the two other shapes a chat-trained model reaches for.
        // Both lines here otherwise pass every other rule, so `markup` is the only possible reason.
        assertEquals(Verdict.Reject("markup"), Verifier.check("brassy_light holds the low sky", "brassy"))
        assertEquals(Verdict.Reject("markup"), Verifier.check("brassy `light` holds low", "brassy"))
    }

    @Test fun rejectsTheCurlyApostropheEvenThoughItLooksLikeOneWeAllow() {
        // The cost of the allowlist, accepted up front: U+2019 is non-ASCII, so it counts as markup and the
        // rejection rate takes the hit. The token split would have found "brassy" inside "brassy’s" anyway -
        // only the character set can be rejecting this line.
        assertEquals(Verdict.Reject("markup"), Verifier.check("brassy\u2019s light holds low", "brassy"))
    }

    @Test fun acceptsTheFourMarksTheAllowlistNames() {
        // Every allowed mark in one line: period, comma, hyphen, straight apostrophe, over pure a-z and spaces.
        // If this ever rejects, the gate is rejecting lines its own allowlist admits.
        assertEquals(Verdict.Accept, Verifier.check("brassy, the low-lit sky's fading.", "brassy"))
    }
}
