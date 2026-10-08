package com.burry071.evenlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EveningRecordTest {
    private val sample = EveningRecord(
        date = "2026-10-08",
        shutter = "2026-10-08--1759",
        bandsHex = listOf("#1E2438", "#2A3350", "#3C4A67", "#5C6B82", "#8A8375", "#B08A5E", "#C2854A", "#3A322A"),
        winnerIndex = 6, name = "brassy", sunAltitudeDeg = 9, meanLuminance = 0.117,
        wording = "brassy light holding at the edge of the wall",
        wordingSource = "model", wordingRejected = false, attempt = 1, clockSuspect = false
    )

    @Test fun survivesARoundTrip() { assertEquals(sample, EveningRecord.decode(sample.encode())) }

    @Test fun bandsSurviveAsAnArrayOfEightHexStrings() {
        val o = Json.tryParse(sample.encode()) as Json.Obj
        assertEquals(sample.bandsHex, o.arr("bands")!!.map { (it as Json.Str).v })
    }

    @Test fun aTemplateSourcedRecordStillDecodes() {
        val r = EveningRecord.decode(sample.encode().replace("\"model\"", "\"template\""))
        assertEquals("template", r!!.wordingSource)
    }

    @Test fun truncatedFileDecodesToNullInsteadOfThrowing() {
        assertNull(EveningRecord.decode(sample.encode().take(40)))
    }

    @Test fun aNullFieldFailsTheDecodeRatherThanInventingAValue() {
        // The record-level consequence of Json having no Null case. Substituting the enum's own deterministic
        // output keeps this honest: a file a hand-editor (or another tool) turned `"model"` into `null` must be
        // refused, not read back as an empty wording_source.
        val corrupted = sample.encode().replace("\"wording_source\":\"model\"", "\"wording_source\":null")
        assertNotEquals(sample.encode(), corrupted)
        assertNull(EveningRecord.decode(corrupted))
    }
}
