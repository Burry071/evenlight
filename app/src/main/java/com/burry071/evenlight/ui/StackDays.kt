package com.burry071.evenlight.ui

import java.time.LocalDate

/**
 * Every evening the stack must show, whether or not it has a card. The range runs from the first card to
 * today and the holes are kept: a gap is the product's claim about a week, so a list built from the cards
 * that exist would silently collapse it.
 *
 * `first` is the earliest card's date, not the date the app was installed: an evening that was never shot
 * before the app existed was never an evening, and inventing it would put a hole in the record for a day
 * the user could not have stood on a balcony.
 */
object StackDays {
    /**
     * A `today` before `first` yields no evenings because the Long range itself is empty when the end is below
     * the start - measured, not assumed: deleting the `if (today.isBefore(first)) return emptyList()` line that
     * used to sit here left all six StackDaysTest cases green. The guard went, because a second mechanism for a
     * result the first one already gives is a line no test can ever show failing.
     */
    fun days(first: LocalDate, today: LocalDate): List<LocalDate> =
        (0L..java.time.temporal.ChronoUnit.DAYS.between(first, today))
            .map { first.plusDays(it) }

    /** `held` is `Store.eveningsWithCards()` — ISO `yyyy-MM-dd` strings, the same text as a card filename. */
    fun holes(days: List<LocalDate>, held: Set<String>): Set<LocalDate> =
        days.filterNot { it.toString() in held }.toSet()
}
