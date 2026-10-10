package com.burry071.evenlight.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
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
        // Two error sources, both deliberate, both named so a future reader stops guessing:
        // (a) the solver uses ZENITH_SUNSET = 90.833 (34' refraction + 16' semidiameter) but evaluates
        //     decl/eqt once at local noon, so it does not land exactly on -0.833;
        // (b) sunset() quantizes to the nearest whole minute (12:40:00Z for 2026-10-08), and this
        //     assertion evaluates at that rounded minute, not at the exact instant.
        // Measured from the compiled Kotlin, not derived by hand: -0.988.
        val sunsetLocal = SunWindow.sunset(LocalDate.of(2026, 10, 8), home, pkt)
        val utc = sunsetLocal.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime()
        assertEquals(-0.988, SunWindow.altitudeAt(utc, home), 0.05)
    }

    @Test fun sunsetDisplayedMinuteIsWithinAMinuteOfPublished() {
        // The home screen prints "sunset HH:mm", so the displayed minute must agree with reality. Assert
        // proximity, not an exact string: today's computed instant sits 3.4 s from a minute boundary, so a
        // pinned "17:40" would certify a near-tie and break the day the maths gets MORE accurate.
        // Truncating sunset() to the minute instead of rounding puts the display 1.8 min out - which fails
        // here, and that regression is the reason this test exists.
        val s = SunWindow.sunset(LocalDate.of(2026, 10, 8), home, pkt)
        val published = ZonedDateTime.of(2026, 10, 8, 12, 40, 48, 0, ZoneId.of("UTC"))
            .withZoneSameInstant(pkt)
        val drift = abs(Duration.between(s, published).toSeconds())
        assertTrue("displayed $s is ${drift}s from published $published", drift <= 60)
    }

    @Test fun altitudeForDisplayRoundsToWholeDegrees() {
        // The app shows "sun 6°" - the second and last solar number it displays. Nothing else covers it.
        assertEquals(52, SunWindow.altitudeForDisplay(LocalDateTime.of(2026, 10, 8, 7, 0), home))
        assertEquals(-1, SunWindow.altitudeForDisplay(LocalDateTime.of(2026, 10, 8, 12, 40), home))
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

    @Test fun sunsetUsesTheRequestedDatesOwnDstOffset() {
        // Finding 2.6. Both dates come from a full sweep of 2026 in the U.S. Eastern zone: they are the dates
        // where the wrong evaluation hour crosses a rounding boundary, so the minute itself moves. 2026-02-05
        // sits on winter time (-5) and reads 17:19 computed from its own offset but 17:18 from the summer one;
        // 2026-07-19 sits on summer time (-4) and reads 20:24 from its own but 20:23 from the winter one.
        // Whatever offset today happens to carry, a sunset() that read Instant.now() gets ONE of these two
        // wrong, so the test has teeth on every run day - and the fixed function is a pure function of the
        // date and zone again, which is the claim the no-network argument rests on.
        val ny = ZoneId.of("America/New_York")
        val east = GeoPoint(40.71, -74.01)
        assertEquals(LocalDateTime.of(2026, 2, 5, 17, 19).atZone(ny), SunWindow.sunset(LocalDate.of(2026, 2, 5), east, ny))
        assertEquals(LocalDateTime.of(2026, 7, 19, 20, 24).atZone(ny), SunWindow.sunset(LocalDate.of(2026, 7, 19), east, ny))
    }
}
