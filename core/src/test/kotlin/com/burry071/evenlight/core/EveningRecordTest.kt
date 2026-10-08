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
        // A file whose `"model"` became `null` was edited by something other than this app, and decoding it
        // into a record with an invented source is the failure this codec exists to avoid. The substitution
        // targets the encoder's own output, so the test cannot pass by accident if the formatting changes.
        val corrupted = sample.encode().replace("\"wording_source\":\"model\"", "\"wording_source\":null")
        assertNotEquals(sample.encode(), corrupted)
        assertNull(EveningRecord.decode(corrupted))
    }

    @Test fun theWireFileIsExactlyWhatPythonReads() {
        // Every key spelling, the order, the absence of spaces and the integral form of `winner` are the
        // contract Task 10's Python reads. A symmetric rename of a key, or `"winner":6.0`, keeps every other
        // test here green: Python would load the file happily and then do `bands[record["winner"]]` on a
        // float, and the two languages would disagree about the same bytes forever.
        assertEquals(
            """{"date":"2026-10-08","shutter":"2026-10-08--1759","bands":["#1E2438","#2A3350","#3C4A67","#5C6B82","#8A8375","#B08A5E","#C2854A","#3A322A"],"winner":6,"name":"brassy","sun_altitude_deg":9,"mean_luminance":0.117,"wording":"brassy light holding at the edge of the wall","wording_source":"model","wording_rejected":false,"attempt":1,"clock_suspect":false}""",
            sample.encode()
        )
    }

    @Test fun aWrongTypeFieldFailsTheDecode() {
        // Absent is tolerated for two fields; present-but-wrong-type is not. A default cannot tell them apart,
        // so `{"clock_suspect":"no"}` would otherwise read back as "the clock was fine" - a corrupt record
        // inventing the trustworthy value, out of the door that Task 12 counts through.
        val w = sample.encode()
        assertNull(EveningRecord.decode(w.replace("\"clock_suspect\":false", "\"clock_suspect\":\"no\"")))
        assertNull(EveningRecord.decode(w.replace("\"attempt\":1", "\"attempt\":\"x\"")))
        assertNull(EveningRecord.decode(w.replace("\"winner\":6", "\"winner\":\"6\"")))
        assertNull(EveningRecord.decode(w.replace("\"wording_rejected\":false", "\"wording_rejected\":0")))
    }

    @Test fun aMissingFieldFailsTheDecode() {
        val w = sample.encode()
        for (frag in listOf("\"date\":\"2026-10-08\",", "\"wording_source\":\"model\",", "\"mean_luminance\":0.117,")) {
            val broken = w.replace(frag, "")
            assertNotEquals(w, broken)
            assertNull(EveningRecord.decode(broken))
        }
    }

    @Test fun bandsMustBeExactlyEightStrings() {
        val w = sample.encode()
        val seven = w.replace(",\"#3A322A\"", "")
        assertNotEquals(w, seven)
        assertNull(EveningRecord.decode(seven))
        val nine = w.replace("\"#3A322A\"", "\"#3A322A\",\"#3A322A\"")
        assertNotEquals(w, nine)
        assertNull(EveningRecord.decode(nine))
        // Not "throws": the element check is the only thing between a numeric band and a ClassCastException
        // from the map below it, and decode's whole promise is null.
        assertNull(EveningRecord.decode(w.replace("\"#1E2438\"", "123")))
        assertNull(EveningRecord.decode(w.replace("\"#1E2438\"", "true")))
    }

    @Test fun anOutOfRangeWinnerFailsTheDecode() {
        val w = sample.encode()
        assertNull(EveningRecord.decode(w.replace("\"winner\":6", "\"winner\":99")))
        assertNull(EveningRecord.decode(w.replace("\"winner\":6", "\"winner\":-1")))
        assertNull(EveningRecord.decode(w.replace("\"winner\":6", "\"winner\":6.7")))
    }

    @Test fun aRecordCarryingNoDefaultValuesRoundTrips() {
        // Every other test here uses a sample whose `attempt`, `clockSuspect` and `wordingRejected` happen to
        // equal the two defaults and `false`, so a decode that hard-coded those three reads would stay green.
        // `wording_rejected` is the honest "the model's line was refused" flag; hard-coding it would silently
        // erase every rejection from the published count.
        val r = sample.copy(
            wordingSource = "none", wordingRejected = true, attempt = 3, clockSuspect = true,
            winnerIndex = 0, sunAltitudeDeg = -4, meanLuminance = 0.0005
        )
        assertEquals(r, EveningRecord.decode(r.encode()))
    }

    @Test fun theTwoToleratedAbsencesAreDeliberate() {
        // Written before `attempt` and `clock_suspect` existed, records still have to read. Pinning the
        // tolerance says which way the defaults go: attempt 1, clock trustworthy.
        val w = sample.encode()
        val noAttempt = w.replace("\"attempt\":1,", "")
        val noClock = w.replace(",\"clock_suspect\":false", "")
        assertNotEquals(w, noAttempt)
        assertNotEquals(w, noClock)
        assertEquals(1, EveningRecord.decode(noAttempt)!!.attempt)
        assertEquals(false, EveningRecord.decode(noClock)!!.clockSuspect)
    }
}
