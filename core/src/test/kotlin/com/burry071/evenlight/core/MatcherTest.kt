package com.burry071.evenlight.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MatcherTest {
    private val grey = (0 until 8).map { Pixels.rgb(10 + it * 12, 10 + it * 12, 10 + it * 12) }

    @Test fun winnerIsTheMostChromaticBand() {
        val b = grey.toMutableList().also { it[5] = Pixels.rgb(194, 133, 74) }
        assertEquals(5, Matcher.winner(b))
    }

    @Test fun chromaTieGoesToTheBrighterBand() {
        assertEquals(1, Matcher.winner(listOf(Pixels.rgb(10, 60, 110), Pixels.rgb(150, 200, 250))))
    }

    @Test fun fullTieGoesToTheLowestIndex() {
        val c = Pixels.rgb(100, 150, 200)
        assertEquals(0, Matcher.winner(listOf(c, c, c)))
    }

    @Test fun anchorsAreNamedForTheirOwnColour() {
        assertEquals("brassy", Matcher.nameFor(Pixels.parseHex("#C2854A")))
        assertEquals("slate", Matcher.nameFor(Pixels.parseHex("#5C6B82")))
        assertEquals("chalk", Matcher.nameFor(Pixels.parseHex("#E3DED4")))
        assertEquals("sunken", Matcher.nameFor(Pixels.parseHex("#262A33")))
    }

    @Test fun neighboursAreAboveThenBelow() {
        val b = grey.toMutableList().also { it[3] = Pixels.rgb(194, 133, 74) }
        val m = Matcher.match(b)
        assertEquals(3, m.winnerIndex)
        assertEquals(listOf(b[2], b[4]), m.neighbours)
    }

    @Test fun winnerAtTheTopEdgeHasOneNeighbour() {
        val b = grey.toMutableList().also { it[0] = Pixels.rgb(250, 120, 20) }
        val m = Matcher.match(b)
        assertEquals(0, m.winnerIndex)
        assertEquals(listOf(b[1]), m.neighbours)
    }

    @Test fun vocabularyHasThirtyFiveWordsAndUniqueAnchors() {
        assertEquals(35, Vocabulary.size)
        assertEquals(35, Vocabulary.words.map { it.word }.toSet().size)
        assertEquals(35, Vocabulary.words.map { it.anchorHex }.toSet().size)
    }
}
