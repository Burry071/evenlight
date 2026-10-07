package com.burry071.evenlight.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SunWindowTest {
    private val home = GeoPoint(31.52, 74.35)
    private val pkt = ZoneId.of("Asia/Karachi")

    @Test fun sunsetOn2026Oct08IsWithinTwoMinutesOfPublished() {
        assertEquals(12 * 60 + 40.8, SunWindow.sunsetUtcMinutes(LocalDate.of(2026, 10, 8), home, 5.0), 2.0)
    }

    @Test fun sunsetOn2026Oct11IsWithinTwoMinutesOfPublished() {
        assertEquals(12 * 60 + 37.2, SunWindow.sunsetUtcMinutes(LocalDate.of(2026, 10, 11), home, 5.0), 2.0)
    }

    @Test fun sunsetMovesEarlierAcrossTheWeek() {
        val a = SunWindow.sunsetUtcMinutes(LocalDate.of(2026, 10, 8), home, 5.0)
        val b = SunWindow.sunsetUtcMinutes(LocalDate.of(2026, 10, 11), home, 5.0)
        assertTrue("expected $b < $a", b < a)
    }

    @Test fun altitudeAtTheRoundedMinuteSunsetReturnsCarriesBothBiases() {
        // This pins the altitude at the instant sunset() ACTUALLY returns, which is not the -0.833
        // ZENITH_SUNSET convention for two independent reasons, both deliberate:
        //  (a) single-pass: decl/eqt are evaluated once at local noon, so the altitude the solver
        //      lands on at its own sunset instant carries that stale-angle residual;
        //  (b) minute quantization: sunset() rounds the solved minutes to a whole minute
        //      (roundToInt, 759.5566 -> 760 = 12:40:00Z), so this assertion checks the ROUNDED
        //      minute, not the exact 12:39:33.4Z instant.
        // Measured from the compiled code, 2026-10-08 at 31.52,74.35: -0.9882064537481341 at
        // 12:40Z. Do not "fix" this toward -0.833 or toward the truncated-minute -0.776: the
        // rounded minute is what the home screen displays (see sunsetFormatsTheMinuteTheUserSees).
        val sunsetLocal = SunWindow.sunset(LocalDate.of(2026, 10, 8), home, pkt)
        val utc = sunsetLocal.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime()
        assertEquals(-0.988, SunWindow.altitudeAt(utc, home), 0.05)
    }

    @Test fun sunsetFormatsTheMinuteTheUserSees() {
        // The home screen shows one solar number: "sunset 17:40 - in 14 min". Published sunset for
        // 2026-10-08 at 31.52,74.35 is 12:40:48Z = 17:40:48 PKT, so rounding the solved minutes
        // (12:39:33.4Z) to the nearest minute must display 17:40. Truncation would display 17:39 --
        // wrong by a minute in the only solar figure the app ever shows.
        val s = SunWindow.sunset(LocalDate.of(2026, 10, 8), home, pkt)
        assertEquals("17:40", s.format(DateTimeFormatter.ofPattern("HH:mm")))
    }

    @Test fun noonAltitudeMatchesTheKnownValue() {
        assertEquals(52.49, SunWindow.altitudeAt(LocalDateTime.of(2026, 10, 8, 7, 0), home), 0.05)
    }

    @Test fun eveningAltitudeIsWellBelowTheHorizon() {
        assertEquals(-24.386, SunWindow.altitudeAt(LocalDateTime.of(2026, 10, 8, 14, 30), home), 0.05)
    }

    @Test fun windowOpensTwentyMinutesEarlyAndClosesTwentyLate() {
        val s = SunWindow.sunset(LocalDate.of(2026, 10, 8), home, pkt)
        assertTrue(SunWindow.inSunsetWindow(s.minusMinutes(19), home))
        assertTrue(SunWindow.inSunsetWindow(s.plusMinutes(19), home))
        assertFalse(SunWindow.inSunsetWindow(s.minusMinutes(21), home))
        assertFalse(SunWindow.inSunsetWindow(s.plusMinutes(21), home))
    }
}
