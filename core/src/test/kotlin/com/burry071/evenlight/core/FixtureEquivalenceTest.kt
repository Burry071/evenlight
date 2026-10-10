package com.burry071.evenlight.core

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Kotlin and Python must agree on the same pixels. Both assert against these files, so
 * agreement is transitive. PNG fixtures: a JPEG decoder difference would make this test lie.
 */
class FixtureEquivalenceTest {
    // The plan's `File("../../fixtures")` does not resolve: the :core test JVM's working directory is the
    // :core module directory (measured, see task-10 report), so two levels up is a sibling of the repo. In
    // write mode that would not merely fail - it would create goldens outside the repository. Gradle passes
    // the absolute path instead, which is the same reason :app pins its manifest input by layout rather than
    // by cwd (app/build.gradle.kts).
    private val dir = File(
        requireNotNull(System.getProperty("evenlight.fixturesDir")) {
            "evenlight.fixturesDir is unset; run this test through :core:test, which points it at <repo>/fixtures"
        }
    )
    private val write = System.getProperty("evenlight.writeFixtures") == "true"

    @Test fun fixturesProduceThePinnedBands() {
        for (n in listOf("sky_ramp", "sky_blocks", "sky_wide", "sky_narrow", "sky_columns")) {
            val img = ImageIO.read(File(dir, "$n.png"))
            val px = IntArray(img.width * img.height)
            img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
            val bands = BandSampler.sample(px, img.width, img.height).ordered.map { Pixels.hex(it) }
            val file = File(dir, "$n.bands.json")
            val text = goldenText(img.width, img.height, bands)
            if (write) {
                // Finding 2.8: write mode used to assert nothing and overwrite the goldens whatever it found -
                // the one file whose whole purpose is to be a pinned contract. Regenerating an unchanged fixture
                // stays a no-op, but a golden that disagrees with the sampler now fails the run instead of being
                // quietly repainted; a deliberate change means reading the diff and deleting the file first.
                val previous = if (file.isFile) file.readText() else null
                if (previous != null && previous != text) {
                    throw IllegalStateException(
                        "write mode refuses to overwrite $n: the pinned golden disagrees with the regenerated " +
                            "bands. Compare the two by hand; if the change is intended, delete the golden and " +
                            "write again."
                    )
                }
                file.writeText(text)
            }
            val pinned = file.readText()
            val expected = Regex("\"bands\": \\[([^]]*)]").find(pinned)!!.groupValues[1]
                .split(",").map { it.trim().trim('"') }
            assertEquals(n, expected, bands)
            // Finding 2.8, second half: the declared size used to be decoration read by nobody. It is now an
            // assertion - a golden whose width/height disagree with the PNG it claims to pin is a corrupt pair.
            val size = Regex("\"width\": (\\d+),\\s*\"height\": (\\d+)").find(pinned)
            if (size == null) throw IllegalStateException("golden $n declares no width/height")
            assertEquals(n, size.groupValues[1].toInt(), img.width)
            assertEquals(n, size.groupValues[2].toInt(), img.height)
        }
    }

    private fun goldenText(width: Int, height: Int, bands: List<String>): String =
        "{\n  \"width\": $width,\n  \"height\": $height,\n  \"bands\": " +
            bands.joinToString(", ", "[", "]") { "\"$it\"" } + "\n}\n"
}
