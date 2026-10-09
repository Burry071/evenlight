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
        for (n in listOf("sky_ramp", "sky_blocks", "sky_wide", "sky_narrow")) {
            val img = ImageIO.read(File(dir, "$n.png"))
            val px = IntArray(img.width * img.height)
            img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
            val bands = BandSampler.sample(px, img.width, img.height).ordered.map { Pixels.hex(it) }
            val file = File(dir, "$n.bands.json")
            if (write) {
                file.writeText(
                    "{\n  \"width\": ${img.width},\n  \"height\": ${img.height},\n  \"bands\": " +
                        bands.joinToString(", ", "[", "]") { "\"$it\"" } + "\n}\n"
                )
            }
            val expected = Regex("\"bands\": \\[([^]]*)]").find(file.readText())!!.groupValues[1]
                .split(",").map { it.trim().trim('"') }
            assertEquals(n, expected, bands)
        }
    }
}
