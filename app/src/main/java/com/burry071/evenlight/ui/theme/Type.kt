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
 * INTERNET permission, so Google Fonts' downloadable-font provider could not fetch a single glyph.
 *
 * Fraunces needs three families, not one, and the reason is in the file's own tables. `fraunces_variable.ttf` ships
 * `fvar wght min=100 default=900 max=900`, `opsz 9–144 default 9`, name 4 "Fraunces 9pt Black", OS/2
 * usWeightClass=900: whatever you ask for, the default instance is the Black cut at the smallest optical size. The
 * design asks for weight 400 and, role by role, `opsz` 72 / 48 / 40 (mockups:58, :111, :125), which the opsz axis
 * draws as different letterforms rather than a scaled copy. So each size band gets a `<font-family>` XML that pins
 * the axes, and the styles below point at the band they belong to.
 *
 * That route, and not the axis strings this file used to carry in `fontFeatureSettings`, because Compose ui-text
 * 1.9.0 has no `fontVariationSettings` on `SpanStyle` or `TextStyle` (verified against the artifact's class files:
 * 0 occurrences of the field name, where `fontFeatureSettings` has 2), and `wght` is a variation axis, not an
 * OpenType feature tag, so a feature string can never reach it. The framework reads `app:fontVariationSettings` on
 * `FontFamilyFont` (android-35 attrs.xml) at load time; `ResourcesCompat.getFont` - which is what Compose's
 * `Font(resId)` and `EvenlightController.forDevice` both go through - accepts a family XML, so the same three files
 * serve the screens and the card PNG. Ledger R5, which called the axis strings a device-review claim, is closed by
 * the screenshots taken in this step: on the previous build they were inert, and the serif rendered Black.
 */
private val FrauncesDisplay = FontFamily(Font(R.font.fraunces_display))
private val FrauncesTitle = FontFamily(Font(R.font.fraunces_title))
private val FrauncesStrip = FontFamily(Font(R.font.fraunces_strip))
private val Body = FontFamily(Font(R.font.instrument_sans_variable))
private val BodyStrong = FontFamily(Font(R.font.instrument_sans_medium))

/**
 * The design's `font-variant-numeric: tabular-nums` on every numeric sans line (mockups:61, :79, :93, :114, :126,
 * :134, :137). Unlike `wght`/`opsz`, `tnum` is a real OpenType feature tag, so `fontFeatureSettings` is the correct
 * and only mechanism for it — and the one the variable axes can never use.
 *
 * `GSUB` in `instrument_sans_variable.ttf` lists `tnum`, so this does something. `fraunces_variable.ttf` does not
 * (no `tnum`, no `lnum`), which is why the serif `hero` and `strip` set nothing: their digits are proportional by
 * design, and a feature string the font lacks is a silent no-op rather than an error.
 */
private const val TNUM = "tnum"

/**
 * Every `Text` in every screen carries an explicit `style` from this object - that is the load-bearing half of the
 * rule. `MaterialTheme.typography` is Roboto, so one `Text("…")` without a style renders in the wrong family at
 * the wrong size and nothing fails.
 *
 * ui-text 1.9.0 removed the flat `TextStyle(fontFamily = …, lineHeight = …)` constructor (it is now internal, built
 * from `SpanStyle` + `ParagraphStyle`), so each style below is assembled with `merge` on `TextStyle.Default`.
 */
object EvenlightType {
    val hero = TextStyle.Default
        .merge(
            SpanStyle(
                fontFamily = FrauncesDisplay, fontWeight = FontWeight.Normal, fontSize = 44.sp,
                letterSpacing = (-0.028).em
            )
        )
        .merge(ParagraphStyle(lineHeight = 46.sp))

    val title = TextStyle.Default
        .merge(
            SpanStyle(
                fontFamily = FrauncesTitle, fontWeight = FontWeight.Normal, fontSize = 24.sp
            )
        )

    /** The winner strip carries display type only: paper-on-band clears WCAG 3:1 for large text, not 4.5:1 for body. */
    val strip = TextStyle.Default
        .merge(
            SpanStyle(
                fontFamily = FrauncesStrip, fontWeight = FontWeight.Normal, fontSize = 29.sp
            )
        )

    val body = TextStyle.Default
        .merge(SpanStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 15.sp))
        .merge(ParagraphStyle(lineHeight = 22.sp))

    /** The only two places the design asks for 500 are the buttons (mockups:65, :101), and they use this, not `body.copy(fontWeight = Medium)`. */
    val bodyStrong = TextStyle.Default
        .merge(SpanStyle(fontFamily = BodyStrong, fontWeight = FontWeight.Medium, fontSize = 15.sp))
        .merge(ParagraphStyle(lineHeight = 22.sp))

    /**
     * `.eyebrow` (mockups:55): the uppercase label above a heading. Callers uppercase the text themselves. No
     * tabular figures because `.eyebrow` never carries a number.
     */
    val eyebrow = TextStyle.Default
        .merge(SpanStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 11.sp, letterSpacing = 0.11.em))

    /** `.sub` (mockups:61): the line under a heading. `tabular-nums` in the design, which is what `sub` sets below. */
    val sub = TextStyle.Default
        .merge(SpanStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 13.sp, fontFeatureSettings = TNUM))
        .merge(ParagraphStyle(lineHeight = 20.sp))

    /**
     * `.meta` and its siblings (mockups:113, :126, :134, :137): date, time and hex lines. The design declares none of
     * them bold or medium — all `font-weight` requests in the mockup's CSS are the two buttons and `.status`, which is
     * the system bar, not our text — and `instrument_sans_variable.ttf` measures `wght min=400 default=400 max=700`,
     * so a `FontWeight.Medium` on the un-pinned family asked for an instance the loader never instantiates: it drew 400
     * while claiming 500. Normal is what the design asks for and what has always rendered.
     */
    val caption = TextStyle.Default
        .merge(
            SpanStyle(
                fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 11.sp,
                letterSpacing = 0.09.em, fontFeatureSettings = TNUM
            )
        )
}
