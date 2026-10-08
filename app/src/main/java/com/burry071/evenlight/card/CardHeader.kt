package com.burry071.evenlight.card

import com.burry071.evenlight.core.EveningRecord
import com.burry071.evenlight.core.Match
import java.time.LocalDate

/**
 * No Android imports in this file, on purpose: the header is the last purely-data step between the stored record
 * and the drawn card, so it stays unit-testable on a JVM while CardRenderer either side of it is not.
 */
private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

/**
 * The card's date line, from the record the app already wrote - design/evenlight-mockups.html shows "9 October".
 * Table-driven like Pixels.HEX_DIGITS, because LocalDate.format's pattern letters resolve against the default
 * locale and a card header that silently switches to Urdu month names on a changed device is the same defect this
 * project already refused once.
 *
 * winnerIndex and neighbours come from the record and the Match respectively. winnerIndex is read from the RECORD,
 * never recomputed from `match`: the JSON is the stored fact, Task 10 re-derives cards from `winner`, and a header
 * that disagreed with the record it was built from is the exact drift CardRenderer's comment in T7 forbids.
 */
fun headerFor(record: EveningRecord, match: Match): CardRenderer.Header {
    val date = LocalDate.parse(record.date)
    return CardRenderer.Header(
        name = record.name,
        dateLabel = "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}",
        sunAltitudeDeg = record.sunAltitudeDeg,
        wording = record.wording,
        winnerIndex = record.winnerIndex,
        neighbours = match.neighbours,
        night = false
    )
}
