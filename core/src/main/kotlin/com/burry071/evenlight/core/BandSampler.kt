package com.burry071.evenlight.core

import kotlin.math.max
import kotlin.math.min

data class Bands(val ordered: List<Int>, val meanLuminance: Double)

/** Frame -> eight ordered band colours, index 0 at the top of the frame. */
object BandSampler {
    const val TARGET_WIDTH = 64
    const val BAND_COUNT = 8

    fun sample(pixels: IntArray, width: Int, height: Int): Bands {
        // Before the size check: 0 == 0 * 0 satisfies it, and a zero height would then reach
        // coerceIn(0.0, (sh - 1).toDouble()) = coerceIn(0.0, -1.0) inside the interpolator, which
        // throws a stdlib "empty range" error instead of naming the bad argument.
        require(width > 0 && height > 0) { "width and height must both be positive, got ${width}x${height}" }
        require(pixels.size == width * height) { "expected ${width * height} pixels, got ${pixels.size}" }
        val dw = TARGET_WIDTH
        // The min(height, ...) clamp is load-bearing: width < TARGET_WIDTH makes height * dw / width
        // > height, so dh grows past the row count and the bilinear pass INVENTS interpolated rows
        // instead of repeating the nearest source row (golden: fewerRowsThanBands...). It is the
        // identity for every width >= TARGET_WIDTH, so the downscale path Python mirrors in
        // tools/python/skycard.py (T10) is bit-for-bit unchanged.
        val dh = max(1, min(height, Pixels.roundHalfUp(height.toDouble() * dw / width)))
        val small = bilinear(pixels, width, height, dw, dh)

        var lumSum = 0.0
        val bands = ArrayList<Int>(BAND_COUNT)
        for (i in 0 until BAND_COUNT) {
            val y0 = i * dh / BAND_COUNT
            val y1 = (i + 1) * dh / BAND_COUNT
            val from = min(y0, dh - 1)
            val to = min(max(y1, y0 + 1) - 1, dh - 1)
            var rs = 0L; var gs = 0L; var bs = 0L; var n = 0
            for (y in from..to) {
                val row = y * dw
                for (x in 0 until dw) {
                    val c = small[row + x]
                    rs += Pixels.r(c); gs += Pixels.g(c); bs += Pixels.b(c); n++
                }
            }
            val band = Pixels.rgb(
                Pixels.roundHalfUp(rs.toDouble() / n),
                Pixels.roundHalfUp(gs.toDouble() / n),
                Pixels.roundHalfUp(bs.toDouble() / n)
            )
            bands += band
            lumSum += Pixels.relativeLuminance(band)
        }
        return Bands(bands, lumSum / BAND_COUNT)
    }

    /** Half-pixel-centre bilinear, per-channel round-half-up. Python mirrors this exactly. */
    private fun bilinear(src: IntArray, sw: Int, sh: Int, dw: Int, dh: Int): IntArray {
        if (sw == dw && sh == dh) return src.copyOf()
        val out = IntArray(dw * dh)
        val scaleX = sw.toDouble() / dw
        val scaleY = sh.toDouble() / dh
        for (dy in 0 until dh) {
            val fy = ((dy + 0.5) * scaleY - 0.5).coerceIn(0.0, (sh - 1).toDouble())
            val y0 = fy.toInt(); val y1 = min(y0 + 1, sh - 1); val wy = fy - y0
            for (dx in 0 until dw) {
                val fx = ((dx + 0.5) * scaleX - 0.5).coerceIn(0.0, (sw - 1).toDouble())
                val x0 = fx.toInt(); val x1 = min(x0 + 1, sw - 1); val wx = fx - x0
                val a = src[y0 * sw + x0]; val b = src[y0 * sw + x1]
                val c = src[y1 * sw + x0]; val d = src[y1 * sw + x1]
                val w00 = (1 - wx) * (1 - wy); val w01 = wx * (1 - wy)
                val w10 = (1 - wx) * wy; val w11 = wx * wy
                fun mix(shift: Int): Int = Pixels.roundHalfUp(
                    ((a shr shift) and 0xFF) * w00 + ((b shr shift) and 0xFF) * w01 +
                        ((c shr shift) and 0xFF) * w10 + ((d shr shift) and 0xFF) * w11
                ).coerceIn(0, 255)
                out[dy * dw + dx] = Pixels.rgb(mix(16), mix(8), mix(0))
            }
        }
        return out
    }
}
