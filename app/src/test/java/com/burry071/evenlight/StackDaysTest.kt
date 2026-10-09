package com.burry071.evenlight

import com.burry071.evenlight.ui.StackDays
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The stack's holes are the product's whole argument - "gaps are never collapsed" - and they are pure set logic,
 * so this is the one part of Task 9 a JVM test can actually police. No Robolectric here, and no Compose runtime:
 * that is why [StackDays] holds no `android.*` and no Compose import.
 */
class StackDaysTest {
    @Test fun rangeIncludesBothEnds() {
        assertEquals(
            listOf(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7)),
            StackDays.days(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7))
        )
    }

    @Test fun oneEveningRangeIsThatEvening() {
        assertEquals(listOf(d(2026, 10, 7)), StackDays.days(d(2026, 10, 7), d(2026, 10, 7)))
    }

    @Test fun todayBeforeFirstCardIsEmptyNotOneNegativeSpan() {
        assertEquals(emptyList<LocalDate>(), StackDays.days(d(2026, 10, 9), d(2026, 10, 7)))
    }

    @Test fun holesAreTheRangeMinusTheCards() {
        val days = StackDays.days(d(2026, 10, 5), d(2026, 10, 7))
        assertEquals(setOf(d(2026, 10, 6)), StackDays.holes(days, setOf("2026-10-05", "2026-10-07")))
    }

    @Test fun aHoleSurvivesWhenNoCardExistsAtAll() {
        // The empty stack is the case a reviewer sees first: every evening is a hole, and none is dropped.
        val days = StackDays.days(d(2026, 10, 1), d(2026, 10, 4))
        assertEquals(4, days.size)
        assertEquals(days.toSet(), StackDays.holes(days, emptySet()))
    }

    @Test fun aCardOutsideTheRangeIsIgnored() {
        // The brief's Step 3 asserted `emptySet()` here, which its own implementation cannot produce: 5 Oct is in
        // the range and not held, so it is a hole whatever the 9 Oct entry does. The inputs are the brief's verbatim
        // ones and `StackDays` is unchanged; the expectation now says what the name claims - an ISO date outside
        // the range has no effect, so the unheld evening survives.
        assertEquals(setOf(d(2026, 10, 5)), StackDays.holes(listOf(d(2026, 10, 5)), setOf("2026-10-09")))
    }

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
}
