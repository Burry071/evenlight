package com.burry071.evenlight.core

import kotlin.math.pow

/** Colours are packed as 0x00RRGGBB ints. No Android types anywhere in this package. */
object Pixels {
    /**
     * The only digits [hex] may emit. An explicit table rather than a format string, because this
     * output is persisted verbatim into the evening record (T8) and diffed against Python's ASCII
     * `%02X` (T10): `"%02X...".format(...)` resolves against Locale.getDefault(), and this JVM's
     * Formatter does substitute locale digits for default-locale conversions (under ar-EG-u-nu-arab,
     * String.format("%d", 30) is U+0663 U+0660, not "30"). Table-driven means no Formatter, no locale,
     * no platform-version dependence for a string that is stored and compared across languages.
     */
    private const val HEX_DIGITS = "0123456789ABCDEF"

    fun r(c: Int): Int = (c shr 16) and 0xFF
    fun g(c: Int): Int = (c shr 8) and 0xFF
    fun b(c: Int): Int = c and 0xFF
    fun rgb(r: Int, g: Int, b: Int): Int = (r shl 16) or (g shl 8) or b

    /** `#RRGGBB`, uppercase ASCII, 7 chars, identical under every locale - see [HEX_DIGITS] for why that matters. */
    fun hex(c: Int): String {
        val out = StringBuilder(7)
        out.append('#')
        for (shift in 16 downTo 0 step 8) {
            val channel = (c shr shift) and 0xFF
            out.append(HEX_DIGITS[channel ushr 4]).append(HEX_DIGITS[channel and 0x0F])
        }
        return out.toString()
    }

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

    /** Round-half-up. Python mirrors this with floor(x + 0.5); do NOT use banker's rounding. */
    fun roundHalfUp(v: Double): Int = Math.floor(v + 0.5).toInt()
}
