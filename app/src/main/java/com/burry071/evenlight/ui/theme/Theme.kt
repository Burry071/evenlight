package com.burry071.evenlight.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Exists so that `MaterialTheme` cannot supply its own defaults: the scheme is built from [EvenlightColors] only.
 *
 * `dark` is a parameter rather than `isSystemInDarkTheme()` because the stack is dark for the same reason the sky
 * is - it is the night - and it stays dark on a phone set to light mode. Which destinations get it is decided in
 * `EvenlightContent`, from the one `class="phone night"` frame in `design/evenlight-mockups.html`; spec section 8
 * says nothing about a dark card, and the mockup's card frame is paper.
 *
 * `Palette.HAIRLINE` is consumed here for the first time; it has no other user in the app, and an unused palette
 * constant is a colour the design claims and the chrome never shows.
 */
@Composable
fun EvenlightTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) {
        darkColorScheme(
            primary = EvenlightColors.NightText, onPrimary = EvenlightColors.NightSurface,
            surface = EvenlightColors.NightSurface, onSurface = EvenlightColors.NightText,
            background = EvenlightColors.NightSurface, onBackground = EvenlightColors.NightText,
            outline = EvenlightColors.NightHairline
        )
    } else {
        lightColorScheme(
            primary = EvenlightColors.Ink, onPrimary = EvenlightColors.Paper,
            surface = EvenlightColors.Card, onSurface = EvenlightColors.Ink,
            background = EvenlightColors.Paper, onBackground = EvenlightColors.Ink,
            outline = EvenlightColors.Hairline
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
