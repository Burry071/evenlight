package com.burry071.evenlight

import com.burry071.evenlight.card.CardRenderer
import com.burry071.evenlight.capture.Still
import com.burry071.evenlight.core.Pixels
import com.burry071.evenlight.data.Settings
import com.burry071.evenlight.data.Store
import com.burry071.evenlight.wording.Wording
import com.burry071.evenlight.wording.WordingEngine
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The controller's guarantees - filing half first, the model half never gating it, render-then-card-then-record
 * on the upgrade, blocking work off the caller's thread - proven on a JVM by replacing exactly the three things
 * a JVM cannot do (take a camera frame, paint a Bitmap, load a 1.2 GB engine) with observable seams. Everything
 * the seams feed - settings, sampling, matching, the clock, both file writes - is the real code over a real Store.
 */
class EvenlightControllerTest {
    private var dir: File? = null

    @After fun tearDown() { dir?.deleteRecursively() }

    private class FakeEngine(
        val consulted: Boolean = true,
        val answer: String? = "still light holds the low sky",
        val crash: Throwable? = null,
        val preOpen: Boolean = true,
    ) : WordingEngine {
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        var ensures = 0
        var lines = 0
        var closes = 0
        init { if (preOpen) gate.complete(Unit) }
        override suspend fun ensure(): Boolean {
            ensures++
            crash?.let { throw it }
            started.complete(Unit)
            gate.await()
            return consulted
        }
        override suspend fun line(name: String, bands: List<String>, altitudeDeg: Int): String? {
            lines++
            return answer
        }
        override fun close() { closes++ }
    }

