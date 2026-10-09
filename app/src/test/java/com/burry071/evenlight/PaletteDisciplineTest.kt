package com.burry071.evenlight

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Chrome is paper and ink only - zero brand hue" is a rule a compiler cannot see, so it rots silently unless
 * something reads the source. Same off-disk style as [ManifestTest]: the subject is read from the working tree at
 * test time, and the module directory is the test JVM's working directory, so the relative path below resolves to
 * `app/src/main/java/...` on every host that runs `:app:testDebugUnitTest`.
 *
 * The two framing-guide strokes are `Color.White` and `Color.Black.copy(alpha = ...)` - achromatic, over the
 * camera rather than on the paper (ledger R6) - so they clear this regex, which is looking for a hex literal, not
 * for a non-Palette colour.
 */
class PaletteDisciplineTest {
    private val uiDir = File("src/main/java/com/burry071/evenlight/ui")

    @Test fun everyChromeColourComesFromPalette() {
        val offenders = uiDir.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { f -> Regex("Color\\(\\s*0[xX]").findAll(f.readText()).map { "${f.name}: $it" } }
            .toList()
        assertTrue("hex passed straight to Color(): $offenders", offenders.isEmpty())
    }

    @Test fun uiDirectoryExists() {
        // Fails loudly if the package path moves, because an empty walk would make the rule above vacuously true.
        assertTrue("no such directory: ${uiDir.path}", uiDir.isDirectory)
    }
}
