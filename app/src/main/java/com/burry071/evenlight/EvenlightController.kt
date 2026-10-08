package com.burry071.evenlight

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import com.burry071.evenlight.card.CardRenderer
import com.burry071.evenlight.card.headerFor
import com.burry071.evenlight.capture.FrameSource
import com.burry071.evenlight.core.BandSampler
import com.burry071.evenlight.core.EveningRecord
import com.burry071.evenlight.core.GeoPoint
import com.burry071.evenlight.core.Matcher
import com.burry071.evenlight.core.Pixels
import com.burry071.evenlight.core.SunWindow
import com.burry071.evenlight.data.Settings
import com.burry071.evenlight.data.Store
import com.burry071.evenlight.wording.Wording
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * Two clocks, injected rather than called directly. `SystemClock.elapsedRealtime()` was the obvious monotonic
 * source and is the wrong one: with `unitTests.isReturnDefaultValues = true` it reads 0 in every JVM test, so a
 * drift test would pass by proving nothing. System.nanoTime() is monotonic, JVM-visible, and only its deltas are
 * used.
 */
class Timebase(val wallClock: () -> LocalDateTime, val monotonicMillis: () -> Long) {
    companion object {
        val SYSTEM = Timebase({ LocalDateTime.now() }, { System.nanoTime() / 1_000_000 })
    }
}

class EvenlightController(
    private val store: Store,
    private val settings: Settings,
    private val frame: FrameSource,
    private val renderer: CardRenderer,
    private val time: Timebase,
    private val displayRotation: () -> Int
) {

    /**
     * Narrowest wall-clock guard this task can justify: the two reads bracket one shutter press, so a jump *during*
     * the capture - an NTP correction landing at 17:59 - is caught, and a clock rewound last Tuesday is not. What
     * actually defends the stack's order against a rewound clock is the shutter name stored in every record; this
     * flag marks the row rather than silently reordering it (spec section 11).
     */
    private fun clockSuspect(now: LocalDateTime, monoBefore: Long): Boolean {
        val wallAdvance = Duration.between(now, time.wallClock()).toMillis()
        val monoAdvance = time.monotonicMillis() - monoBefore
        return wallAdvance < 0 || abs(wallAdvance - monoAdvance) > 5_000
    }

    suspend fun shoot(): Result<EveningRecord> = runCatching {
        // No coordinates, no card. The sun line would otherwise print an altitude computed for the Gulf of Guinea
        // over a sky in Lahore: a number the app did not measure, which this project does not put on a card.
        check(settings.isSet) { "no place set yet" }

        val monoBefore = time.monotonicMillis()
        val now = time.wallClock()

        val still = frame.take(FrameSource.rotationFor(displayRotation()))
        require(still.pixels.isNotEmpty()) { "capture produced no pixels" }

        val bands = BandSampler.sample(still.pixels, still.width, still.height)
        val match = Matcher.match(bands.ordered)
        val altitude = SunWindow.altitudeForDisplay(
            now.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),
            GeoPoint(settings.lat, settings.lon)
        )
        val date = now.toLocalDate().toString()
        val attempt = store.attemptsFor(date) + 1
        val suspect = clockSuspect(now, monoBefore)

        val record = EveningRecord(
            date = date,
            shutter = store.nextShutterName(now),
            bandsHex = bands.ordered.map { Pixels.hex(it) },
            winnerIndex = match.winnerIndex,
            name = match.name,
            sunAltitudeDeg = altitude,
            meanLuminance = bands.meanLuminance,
            wording = Wording.template(match.name),
            wordingSource = "none",     // the engine arrives in T11; "none" means it was never consulted
            wordingRejected = false,
            attempt = attempt,
            clockSuspect = suspect
        )
        store.writeRaw(still.jpeg, record.shutter, attempt)
        store.writeRecord(record, attempt)              // record BEFORE render: a render failure must not lose the evening
        store.writeCard(date, pngBytes(renderer.render(bands.ordered, headerFor(record, match))))
        record
    }

    private fun pngBytes(bmp: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        // Quality is meaningless for PNG; 100 says so rather than leaving a number that looks like a choice.
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }

    companion object {
        /**
         * The device-shaped controller. The typefaces are the platform defaults because Fraunces and Instrument
         * Sans only land in Task 9; the strip name is laid out for Fraunces at 116f, so with DEFAULT it may crowd
         * the strip. That is the known cost of landing the nightly path tonight, and Task 9's fonts retire it.
         */
        fun forDevice(activity: Context, store: Store, settings: Settings, frame: FrameSource): EvenlightController =
            EvenlightController(
                store = store, settings = settings, frame = frame,
                renderer = CardRenderer(Typeface.DEFAULT, Typeface.SANS_SERIF),
                time = Timebase.SYSTEM,
                displayRotation = {
                    @Suppress("DEPRECATION")
                    (activity as? android.app.Activity)?.windowManager?.defaultDisplay?.rotation ?: 0
                }
            )
    }
}
