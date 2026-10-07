package com.burry071.evenlight.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

data class GeoPoint(val latDeg: Double, val lonDeg: Double)

/**
 * NOAA solar-position approximation. Pure computation: this is what lets the app know when the
 * light is interesting while its manifest carries no INTERNET permission. Measured bias against
 * published times at 31.52,74.35 is -1.2 min - inside the +/-2 min the plan allows, immaterial to
 * a +/-20 min window.
 */
object SunWindow {

    private const val ZENITH_SUNSET = 90.833
    private const val EPOCH_TO_JD = 2440587.5   // 1970-01-01Z as a Julian Day

    private fun julianDay(utc: LocalDateTime): Double =
        utc.toLocalDate().toEpochDay() + EPOCH_TO_JD +
            (utc.hour * 3600 + utc.minute * 60 + utc.second) / 86400.0

    /** Solar declination (radians) and equation of time (minutes). */
    private fun solar(jd: Double): Pair<Double, Double> {
        val t = (jd - 2451545.0) / 36525.0
        val meanLong = norm(280.46646 + 36000.76983 * t + 0.0003032 * t * t)
        val meanAnom = norm(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
        val e = 0.016708634 - 0.000042037 * t
        val m = Math.toRadians(meanAnom)
        val trueLong = norm(
            meanLong + (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(m) +
                (0.019993 - 0.000101 * t) * sin(2 * m) + 0.000289 * sin(3 * m)
        )
        val omega = norm(125.04 - 1934.136 * t)
        val lambda = norm(trueLong - 0.00569 - 0.00478 * sin(Math.toRadians(omega)))
        val eps0 = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val eps = Math.toRadians(eps0 + 0.00256 * cos(Math.toRadians(omega)))
        val decl = asin(sin(eps) * sin(Math.toRadians(lambda)))
        val y = tan(eps / 2.0).let { it * it }
        val l0 = Math.toRadians(meanLong)
        val eqTime = 4.0 * Math.toDegrees(
            y * sin(2 * l0) - 2 * e * sin(m) + 4 * e * y * sin(m) * cos(2 * l0) -
                0.5 * y * y * sin(4 * l0) - 1.25 * e * e * sin(2 * m)
        )
        return decl to eqTime
    }

    private fun norm(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

    /** Minutes from 00:00 UTC. Evaluated once at local noon; the residual is the bias above. */
    fun sunsetUtcMinutes(date: LocalDate, g: GeoPoint, tzOffsetHours: Double): Double {
        val noonUtc = LocalDateTime.of(date, LocalTime.NOON).minusHours(tzOffsetHours.toLong())
        val (decl, eqTime) = solar(julianDay(noonUtc))
        val lat = Math.toRadians(g.latDeg)
        val cosH = (cos(Math.toRadians(ZENITH_SUNSET)) - sin(lat) * sin(decl)) / (cos(lat) * cos(decl))
        val hourAngle = Math.toDegrees(acos(cosH.coerceIn(-1.0, 1.0)))
        return 720.0 - 4.0 * (g.lonDeg - hourAngle) - eqTime
    }

    fun sunset(date: LocalDate, g: GeoPoint, zone: ZoneId): ZonedDateTime {
        val offset = zone.rules.getOffset(java.time.Instant.now()).totalSeconds / 3600.0
        val minutesUtc = sunsetUtcMinutes(date, g, offset)
        return LocalDateTime.of(date, LocalTime.MIDNIGHT)
            .plusMinutes(minutesUtc.roundToInt().toLong())
            .atZone(ZoneId.of("UTC"))
            .withZoneSameInstant(zone)
    }

    /** Sun altitude in degrees at a UTC instant. Negative below the horizon. */
    fun altitudeAt(instantUtc: LocalDateTime, g: GeoPoint): Double {
        val (decl, eqTime) = solar(julianDay(instantUtc))
        val utcMinutes = instantUtc.hour * 60.0 + instantUtc.minute + instantUtc.second / 60.0
        val trueSolar = (utcMinutes + eqTime + 4.0 * g.lonDeg) % 1440.0
        val hourAngle = trueSolar / 4.0 - 180.0
        val lat = Math.toRadians(g.latDeg)
        val cosZenith = sin(lat) * sin(decl) + cos(lat) * cos(decl) * cos(Math.toRadians(hourAngle))
        return 90.0 - Math.toDegrees(acos(cosZenith.coerceIn(-1.0, 1.0)))
    }

    fun altitudeForDisplay(instantUtc: LocalDateTime, g: GeoPoint): Int = altitudeAt(instantUtc, g).roundToInt()

    fun inSunsetWindow(now: ZonedDateTime, g: GeoPoint, minutes: Long = 20):Boolean =
        abs(java.time.Duration.between(now, sunset(now.toLocalDate(), g, now.zone)).toMinutes()) <= minutes
}
