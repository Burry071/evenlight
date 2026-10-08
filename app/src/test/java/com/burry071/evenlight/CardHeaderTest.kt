package com.burry071.evenlight

import com.burry071.evenlight.core.EveningRecord
import com.burry071.evenlight.core.Match
import com.burry071.evenlight.card.headerFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CardHeaderTest {
    private val hex = listOf("#000000", "#111111", "#222222", "#333333", "#444444", "#555555", "#666666", "#777777")

    private fun record(winner: Int) = EveningRecord(
        "2026-10-08", "2026-10-08--1759", hex, winner, "amber", 9, 0.0,
        "amber light, measured from this sky", "none", false, 1, false
    )

    @Test fun theHeaderCarriesTheBandTheRecordChoseNotTheOneTheMatcherWouldChooseAgain() {
        // The Match here disagrees with the record on purpose. If headerFor reads match.winnerIndex, this test
        // draws 3 where the record says 6, and the card contradicts the JSON next to it.
        val h = headerFor(record(6), Match(3, "slate", listOf(2, 4)))
        assertEquals(6, h.winnerIndex)
        assertNotEquals(3, h.winnerIndex)
    }

    @Test fun theHeaderLabelsTheEveningInTheDesignsOwnWords() {
        // The chips are match.neighbours - the bands adjacent to the winner, as measured ints in the 0x00RRGGBB
        // packing T3 defines (CardRenderer promotes each through Palette.opaque at draw time, pinned in T7).
        // `night` is false because no spec rule decides a dark *card*: the stack follows the system theme in T9,
        // and wiring a card to the measured luminance would be a rule this project never wrote down.
        val h = headerFor(record(6), Match(6, "amber", listOf(5, 7)))
        assertEquals(listOf(5, 7), h.neighbours)
        assertEquals("8 October", h.dateLabel)
        assertEquals(9, h.sunAltitudeDeg)
        assertEquals("amber light, measured from this sky", h.wording)
        assertEquals(false, h.night)
    }
}
