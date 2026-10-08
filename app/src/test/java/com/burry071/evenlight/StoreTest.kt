package com.burry071.evenlight

import com.burry071.evenlight.core.EveningRecord
import com.burry071.evenlight.data.Store
import java.io.File
import java.time.LocalDateTime
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreTest {
    private var dir: File? = null

    @After fun tearDown() { dir?.deleteRecursively() }

    private fun temp(): Store {
        val d = File(System.getProperty("java.io.tmpdir"), "evenlight-test-" + System.nanoTime())
        dir = d
        return Store(d)
    }

    private fun record(date: String) = EveningRecord(
        date, "$date--1759", List(8) { "#000000" }, 0, "low", 9, 0.0,
        "low light, measured from this sky", "none", false, 1, false
    )

    @Test fun shutterNameIsDateAndTimeToTheMinute() {
        assertEquals("2026-10-08--1759", temp().nextShutterName(LocalDateTime.of(2026, 10, 8, 17, 59, 33)))
        // Both single-digit cases, because `twoDigit`'s padding branch is otherwise never exercised and dropping
        // the zero - 09:05 becoming "95" - would still leave the first assertion green.
        assertEquals("2026-10-08--0905", temp().nextShutterName(LocalDateTime.of(2026, 10, 8, 9, 5)))
    }

    @Test fun aSecondShotTheSameEveningIsAttemptTwo() {
        val store = temp()
        store.writeRecord(record("2026-10-08"), 1)
        assertEquals(1, store.attemptsFor("2026-10-08"))
        store.writeRecord(record("2026-10-08"), 2)
        assertEquals(2, store.attemptsFor("2026-10-08"))
    }

    @Test fun anEveningWithNoRecordIsNotAnEveningWithACard() {
        // Written the other way round this would pass on an empty store whatever eveningsWithCards did, so the
        // record has to exist first: the distinction under test is record-without-card, not nothing-without-nothing.
        val store = temp()
        store.writeRecord(record("2026-10-08"), 1)
        assertFalse(store.eveningsWithCards().contains("2026-10-08"))
        store.writeCard("2026-10-08", byteArrayOf(0))
        assertTrue(store.eveningsWithCards().contains("2026-10-08"))
        assertFalse(store.eveningsWithCards().contains("2026-10-07"))
    }

    @Test fun attemptsComeBackInOrderEvenThoughADashSortsBeforeADot() {
        // recordFile writes "2026-10-08.json" for attempt 1 and "2026-10-08-attempt-2.json" for attempt 2, and '-'
        // (0x2D) sorts before '.' (0x2E), so sorting by file name alone hands Task 9's stack and Task 10's
        // "latest attempt wins" the attempts backwards. Sort by the (date, attempt) the NAME asserts.
        val store = temp()
        store.writeRecord(record("2026-10-08"), 1)
        // The body carries attempt 2 as well, the way shoot() writes it: readRecords returns the decoded record,
        // so without this the assertion below could only ever see [1, 1] whatever the sort did.
        store.writeRecord(record("2026-10-08").copy(attempt = 2), 2)
        assertEquals(listOf(1, 2), store.readRecords().map { it.attempt })
    }

    @Test fun aBodyThatContradictsItsFileNameStillSortsByItsFileName() {
        // decode defaults a missing `attempt` to 1, so a file named attempt-2 whose body says 1 would tie with the
        // real attempt 1 if the sort key came from the decoded record - and sortedBy is stable, so the winner would
        // be whatever order listFiles() happened to return. The name is what recordFile wrote; the name decides.
        // The two bodies are made distinguishable by their `name`, since their `attempt` fields are deliberately
        // identical: the one in the attempt-2 FILE must come back last even though it claims to be attempt 1.
        val store = temp()
        store.writeRecord(record("2026-10-08").copy(name = "from-the-bare-file"), 1)
        File(store.root, "evenlight/records/2026-10-08-attempt-2.json")
            .writeText(record("2026-10-08").copy(name = "from-the-named-file").encode())
        val read = store.readRecords()
        assertEquals("a decoded record must never be dropped, whatever its body claims", 2, read.size)
        assertEquals("from-the-bare-file", read.first().name)
        assertEquals("from-the-named-file", read.last().name)
    }

    @Test fun aNameTheAppWouldNeverWriteStillGetsADeterministicPlace() {
        // `2026-10-08-attempt-abc.json` parses to attempt 1, tying with the real bare-date file. Without the
        // filename as the comparator's third key that tie is settled by listFiles() order, so Task 10's
        // "latest attempt wins" would depend on the filesystem - which is the nondeterminism this store is
        // supposed to have removed. What this assertion pins is the ORDER THE NAME IMPLIES: '-' is 0x2D and '.'
        // is 0x2E, so "2026-10-08-attempt-abc" comes before "2026-10-08.json". Fix round 2 deleted the third
        // comparator key and this test still passed, because this host's listFiles() returned the attempt-abc
        // file first anyway. It documents the order; it does not prove the third key is load-bearing.
        val store = temp()
        store.writeRecord(record("2026-10-08").copy(name = "bare"), 1)
        File(store.root, "evenlight/records/2026-10-08-attempt-abc.json")
            .writeText(record("2026-10-08").copy(name = "abc").encode())
        assertEquals(listOf("abc", "bare"), store.readRecords().map { it.name })
    }

    @Test fun attemptOneIsTheBareDateFileAndAttemptThreeIsNamed() {
        // The naming convention is what eveningsWithCards and Task 10 both read; without this assertion the
        // `attempt <= 1` branch could become `< 1` and every test above would still pass.
        val store = temp()
        assertEquals("2026-10-08.json", store.recordFile("2026-10-08", 1).name)
        assertEquals("2026-10-08-attempt-3.json", store.recordFile("2026-10-08", 3).name)
    }

    @Test fun corruptRecordsAreCountedNotDeleted() {
        val store = temp()
        store.writeRecord(record("2026-10-08"), 1)
        val bad = File(store.root, "evenlight/records/2026-10-06.json").apply { writeText("{ truncated") }
        val ok = store.readRecords()
        assertEquals(1, ok.size)
        assertEquals("2026-10-08", ok.first().date)
        assertEquals(1, store.corruptRecords())
        assertTrue("a parse failure must not delete evidence", bad.exists())
    }

    @Test fun onePathologicallyDeepFileDoesNotHideTheGoodRecords() {
        // Json.tryParse catches Exception, and a parser that recurses per bracket dies with StackOverflowError -
        // an Error, not an Exception - so a single 200k-bracket file would propagate out of readRecords and the
        // stack would show zero evenings instead of one good one. This test fails if Store catches Exception.
        val store = temp()
        store.writeRecord(record("2026-10-08"), 1)
        File(store.root, "evenlight/records/2026-10-05.json").writeText("[".repeat(200_000))
        assertEquals("2026-10-08", store.readRecords().single().date)
        assertEquals(1, store.corruptRecords())
    }

    @Test fun aSecondShotInTheSameMinuteKeepsBothRawJpegs() {
        // The shutter name is only precise to the minute, so two shots at 17:59:10 and 17:59:50 share it. If the
        // raw file is keyed on the name alone the first evening's evidence is gone and only the card survives -
        // the one loss this stack cannot show, because the raw JPEG is what proves the bands were measured.
        val store = temp()
        val a = store.writeRaw(byteArrayOf(1, 2, 3), "2026-10-08--1759", 1)
        val b = store.writeRaw(byteArrayOf(4, 5, 6), "2026-10-08--1759", 2)
        assertTrue(a.name != b.name)
        assertEquals(3, a.readBytes().size)
        assertArrayEquals(byteArrayOf(4, 5, 6), b.readBytes())
    }

    @Test fun settingsRoundTripKeepsTwoDecimalCoordinates() {
        val store = temp()
        store.saveSettings(com.burry071.evenlight.data.Settings("hostel balcony", 31.52, 74.35))
        assertEquals(com.burry071.evenlight.data.Settings("hostel balcony", 31.52, 74.35), store.loadSettings())
    }

    @Test fun aTruncatedSettingsFileReadsAsUnsetRatherThanCrashing() {
        val store = temp()
        File(store.root, "evenlight/settings.json").writeText("{ truncated")
        assertEquals(com.burry071.evenlight.data.Settings.UNSET, store.loadSettings())
    }
}
