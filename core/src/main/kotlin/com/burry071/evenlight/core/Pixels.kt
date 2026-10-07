package com.burry071.evenlight.core

import kotlin.math.pow
import kotlin.math.roundToInt

/** Colours are packed as 0x00RRGGBB ints. No Android types anywhere in this package. */
object Pixels {
    fun r(c: Int): Int = (c shr 16) and 0xFF
    fun g(c: Int): Int = (c shr 8) and 0xFF
    fun b(c: Int): Int = c and 0xFF
    fun rgb(r: Int, g: Int, b: Int): Int = (r shl 16) or (g shl 8) or b

    fun hex(c: Int): String = "#%02X%02X%02X".format(r(c), g(c), b(c))

    fun parseHex(s: String): Int {
        val t = s.removePrefix("#")
        require(t.length == 6) { "not a hex colour: $s" }
        return (t.substring(0, 2).toInt(16) shl 16) or (t.substring(2, 4).toInt(16) shl 8) or t.substring(4, 6).toInt(16)
    }

    /** WCAG relative luminance, 0..1. Used only to pick a text colour - never to alter a colour. */
    fun relativeLuminance(c: Int): Double {
        fun lin(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin(r(c)) + 0.7152 * lin(g(c)) + 0.0722 * lin(b(c))
    }

    fun luma(c: Int): Int = (0.299 * r(c) + 0.587 * g(c) + 0.114 * b(c)).roundToInt()

    /** Round-half-up. Python mirrors this with floor(x + 0.5); do NOT use banker's rounding. */
    fun roundHalfUp(v: Double): Int = Math.floor(v + 0.5).toInt()
}
