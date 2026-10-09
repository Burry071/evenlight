package com.burry071.evenlight.ui.theme

import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.burry071.evenlight.R

/**
 * The bundled variable TTFs (Task 9 Step 1, commit `36af0ad`), not downloadable fonts: the manifest carries no
 * INTERNET permission, so Google Fonts' downloadable-font provider could not fetch a single glyph. Whether Compose
 * applies the `opsz`/`SOFT`/`WONK` axes is a device-review claim (ledger R5): nothing in `:app:testDebugUnitTest`
 * renders text, and the fallback if a device ignores them is the font's default instance, not a different family.
 *
 * Two forced departures from the brief's Step 2 snippet, both in form only - same families, weights, sizes,
 * tracking, leading and variation strings:
 *  1. ui-text 1.9.0 removed the flat `TextStyle(fontFamily = …, lineHeight = …)` constructor (it is now internal,
 *     built from `SpanStyle` + `ParagraphStyle`), so the styles are assembled with `merge` on `TextStyle.Default`.
 *  2. ui-text 1.9.0 has no `fontVariationSettings` on `SpanStyle`, on `TextStyle.copy`, or on material3's `Text`
 *     (verified by compiling against the artifact), so the axis strings ride in `fontFeatureSettings`, which
 *     Android's text layout hands to minikin; minikin parses `'SOFT'`/`'WONK'` (upper-case tags) as variation
 *     axes and `'opsz'` as the optical-size feature. That path is legal from API 26 and minSdk is 27, but only a
 *     screen can confirm the glyph shapes actually change.
 */
private val Display = FontFamily(Font(R.font.fraunces_variable))
private val Body = FontFamily(Font(R.font.instrument_sans_variable))

/**
 * Every `Text` in every screen carries an explicit `style` from this object - that is the load-bearing half of the
 * rule. `MaterialTheme.typography` is Roboto, so one `Text("…")` without a style renders in the wrong family at
 * the wrong size and nothing fails.
 */
object EvenlightType {
    val hero = TextStyle.Default
        .merge(
            SpanStyle(
                fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 44.sp,
                letterSpacing = (-0.028).em, fontFeatureSettings = "'opsz' 72, 'SOFT' 60, 'WONK' 1"
            )
        )
        .merge(ParagraphStyle(lineHeight = 46.sp))

    val title = TextStyle.Default
        .merge(
            SpanStyle(
                fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 24.sp,
                fontFeatureSettings = "'opsz' 40, 'SOFT' 60, 'WONK' 1"
            )
        )

    /** The winner strip carries display type only: paper-on-band clears WCAG 3:1 for large text, not 4.5:1 for body. */
    val strip = TextStyle.Default
        .merge(
            SpanStyle(
                fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 29.sp,
                fontFeatureSettings = "'opsz' 48, 'SOFT' 70, 'WONK' 1"
            )
        )

    val body = TextStyle.Default
        .merge(SpanStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 15.sp))
        .merge(ParagraphStyle(lineHeight = 22.sp))

    val caption = TextStyle.Default
        .merge(
            SpanStyle(
                fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 11.sp,
                letterSpacing = 0.09.em
            )
        )
}
