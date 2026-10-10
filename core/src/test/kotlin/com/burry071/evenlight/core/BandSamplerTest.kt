package com.burry071.evenlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class BandSamplerTest {
    private fun rows(w: Int, values: List<Int>): IntArray {
        val h = values.size
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) out[y * w + x] = values[y]
        return out
    }

    @Test fun eightRowRampReturnsItsOwnEightColoursInOrder() {
        val v = (0 until 8).map { Pixels.rgb(10 + it * 20, 200 - it * 10, 30 + it * 5) }
        val bands = BandSampler.sample(rows(64, v), 64, 8)
        assertEquals(v, bands.ordered)
        assertEquals("#1EBE23", Pixels.hex(bands.ordered[1]))
    }

    @Test fun topOfFrameIsBandZero() {
        val v = listOf(Pixels.rgb(20, 24, 40), Pixels.rgb(20, 24, 40),
                       Pixels.rgb(200, 160, 90), Pixels.rgb(200, 160, 90))
        val bands = BandSampler.sample(rows(64, v), 64, 4)
        assertEquals(8, bands.ordered.size)
        assertEquals(Pixels.rgb(20, 24, 40), bands.ordered[0])
        assertEquals(Pixels.rgb(200, 160, 90), bands.ordered[7])
    }

    @Test fun fewerRowsThanBandsRepeatsTheNearestRowInsteadOfCrashing() {
        val bands = BandSampler.sample(rows(8, listOf(Pixels.rgb(255, 0, 0), Pixels.rgb(0, 0, 255))), 8, 2)
        assertEquals(8, bands.ordered.size)
        repeat(4) { assertEquals(Pixels.rgb(255, 0, 0), bands.ordered[it]) }
        repeat(4) { assertEquals(Pixels.rgb(0, 0, 255), bands.ordered[4 + it]) }
    }

    @Test fun uniformFrameGivesEightIdenticalBands() {
        val c = Pixels.rgb(120, 130, 140)
        val bands = BandSampler.sample(IntArray(64 * 48) { c }, 64, 48)
        assertEquals(List(8) { c }, bands.ordered)
        assertEquals(Pixels.relativeLuminance(c), bands.meanLuminance, 1e-9)
    }

    @Test fun bilinearDownscaleOfAHalfBlackHalfAlmostWhiteRowRoundsTheTieHalfUp() {
        // Finding 2.1: the old version of this test averaged 0 and 255, a mean of exactly 127.5 - the one .5
        // value where half-up and half-even AGREE (128 is even), so it looked like a tie assertion while pinning
        // nothing. 253 puts the band mean at 32 * 253 / 64 = 126.5: half-up gives 127, half-even gives 126. The
        // bilinear pass itself cannot land on the tie here (fx = 2dx + 0.5 pairs whole columns, so every small
        // pixel is 0 or 253); the tie is the band average, which is where Pixels.roundHalfUp decides.
        // test_skycard.py mirrors this exact frame so the tie direction stays a cross-language contract.
        val w = 128
        val px = IntArray(2 * w) { x -> val col = x % w; if (col < w / 2) Pixels.rgb(0, 0, 0) else Pixels.rgb(253, 253, 253) }
        assertEquals(Pixels.rgb(127, 127, 127), BandSampler.sample(px, w, 2).ordered[0])
    }

    @Test fun meanLuminanceIsStrictlyBetweenTheBandExtremes() {
        val v = (0 until 8).map { Pixels.rgb(it * 30, 255 - it * 30, 128) }
        val sampled = BandSampler.sample(rows(64, v), 64, 8)
        val luminances = sampled.ordered.map { Pixels.relativeLuminance(it) }
        // Strict, not inclusive: `mean in 0.0..1.0` can only ever fail on NaN/Inf, because the mean
        // of eight values that are each provably in [0,1] is in [0,1]. min < mean < max additionally
        // fails if the aggregate collapses onto a single band (the mean then equals one of the two
        // extremes it is supposed to sit strictly between) or drifts outside their span.
        val mean = sampled.meanLuminance
        assertTrue(luminances.toString(), mean > luminances.min())
        assertTrue(luminances.toString(), mean < luminances.max())
        assertTrue(luminances.toString(), mean in 0.0..1.0)
    }

    @Test fun meanLuminanceOfOneWhiteRowAmongSevenBlackRowsIsExactlyOneEighth() {
        // Hand-derived, not recomputed from the implementation: relativeLuminance is
        // 0.2126*lin(r) + 0.7152*lin(g) + 0.0722*lin(b); lin(255) = ((1 + 0.055) / 1.055)^2.4 = 1
        // and lin(0) = 0 / 12.92 = 0, so white = 0.2126 + 0.7152 + 0.0722 = 1.0 and black = 0.0.
        // dh = 8 so band i is source row i exactly, and meanLuminance = (1.0 + 7 * 0.0) / 8 = 0.125.
        // This exact golden is the teeth the open interval above lacks: dropping the white band from
        // the sum, counting any band twice, or dividing by anything other than 8 all move it off 0.125.
        val white = Pixels.rgb(255, 255, 255)
        val black = Pixels.rgb(0, 0, 0)
        val sampled = BandSampler.sample(rows(64, listOf(white) + List(7) { black }), 64, 8)
        val luminances = sampled.ordered.map { Pixels.relativeLuminance(it) }
        assertEquals(0.125, sampled.meanLuminance, 1e-12)
        assertTrue(luminances.toString(), sampled.meanLuminance > luminances.min())
        assertTrue(luminances.toString(), sampled.meanLuminance < luminances.max())
    }

    @Test fun dhNotAMultipleOfEightTilesTheRowsExactlyOnce() {
        // 64x10 -> dh = min(10, roundHalfUp(10 * 64 / 64)) = 10, and 10 is not a multiple of 8.
        // Integer row tiling (BandSampler.sample), hand-derived: band i covers rows
        // from = min(i*10/8, 9) .. to = min(max((i+1)*10/8, i*10/8 + 1) - 1, 9)
        //   i=0: y0=0,  y1=1  -> 0..0      i=4: y0=5, y1=6  -> 5..5
        //   i=1: y0=1,  y1=2  -> 1..1      i=5: y0=6, y1=7  -> 6..6
        //   i=2: y0=2,  y1=3  -> 2..2      i=6: y0=7, y1=8  -> 7..7
        //   i=3: y0=3,  y1=5  -> 3..4      i=7: y0=8, y1=10 -> 8..9
        // Rows 3+4 and 8+9 are averaged; every other band is a single row, so all ten source rows
        // are covered exactly once. Row colours are rgb(20 + 20y, 250 - 20y, 30 + 20y), so both
        // averaged pairs have even channel sums and their means are exact integers.
        val rowsOfTen = (0 until 10).map { Pixels.rgb(20 + it * 20, 250 - it * 20, 30 + it * 20) }
        val bands = BandSampler.sample(rows(64, rowsOfTen), 64, 10).ordered
        assertEquals(
            listOf(
                Pixels.rgb(20, 250, 30),     // band 0 = row 0
                Pixels.rgb(40, 230, 50),     // band 1 = row 1
                Pixels.rgb(60, 210, 70),     // band 2 = row 2
                Pixels.rgb(90, 180, 100),    // band 3 = mean(rows 3, 4)
                Pixels.rgb(120, 150, 130),   // band 4 = row 5
                Pixels.rgb(140, 130, 150),   // band 5 = row 6
                Pixels.rgb(160, 110, 170),   // band 6 = row 7
                Pixels.rgb(190, 80, 200)     // band 7 = mean(rows 8, 9)
            ),
            bands
        )
    }

    @Test fun wideFrameWithFewerRowsThanBandsRepeatsWithoutTheClamp() {
        // The 8x2 red/blue fixture above reaches dh = height only because of the min(height, ...)
        // clamp. This one is 200 wide, so the clamp is inert and row repetition comes from the
        // arithmetic alone: dh = max(1, min(3, roundHalfUp(3 * 64 / 200))) = max(1, min(3, 1)) = 1,
        // and with dh = 1 band tiling gives from = to = 0 for all eight bands.
        // The single small row is the half-pixel-centre vertical sample: scaleY = 3 / 1 = 3,
        // fy = (0 + 0.5) * 3 - 0.5 = 1.0 exactly -> y0 = 1, wy = 0, so row 1 of the source is read
        // with weight 1 and rows 0 and 2 with weight 0; horizontally scaleX = 200 / 64 = 3.125,
        // every fx is a dyadic rational, a = b = the same uniform-row colour, and
        // roundHalfUp(c * ((1 - wx) + wx)) = c. So all eight bands are exactly the middle row.
        val middle = Pixels.rgb(200, 160, 90)
        val bands = BandSampler.sample(rows(200, listOf(Pixels.rgb(10, 200, 20), middle, Pixels.rgb(60, 60, 240))), 200, 3).ordered
        assertEquals(List(8) { middle }, bands)
    }

    @Test fun zeroSizedFrameFailsThePreconditionNotTheInterpolator() {
        // require(pixels.size == width * height) passes for 0 == 0 * 0, so without an explicit
        // positivity precondition the bilinear pass reaches coerceIn(0.0, -1.0) and Kotlin throws
        // "Cannot coerce value to an empty range: maximum -1.0 is less than minimum 0.0." - a stdlib
        // message that tells the caller nothing about which argument is wrong.
        val ex = assertThrows(IllegalArgumentException::class.java) {
            BandSampler.sample(IntArray(0), 0, 0)
        }
        assertEquals("width and height must both be positive, got 0x0", ex.message)
    }

    @Test fun hexIsAsciiRegardlessOfTheDefaultLocale() {
        // This exact string is persisted into the evening record (T8) and diffed against Python's
        // ASCII "%02X" (T10), so a non-ASCII nibble is a cross-language contract break, not a
        // cosmetic issue. "%02X%02X%02X".format(...) resolves against Locale.getDefault(), and this
        // JVM's Formatter does substitute digits for locale-resolved conversions: under ar-EG-u-nu-arab
        // String.format("%d", 30) is [0x663, 0x660], not "30".
        // 0x301256 has six digit nibbles and no letter nibbles, so any digit substitution changes it.
        val allDigits = Pixels.rgb(0x30, 0x12, 0x56)
        val withLetters = Pixels.rgb(0x1E, 0xBE, 0x23)
        val saved = Locale.getDefault()
        try {
            for (tag in listOf("en-US", "de-DE", "tr-TR", "ar-EG-u-nu-arab", "fa-IR-u-nu-arab", "hi-IN-u-nu-deva", "th-TH-u-nu-thai")) {
                Locale.setDefault(Locale.forLanguageTag(tag))
                val hex = Pixels.hex(allDigits)
                assertEquals(tag, "#301256", hex)
                assertEquals(tag, 7, hex.length)
                assertTrue(tag, hex.all { it in "#0123456789ABCDEF" })
                assertEquals(tag, "#1EBE23", Pixels.hex(withLetters))
            }
        } finally {
            Locale.setDefault(saved)
        }
    }
}
