package com.burry071.evenlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardGeometryTest {
    private val g = CardGeometry()

    @Test fun fieldTakesSixtyTwoPercentOfTheCard() {
        assertEquals(893, g.fieldHeight)
        assertEquals(893, g.paperTop)
    }

    @Test fun bandCentresAreMonotonicAndInsideTheField() {
        val centres = (0 until 8).map { g.bandCentreY(it) }
        assertTrue(centres.zipWithNext().all { (a, b) -> b > a })
        assertTrue(centres.first() >= 0.0 && centres.last() < g.fieldHeight)
    }

    @Test fun bandCentresSitAtHalfBandOffsets() {
        // `bandCentreY` is `(i + 0.5) * bandHeight`, and the 0.5 is the whole design: it is why the top of the
        // field is band 0 untouched rather than a blend of nothing with band 0, and why row 0 reads as the sky's
        // first measured colour. With `(i + 0.6)` instead, all 80 other tests still pass - verified by mutation -
        // because every blend test below only asks for monotonicity and endpoints. These two numbers are exactly
        // 0.5 * 111.625 and 7.5 * 111.625, so they are stated rather than recomputed from the code under test.
        assertEquals(55.8125, g.bandCentreY(0), 0.0)
        assertEquals(837.1875, g.bandCentreY(7), 0.0)
    }

    @Test fun aboveTheFirstCentreIsPurelyBandZero() {
        assertEquals(0.0, g.fieldBlend(0), 1e-6)
        assertEquals(0.0, g.fieldBlend(40), 1e-6)
    }

    @Test fun belowTheLastCentreIsPurelyBandSeven() {
        assertEquals(7.0, g.fieldBlend(g.fieldHeight - 1), 1e-6)
    }

    @Test fun halfwayBetweenTwoCentresIsAnEvenBlend() {
        val mid = ((g.bandCentreY(2) + g.bandCentreY(3)) / 2).toInt()
        assertEquals(2.5, g.fieldBlend(mid), 0.02)
    }

    @Test fun blendingTwoBandsAveragesTheChannelsAndRoundsHalfUp() {
        val b = MutableList(8) { Pixels.rgb(0, 0, 0) }.also { it[1] = Pixels.rgb(200, 100, 40) }
        assertEquals(Palette.opaque(Pixels.rgb(0, 0, 0)), g.blendOf(b, 0.0))
        assertEquals(Palette.opaque(Pixels.rgb(200, 100, 40)), g.blendOf(b, 1.0))
        assertEquals(Palette.opaque(Pixels.rgb(100, 50, 20)), g.blendOf(b, 0.5))
        // The three cases above average to exact integers, so none of them exercises a rounding rule. Odd
        // channels land the average on exactly .5, which is where Kotlin and Python can part company: half-up
        // gives 101/51/21, banker's rounding gives 100/50/20. Task 10 mirrors this line in Python, so the tie
        // direction is cross-language contract, not local style.
        val odd = MutableList(8) { Pixels.rgb(0, 0, 0) }.also { it[1] = Pixels.rgb(201, 101, 41) }
        assertEquals(Palette.opaque(Pixels.rgb(101, 51, 21)), g.blendOf(odd, 0.5))
    }

    @Test fun aRowThatBlendsOntoTheExactHalfKeepsItInBinary64() {
        // Row 57 sits where the blue ramp from band 0 to band 1 crosses a rounding tie exactly. The blend there
        // is (57 - 55.8125) / (167.4375 - 55.8125) = 1.1875 / 111.625 = 19/1786, and 47 * 19/1786 = 0.5 to the
        // penny - 47 * 1.1875 = 55.8125 = half of 111.625 - so the answer depends entirely on the precision the
        // ratio is kept in: binary64 holds exactly 0.5 and half-up gives 1, while Float quantises the ratio to
        // 0.010638297535..., whose product with 47 is 0.49999998 and gives 0. This is the only test that dies if
        // `fieldBlend` ever returns `.toFloat()` again, and it is why Task 10's Python can trust us: Python has
        // no 32-bit path here at all. Verified numerically, not reasoned, at 893/8 band height.
        val bands = MutableList(8) { Pixels.rgb(0, 0, 0) }.also { it[1] = Pixels.rgb(0, 0, 47) }
        assertEquals(Palette.opaque(Pixels.rgb(0, 0, 1)), g.blendOf(bands, g.fieldBlend(57)))
    }

    @Test fun everyColourTheGeometryHandsOutIsOpaque() {
        // BandSampler (T3) packs a measured band as 0x00RRGGBB, and that packing is the record's and Python's.
        // Canvas and Compose read the high byte as alpha, so drawing one of those ints paints nothing: the
        // field, the winner strip and the neighbour chips all vanish, and no int-equality test here can see it
        // because none of them asks which byte is which. Palette.opaque is the single crossing point, so the
        // alpha claim is checked where the crossing happens - and the first assertion checks the trap is real.
        fun alpha(c: Int) = (c ushr 24) and 0xFF
        val bands = List(8) { Pixels.rgb(201, 101, 41) }
        assertEquals(0, alpha(bands[0]))
        for (t in doubleArrayOf(0.0, 0.5, 2.7, 7.0)) assertEquals(0xFF, alpha(g.blendOf(bands, t)))
        assertEquals(0xFF, alpha(g.inkOn(bands[0])))
        assertEquals(0xFF, alpha(Palette.opaque(bands[0])))
        // Promoting must not shift or clip a channel, or the strip stops being the measured colour.
        assertEquals(bands[0], Palette.opaque(bands[0]) and 0x00FFFFFF)
    }

    @Test fun aDarkWinnerStripCarriesPaper() {
        assertEquals(Palette.PAPER, g.inkOn(Pixels.parseHex("#C2854A")))   // luminance 0.287
    }

    @Test fun aPaleWinnerStripCarriesInk() {
        assertEquals(Palette.INK, g.inkOn(Pixels.parseHex("#E3DED4")))
    }

    @Test fun theThresholdSitsAtFortyFivePercentLuminance() {
        val dark = Pixels.rgb(180, 150, 120)
        val pale = Pixels.rgb(210, 210, 205)
        assertTrue(Pixels.relativeLuminance(dark) < 0.45)
        assertTrue(Pixels.relativeLuminance(pale) > 0.45)
        assertEquals(Palette.PAPER, g.inkOn(dark))
        assertEquals(Palette.INK, g.inkOn(pale))
    }

    @Test fun aSmallerCardKeepsTheSameProportions() {
        assertEquals(446, CardGeometry(540, 720).fieldHeight)   // 0.62 * 720 = 446.4 -> half-up
    }

    @Test fun theCardIsTheArtefactSizeAndTheFieldIsEightBands() {
        // 1080x1440 is the exported artefact, and `bandHeight` is what every centre and blend is built from.
        // Neither is asserted anywhere else, so a default changed in either direction reads as a style tweak.
        assertEquals(1080, g.width)
        assertEquals(1440, g.height)
        assertEquals(893 / 8.0, g.bandHeight, 1e-9)
    }

    @Test fun theWinnerStripIsTheBottomOfTheField() {
        // 96 px at a 1440 card is the mockup's number, and the strip is where a measured colour is shown
        // unmixed - so its geometry is a colour-honesty fact, not a layout nicety. Nothing else pins it.
        assertEquals(96, g.stripHeight)
        assertEquals(797, g.stripTop)
        assertEquals(g.fieldHeight - g.stripHeight, g.stripTop)
    }

    @Test fun theInkThresholdIsExactlyWhereTheDesignSays() {
        // The two colours in the tests above sit at 0.287 and 0.73 luminance, so any threshold between them
        // passes - which means 0.45 itself is unpoliced. Grey 178 is 0.4452 and grey 179 is 0.4508, so these
        // four lines pin the boundary to within one grey level.
        assertTrue(Pixels.relativeLuminance(Pixels.rgb(178, 178, 178)) < 0.45)
        assertTrue(Pixels.relativeLuminance(Pixels.rgb(179, 179, 179)) > 0.45)
        assertEquals(Palette.PAPER, g.inkOn(Pixels.rgb(178, 178, 178)))
        assertEquals(Palette.INK, g.inkOn(Pixels.rgb(179, 179, 179)))
    }

    @Test fun thePaletteIsTheDesignSystemVerbatim() {
        // The app's chrome reads these constants, not the hex literals in the design system, so a mistyped
        // digit here is invisible to every other test - and a brand hue leaking into chrome is the failure
        // the spec forbids. Pinned as packed ARGB, including the alpha, because 0xF4EFE6 would compile too.
        assertEquals(0xFFF4EFE6.toInt(), Palette.PAPER)
        assertEquals(0xFFFBF8F2.toInt(), Palette.CARD)
        assertEquals(0xFF2A2622.toInt(), Palette.INK)
        assertEquals(0xFF6E6656.toInt(), Palette.MUTED)
        assertEquals(0xFFD9D0BF.toInt(), Palette.HAIRLINE)
        assertEquals(0xFF17150F.toInt(), Palette.NIGHT_SURFACE)
        assertEquals(0xFFE9E3D6.toInt(), Palette.NIGHT_TEXT)
        assertEquals(0xFF332F26.toInt(), Palette.NIGHT_HAIRLINE)
    }
}