    private inner class Fixture(settingsSet: Boolean = true, framePixels: IntArray = IntArray(64 * 8) { STILL }) {
        val store: Store
        val cardCalls = mutableListOf<String?>()   // wording_source on disk at each render
        val frameThreads = mutableListOf<String>()
        val rotations = mutableListOf<Int>()
        val callerThread = Thread.currentThread().name
        private val pixels: IntArray = framePixels
        private var cardSerial = 0

        init {
            val d = File(System.getProperty("java.io.tmpdir"), "evenlight-ctrl-" + System.nanoTime())
            dir = d
            store = Store(d)
            if (settingsSet) store.saveSettings(Settings("hostel balcony", 31.52, 74.35))
        }

        fun card(bands: List<Int>, header: CardRenderer.Header): ByteArray {
            cardCalls.add(store.readRecords().firstOrNull()?.wordingSource)
            cardSerial++
            return byteArrayOf('c'.code.toByte(), cardSerial.toByte())
        }

        fun frame(rotationDegrees: Int): Still {
            frameThreads.add(Thread.currentThread().name)
            rotations.add(rotationDegrees)
            return Still(pixels, if (pixels.isEmpty()) 0 else 64, if (pixels.isEmpty()) 0 else 8, byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        }
    }

    private fun controller(fx: Fixture, engine: WordingEngine) = EvenlightController(
        store = fx.store,
        card = { bands, header -> fx.card(bands, header) },
        frame = { rotation -> fx.frame(rotation) },
        time = Timebase({ LocalDateTime.of(2026, 10, 8, 17, 30) }, { 0L }),
        displayRotation = { 0 },
        engine = engine
    )

    @Test fun filingReturnsWhileTheEngineIsUntouchedAndTheUpgradeRewritesCardThenRecord() = runBlocking {
        // Findings 1.3 and 1.4 together. The card the shutter files must be on disk and revealed while the
        // engine is still being loaded; the model's line lands afterwards as an upgrade, and when it does the
        // PNG is re-rendered BEFORE the record is rewritten, so a crash between the two leaves an evening
        // whose JSON and card still tell the same (template) story.
        val fx = Fixture()
        val engine = FakeEngine(preOpen = false)
        val controller = controller(fx, engine)

        val filed = controller.fileShot().getOrThrow()

        assertEquals("none", filed.wordingSource)
        assertEquals("still", filed.name)
        assertEquals(0, filed.winnerIndex)
        assertEquals(Wording.template("still"), filed.wording)
        assertEquals(List(8) { "#4A5560" }, filed.bandsHex)
        assertEquals("2026-10-08", filed.date)
        assertEquals("2026-10-08--1730", filed.shutter)
        assertFalse(filed.clockSuspect)
        // Display rotation 0 asks the frame source for a clockwise-90 re-derive (the measured EXIF-6 row).
        assertEquals(listOf(90), fx.rotations)

        val recordFile = fx.store.recordFile("2026-10-08", 1)
        val cardFile = fx.store.cardFile("2026-10-08")
        val rawFile = fx.store.rawFile("2026-10-08--1730", 1)
        assertTrue(recordFile.exists()); assertTrue(cardFile.exists()); assertTrue(rawFile.exists())
        assertArrayEquals(byteArrayOf('c'.code.toByte(), 1), cardFile.readBytes())
        // The record leads the render: at the only card call so far, the JSON was already on disk saying "none".
        assertEquals(listOf("none"), fx.cardCalls)
        // The reveal half never touched the engine - not an init, not a line.
        assertEquals(0, engine.ensures)
        assertFalse(engine.started.isCompleted)
        // Finding 1.3: the capture and everything after it ran off the calling thread (Dispatchers.IO).
        assertTrue(fx.frameThreads.isNotEmpty())
        assertTrue(fx.frameThreads.all { it != fx.callerThread })

        val upgrade = launch { controller.upgradeWording(filed) }
        engine.started.await()
        // While the model is still being consulted, nothing on disk has moved: tonight keeps the filed pair.
        assertEquals("none", fx.store.readRecords().single().wordingSource)
        assertArrayEquals(byteArrayOf('c'.code.toByte(), 1), cardFile.readBytes())

        engine.gate.complete(Unit)
        upgrade.join()

        val upgraded = fx.store.readRecords().single()
        assertEquals("model", upgraded.wordingSource)
        assertEquals("still light holds the low sky", upgraded.wording)
        assertFalse(upgraded.wordingRejected)
        assertArrayEquals(byteArrayOf('c'.code.toByte(), 2), cardFile.readBytes())
        // At the SECOND render the disk still held the "none" record - card written, then record rewritten.
        assertEquals(listOf("none", "none"), fx.cardCalls)
        // Same (date, attempt): the upgrade overwrote, it did not append a second evening.
        assertEquals(1, fx.store.attemptsFor("2026-10-08"))
    }

    @Test fun anUpgradeThatLandsAfterANewerShotLeavesTheNewerCardAlone() = runBlocking {
        // The reveal no longer waits for the model, so the shutter is live while the upgrade runs: a second shot
        // can be filed while the first is still inside the engine, and the README measures that inside at
        // 1,080 / 1,247 / 3,502 ms per line - long enough for a re-press. If the late answer then rewrote the
        // day's card, today's PNG would carry the earlier attempt's bands and line - permanently, whenever the
        // newer attempt's own upgrade is provenance-only and never re-renders.
        val fx = Fixture()
        val engine = FakeEngine(preOpen = false)
        val controller = controller(fx, engine)

        val first = controller.fileShot().getOrThrow()
        assertEquals(1, first.attempt)
        val card = fx.store.cardFile("2026-10-08")
        assertArrayEquals(byteArrayOf('c'.code.toByte(), 1), card.readBytes())

        val upgrade = launch { controller.upgradeWording(first) }
        engine.started.await()

        val second = controller.fileShot().getOrThrow()
        assertEquals(2, second.attempt)
        assertArrayEquals(byteArrayOf('c'.code.toByte(), 2), card.readBytes())

        engine.gate.complete(Unit)
        upgrade.join()

        assertArrayEquals(byteArrayOf('c'.code.toByte(), 2), card.readBytes())
        val records = fx.store.readRecords()
        assertEquals(2, records.size)
        assertEquals("none", records.first { it.attempt == 1 }.wordingSource)
        assertEquals("none", records.first { it.attempt == 2 }.wordingSource)
        assertEquals(2, fx.cardCalls.size)
    }

    @Test fun aProvenanceOnlyUpgradeRewritesTheRecordWithoutRedrawingTheCard() = runBlocking {
        // The wording on a card is drawn text, so a provenance change that leaves the SAME template line must
        // not pay for a re-render - and a no-answer evening records a fact the card cannot show anyway.
        val fx = Fixture()
        val engine = FakeEngine(answer = null)
        val controller = controller(fx, engine)

        val filed = controller.fileShot().getOrThrow()
        val upgraded = controller.upgradeWording(filed)

        assertEquals("no-answer", upgraded.wordingSource)
        assertEquals(Wording.template("still"), upgraded.wording)
        assertEquals(listOf("none"), fx.cardCalls)
        assertArrayEquals(byteArrayOf('c'.code.toByte(), 1), fx.store.cardFile("2026-10-08").readBytes())
        assertEquals("no-answer", fx.store.readRecords().single().wordingSource)
        assertEquals(1, engine.ensures)
        assertEquals(1, engine.lines)
    }

    @Test fun anEngineThatThrowsLeavesTonightOnItsTemplateLine() = runBlocking {
        // A failed best-effort is not a failed shot: whatever the engine throws, the evening keeps exactly what
        // filing wrote - template record, filed card, one attempt, no rewrite.
        val fx = Fixture()
        val engine = FakeEngine(crash = RuntimeException("engine died"))
        val controller = controller(fx, engine)

        val filed = controller.fileShot().getOrThrow()
        val kept = controller.upgradeWording(filed)

        assertEquals(filed, kept)
        assertEquals("none", fx.store.readRecords().single().wordingSource)
        assertEquals(listOf("none"), fx.cardCalls)
        assertArrayEquals(byteArrayOf('c'.code.toByte(), 1), fx.store.cardFile("2026-10-08").readBytes())
    }

    @Test fun cancellationTravelsBackOutOfTheUpgradeInsteadOfFilingQuietlyAsWrong() = runBlocking {
        val fx = Fixture()
        val engine = FakeEngine(crash = CancellationException("caller cancelled"))
        val controller = controller(fx, engine)
        val filed = controller.fileShot().getOrThrow()
        var caught: Throwable? = null
        try {
            controller.upgradeWording(filed)
        } catch (t: Throwable) {
            caught = t
        }
        assertTrue("cancellation must not be folded into the filed record", caught is CancellationException)
        assertEquals("none", fx.store.readRecords().single().wordingSource)
    }

    @Test fun aCancelledCaptureIsRethrownFromFilingNotFoldedIntoAFailedResult() = runBlocking {
        // The other rethrow leg: a capture cancelled mid-shot (a destroyed window) must travel back up as
        // cancellation, not arrive at the screen as tonight's failure message.
        val fx = Fixture()
        val controller = EvenlightController(
            store = fx.store,
            card = { bands, header -> fx.card(bands, header) },
            frame = { throw CancellationException("window destroyed") },
            time = Timebase({ LocalDateTime.of(2026, 10, 8, 17, 30) }, { 0L }),
            displayRotation = { 0 },
            engine = FakeEngine()
        )
        var caught: Throwable? = null
        try {
            controller.fileShot()
        } catch (t: Throwable) {
            caught = t
        }
        assertTrue("fileShot must rethrow its own cancellation, not fold it", caught is CancellationException)
        assertEquals(0, fx.store.readRecords().size)
    }

    @Test fun noPlaceSetFilesNothing() = runBlocking {
        val fx = Fixture(settingsSet = false)
        val engine = FakeEngine()
        val controller = controller(fx, engine)
        val outcome = controller.fileShot()
        assertTrue(outcome.isFailure)
        assertEquals("no place set yet", outcome.exceptionOrNull()?.message)
        assertEquals(0, fx.store.readRecords().size)
        assertTrue(fx.cardCalls.isEmpty())
        assertEquals(0, engine.ensures)
    }

    @Test fun anEmptyFrameFailsTheShotBeforeAnyEveningIsWritten() = runBlocking {
        val fx = Fixture(framePixels = IntArray(0))
        val controller = controller(fx, FakeEngine())
        val outcome = controller.fileShot()
        assertTrue(outcome.isFailure)
        assertEquals("capture produced no pixels", outcome.exceptionOrNull()?.message)
        assertEquals(0, fx.store.readRecords().size)
        assertTrue(fx.cardCalls.isEmpty())
    }

    @Test fun closeReachesTheEngineExactlyOnce() {
        // Finding 1.5's lifetime claim in one line: the controller delegates close instead of owning an engine
        // of its own, so the activity retiring one controller retires one engine - and a settings save that no
        // longer builds a controller has no holder to close either.
        val fx = Fixture()
        val engine = FakeEngine()
        val controller = controller(fx, engine)
        controller.close()
        assertEquals(1, engine.closes)
    }

    companion object {
        // The vocabulary's "still" (#4A5560): a uniform frame of it makes name, winner and all eight band
        // hexes exactly predictable without re-implementing the sampler in the test.
        private val STILL = Pixels.rgb(0x4A, 0x55, 0x60)
    }
}
