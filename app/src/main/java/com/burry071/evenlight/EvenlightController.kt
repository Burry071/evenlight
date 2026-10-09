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
import kotlinx.coroutines.CancellationException
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
     * Two reads of one wall clock around one shutter press, so what this catches is a jump that lands *during* the
     * capture - an NTP correction at 17:59 - and what it cannot catch is a clock rewound before the app started.
     *
     * It catches less than that, and the earlier version of this comment was wrong to imply otherwise: it said the
     * stored shutter name is what defends the stack's order, but readRecords() orders by the filename, and the
     * filename is built from this same wall clock. A clock rewound last Tuesday therefore really does move that
     * evening's row in the stack, and nothing in the app notices. So this flag is a mark on one row saying "the
     * two clocks disagreed here" (spec section 11) - not a guard on the stack, and not a diagnosis either.
     *
     * A third thing trips it: System.nanoTime() is CLOCK_MONOTONIC and does not advance while the device sleeps,
     * while LocalDateTime.now() does, so a phone that dozed between the two reads shows wall time gone by and
     * monotonic time not, and the row is marked for a reason that is not a clock fault at all.
     */
    private fun clockSuspect(now: LocalDateTime, monoBefore: Long): Boolean {
        val wallAdvance = Duration.between(now, time.wallClock()).toMillis()
        val monoAdvance = time.monotonicMillis() - monoBefore
        return wallAdvance < 0 || abs(wallAdvance - monoAdvance) > 5_000
    }

    suspend fun shoot(): Result<EveningRecord> {
        val outcome = runCatching {
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
            store.writeRecord(record, attempt)          // the record leads: it is the datum, and no later failure may lose it
            store.writeRaw(still.jpeg, record.shutter, attempt)
            store.writeCard(date, pngBytes(renderer.render(bands.ordered, headerFor(record, match))))
            record
        }
        // runCatching catches Throwable, which is what a shutter crash needs - but that includes the coroutine's own
        // cancellation, which would otherwise become a failed Result a destroyed window is then asked to draw into.
        // So cancellation travels back up and everything else is reported. Task 9 inherits this file, and a
        // coroutine that swallows its own cancellation stops being obvious once it is load-bearing.
        outcome.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        return outcome
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
         * The device-shaped controller. The typefaces are the two bundled variable TTFs from Task 9 Step 1: the
         * strip name is laid out for Fraunces at 116f, and with `Typeface.DEFAULT` it crowded the strip - which was
         * the known cost of landing the nightly path one night, and is retired here.
         *
         * `Resources.getFont` is API 26 and this module's minSdk is 27, so no compatibility branch is needed. The
         * faces are read from the resource table rather than from `androidx.compose.ui.text.font.Font`, because the
         * card is drawn with a `Canvas` and a `Paint`, not with Compose: the two halves of the app share the file,
         * not the type.
         */
        fun forDevice(activity: Context, store: Store, settings: Settings, frame: FrameSource): EvenlightController =
            EvenlightController(
                store = store, settings = settings, frame = frame,
                renderer = CardRenderer(
                    activity.resources.getFont(R.font.fraunces_variable),
                    activity.resources.getFont(R.font.instrument_sans_variable)
                ),
                time = Timebase.SYSTEM,
                displayRotation = {
                    @Suppress("DEPRECATION")
                    (activity as? android.app.Activity)?.windowManager?.defaultDisplay?.rotation ?: 0
                }
            )
    }
}
