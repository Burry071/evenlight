package com.burry071.evenlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test fun bilinearDownscaleOfAHalfBlackHalfWhiteRowIsTheMidpoint() {
        val w = 128
        val px = IntArray(2 * w) { x -> val col = x % w; if (col < w / 2) Pixels.rgb(0, 0, 0) else Pixels.rgb(255, 255, 255) }
        assertEquals(Pixels.rgb(128, 128, 128), BandSampler.sample(px, w, 2).ordered[0])
    }

    @Test fun meanLuminanceIsWithinUnitRange() {
        val v = (0 until 8).map { Pixels.rgb(it * 30, 255 - it * 30, 128) }
        assertTrue(BandSampler.sample(rows(64, v), 64, 8).meanLuminance in 0.0..1.0)
    }
}
