package com.burry071.evenlight.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class Match(val winnerIndex: Int, val name: String, val neighbours: List<Int>)

object Matcher {
    fun chromaRange(c: Int): Int {
        val r = Pixels.r(c); val g = Pixels.g(c); val b = Pixels.b(c)
        return max(r, max(g, b)) - min(r, min(g, b))
    }

    /** Max chroma range; tie -> higher relative luminance; tie -> lowest index. Deterministic. */
    fun winner(bands: List<Int>): Int {
        require(bands.isNotEmpty())
        var best = 0
        var bestRange = chromaRange(bands[0])
        var bestLum = Pixels.relativeLuminance(bands[0])
        for (i in 1 until bands.size) {
            val range = chromaRange(bands[i])
            val lum = Pixels.relativeLuminance(bands[i])
            if (range > bestRange || (range == bestRange && lum > bestLum)) {
                best = i; bestRange = range; bestLum = lum
            }
        }
        return best
    }

    /** Euclidean RGB distance, unweighted. Ties keep the earlier vocabulary entry. */
    fun nameFor(c: Int): String {
        var word = Vocabulary.words.first().word
        var best = Double.MAX_VALUE
        for (w in Vocabulary.words) {
            val dr = (Pixels.r(c) - Pixels.r(w.anchor)).toDouble()
            val dg = (Pixels.g(c) - Pixels.g(w.anchor)).toDouble()
            val db = (Pixels.b(c) - Pixels.b(w.anchor)).toDouble()
            val d = sqrt(dr * dr + dg * dg + db * db)
            if (d < best) { best = d; word = w.word }
        }
        return word
    }

    fun match(bands: List<Int>): Match {
        val i = winner(bands)
        return Match(i, nameFor(bands[i]), listOfNotNull(bands.getOrNull(i - 1), bands.getOrNull(i + 1)))
    }
}
