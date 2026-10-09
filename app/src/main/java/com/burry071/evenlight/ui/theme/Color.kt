package com.burry071.evenlight.ui.theme

import androidx.compose.ui.graphics.Color
import com.burry071.evenlight.core.Palette

/**
 * Chrome colours are the eight `Palette` constants and nothing else: a hex literal passed straight to `Color(…)`
 * anywhere under `ui/` is a brand hue, and `PaletteDisciplineTest` reads this directory off the disk looking for
 * exactly that - which is why this comment does not spell the pattern out.
 * The measured bands are NOT here - they come from a record and reach Compose through [Palette.opaque], because
 * a band packed as a zero-high-byte integer paints nothing when that byte is read as alpha.
 */
object EvenlightColors {
    val Paper = Color(Palette.PAPER)
    val Card = Color(Palette.CARD)
    val Ink = Color(Palette.INK)
    val Muted = Color(Palette.MUTED)
    val Hairline = Color(Palette.HAIRLINE)
    val NightSurface = Color(Palette.NIGHT_SURFACE)
    val NightText = Color(Palette.NIGHT_TEXT)
    val NightHairline = Color(Palette.NIGHT_HAIRLINE)
}
