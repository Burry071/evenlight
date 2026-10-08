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
    }

    @Test fun aSecondShotTheSameEveningIsAttemptTwo() {
        val store = temp()
        store.writeRecord(record("2026-10-08"), 1)
        assertEquals(1, store.attemptsFor("2026-10-08"))
        store.writeRecord(record("2026-10-08"), 2)
        assertEquals(2, store.attemptsFor("2026-10-08"))
    }

    @Test fun anEveningWithNoRecordIsNotAnEveningWithACard() {
        assertFalse(temp().eveningsWithCards().contains("2026-10-07"))
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
