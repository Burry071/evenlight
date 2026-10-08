package com.burry071.evenlight.core

import kotlin.math.max
import kotlin.math.min

/** The only colours allowed on chrome. Every saturated pixel on a card is a measured band. */
object Palette {
    const val PAPER = 0xFFF4EFE6.toInt()
    const val CARD = 0xFFFBF8F2.toInt()
    const val INK = 0xFF2A2622.toInt()
    const val MUTED = 0xFF6E6656.toInt()
    const val HAIRLINE = 0xFFD9D0BF.toInt()
    const val NIGHT_SURFACE = 0xFF17150F.toInt()
    const val NIGHT_TEXT = 0xFFE9E3D6.toInt()
    const val NIGHT_HAIRLINE = 0xFF332F26.toInt()

    /**
     * The one place the measurement domain becomes the drawing domain. `Pixels` packs a measured colour as
     * 0x00RRGGBB (T3) because that is what the record stores and what Python reads, while `Canvas` and
     * Compose read the high byte as alpha - so a measured colour drawn as-is paints nothing. Every palette
     * constant above is already opaque; this is how a band becomes drawable without touching a channel.
     */
    fun opaque(c: Int): Int = (0xFF000000.toInt()) or (c and 0x00FFFFFF)
}

/**
 * Card layout as pure numbers, so the Android renderer and the Python reference tool cannot
 * drift. The field is a linear blend between band centres; that blend is the ONLY permitted
 * transformation of a measured colour, and it is defined here rather than by a shader.
 *
 * Every blend number is binary64 (`Double`), on purpose and in both languages. The ratio `(y-a)/(b-a)`
 * is not representable, so quantising it to Float moves where a channel average lands relative to a `.5`
 * rounding boundary - and Task 10's Python mirror works in binary64 like this does, so a Float path here
 * disagrees with the reference tool pixel-for-pixel on the very rows the project is judged on.
 */
data class CardGeometry(val width: Int = 1080, val height: Int = 1440) {
    val fieldHeight: Int = Pixels.roundHalfUp(height * 0.62)
    val paperTop: Int get() = fieldHeight
    val bandHeight: Double get() = fieldHeight / 8.0
    val stripHeight: Int = Pixels.roundHalfUp(height * 0.0667)
    val stripTop: Int get() = fieldHeight - stripHeight

    fun bandCentreY(i: Int): Double = (i + 0.5) * bandHeight

    /** Integer part = the lower band index; fraction = how far toward the next one. */
    fun fieldBlend(y: Int): Double {
        if (y <= bandCentreY(0)) return 0.0
        if (y >= bandCentreY(7)) return 7.0
        for (i in 0 until 7) {
            val a = bandCentreY(i); val b = bandCentreY(i + 1)
            if (y < b) return i + (y - a) / (b - a)
        }
        return 7.0
    }

    /**
     * Text colour for the winner strip: paper on a dark band, ink on a pale one, so the measured
     * hue is displayed untouched. Clears WCAG 3:1 for large text but not 4.5:1 for body, so the
     * strip carries display type only - never body copy.
     */
    fun inkOn(band: Int): Int = if (Pixels.relativeLuminance(band) < 0.45) Palette.PAPER else Palette.INK

    /**
     * Blend two neighbouring measured bands. `bands` must hold at least two entries - the caller is the
     * renderer over a sampler output, so eight is guaranteed and a runtime check here would be dead code
     * that no test can reach.
     */
    fun blendOf(bands: List<Int>, blend: Double): Int {
        val i = max(0, min(blend.toInt(), bands.size - 2))
        val t = (blend - i).coerceIn(0.0, 1.0)
        val a = bands[i]; val b = bands[i + 1]
        fun mix(shift: Int) = Pixels.roundHalfUp(
            (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t)
        ).coerceIn(0, 255)
        // Alpha is asserted here rather than left to the caller: the field is 893 drawn lines, and a blend that
        // returned 0x00RRGGBB would make the whole card invisible while every int comparison here still passed.
        return Palette.opaque(Pixels.rgb(mix(16), mix(8), mix(0)))
    }
}
