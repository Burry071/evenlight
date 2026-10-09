package com.burry071.evenlight.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.burry071.evenlight.core.BandSampler
import com.burry071.evenlight.core.CardGeometry
import com.burry071.evenlight.core.Palette
import com.burry071.evenlight.core.Pixels
import com.burry071.evenlight.ui.theme.EvenlightColors
import com.burry071.evenlight.ui.theme.EvenlightType
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * The card as an object, in one place: its shape, its measured band field, its date words, and the detail screen
 * that shows the finished PNG. These helpers live here rather than in a sixth file because the brief's file list is
 * the file list, and they cannot live in `StackDays.kt`, which must stay free of `android.*` so the JVM test can
 * load it. Every screen that draws a card draws it through this file, so a card cannot look like three things.
 */

/**
 * The mockup's corner lock: "Compose `RoundedCornerShape` is circular, not continuous", and the 20 dp card radius
 * is a squircle. One `GenericShape`, not a library: a cubic per corner whose handles run past the circular
 * 0.5523 r, which is what flattens the entry into the straight edge. The mockup gives no number for the handle, so
 * 0.72 r is this file's choice, and only Step 10's device pass can judge it.
 *
 * ui 1.9.0's `GenericShape` builder is `Path.(Size, LayoutDirection) -> Unit` - it takes no `Density` any more, so
 * the dp-to-px crossing happens where a density exists: `LocalDensity`, then remembered. Caching on the px radius
 * means a density change rebuilds the shape instead of drawing last week's radius at today's scale.
 */
@Composable
fun rememberSquircle(radiusDp: Dp): Shape {
    val radiusPx = with(LocalDensity.current) { radiusDp.toPx() }
    return remember(radiusPx) { squircleShape(radiusPx) }
}

private fun squircleShape(radiusPx: Float): Shape = GenericShape { size, _ ->
    val r = radiusPx.coerceAtMost(minOf(size.width, size.height) / 2f)
    val k = 0.72f * r
    val w = size.width
    val h = size.height
    moveTo(r, 0f)
    lineTo(w - r, 0f)
    cubicTo(w - r + k, 0f, w, r - k, w, r)
    lineTo(w, h - r)
    cubicTo(w, h - r + k, w - r + k, h, w - r, h)
    lineTo(r, h)
    cubicTo(r - k, h, 0f, h - r + k, 0f, h - r)
    lineTo(0f, r)
    cubicTo(0f, r - k, r - k, 0f, r, 0f)
    close()
}

/** The card radius, 20 dp, from the mockup's shape lock ("cards 20"). */
@Composable
fun cardShape(): Shape = rememberSquircle(20.dp)

/** The chip radius, 12 dp ("chips 12"), same continuous corner. */
@Composable
fun chipShape(): Shape = rememberSquircle(12.dp)

/** Actions are pills: round at both ends, which a percentage shape gets right at any height. */
val PillShape = RoundedCornerShape(percent = 50)

/**
 * Month names as a table, for the same reason `CardHeader` and `Pixels.HEX_DIGITS` are: a locale-sensitive formatter
 * would put Urdu month names on a screen after a device locale change. Everything the user reads is ASCII words.
 */
private val MONTHS_LONG = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)
private val MONTHS_SHORT = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/** "9 October", the card's own header. */
fun longDate(date: LocalDate): String = "${date.dayOfMonth} ${MONTHS_LONG[date.monthValue - 1]}"

/** "8 Oct", the stack row and the preview card. */
fun shortDate(date: LocalDate): String = "${date.dayOfMonth} ${MONTHS_SHORT[date.monthValue - 1]}"

/** "17:40" from a `ZonedDateTime`, two digits by table rather than by a locale-sensitive pattern. */
fun clockText(hour: Int, minute: Int): String =
    (if (hour < 10) "0$hour" else "$hour") + ":" + (if (minute < 10) "0$minute" else "$minute")

/**
 * The eight measured bands, top to horizon, each drawn exactly as stored.
 *
 * `Palette.opaque` is the single crossing from the measurement domain into the drawing domain: a record's bands are
 * packed `0x00RRGGBB`, and Compose reads the high byte as alpha, so a band that reaches here without the crossing
 * paints nothing at all. No saturation, contrast, vibrance, tint, filter, preset or blend is applied - the only
 * permitted transformation anywhere in this project is `CardGeometry`'s linear blend between band centres, and that
 * happens in the renderer, not on screen.
 *
 * Returns null when a band will not parse, so a corrupt record renders as the hole it functionally is instead of
 * crashing the list mid-scroll.
 */
fun bandColors(hex: List<String>): List<Color>? {
    if (hex.size != BandSampler.BAND_COUNT) return null
    return hex.map { raw -> opaqueBand(raw)?.let { Color(it) } ?: return null }
}

/** One measured band, drawn: the record's `#RRGGBB` through the crossing, or null if it is not a colour. */
fun opaqueBand(hex: String): Int? =
    runCatching { Palette.opaque(Pixels.parseHex(hex)) }.getOrNull()

/**
 * The strip's text colour for a measured band, answered by `CardGeometry.inkOn` - the same rule the renderer uses.
 * A second opinion written here is how a screen and a card end up disagreeing about one band.
 */
fun stripInk(bandArgb: Int): Color = Color(CardGeometry().inkOn(bandArgb))

/** A card's band field: equal-height bands, `weight(1f)`, never stretched and never altered. */
@Composable
fun BandField(bands: List<Color>, modifier: Modifier = Modifier) {
    Column(modifier) {
        bands.forEach { c ->
            Box(Modifier.weight(1f).fillMaxWidth().background(c))
        }
    }
}

/**
 * The card, on arrival, and whenever the stack is opened onto it.
 *
 * Night theme: the detail screen is the night, and it stays night on a phone set to light mode (spec section 8).
 * The artefact itself is paper - that is the card that was drawn - so the two are deliberately different surfaces.
 *
 * `cardMaxWidth` is the design system's cap on how large a card may be drawn, not a layout convenience: the mockup
 * sizes the card as an object on a surface, so a wide window gets a centred 340 dp card (420 dp in the expanded
 * stack's detail pane) rather than a print the width of a tablet.
 */
@Composable
fun CardScreen(
    cardFile: File,
    hasRawPhoto: Boolean,
    onRawPhoto: () -> Unit,
    onShare: () -> Unit,
    cardMaxWidth: Dp = 340.dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .background(EvenlightColors.NightSurface)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp)
    ) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            CardDetail(
                cardFile = cardFile,
                hasRawPhoto = hasRawPhoto,
                onRawPhoto = onRawPhoto,
                onShare = onShare,
                // `widthIn` before `fillMaxWidth`: the cap has to narrow the incoming constraint, or the fill
                // takes the whole window and the card prints at tablet width regardless of the number.
                modifier = Modifier.widthIn(max = cardMaxWidth).fillMaxWidth()
            )
        }
    }
}

/**
 * The detail screen: the rendered PNG and nothing invented beside it. `CardRenderer` already drew the name strip,
 * the date, the sun, the neighbours and `measured, not adjusted · 8 bands` into the file, so restating them in
 * Compose would create a second copy of the card that can drift from the first.
 *
 * The PNG is decoded off the main thread: it is a 1080x1440 bitmap and this screen is reached by a tap the user
 * expects to be instant.
 *
 * `raw photo` only appears when the raw JPEG is actually readable. An action that silently does nothing is the
 * failure this screen's own caption promises against, and a missing raw file is normal: the app keeps the last
 * few shots and the OS is free to clear them.
 */
@Composable
fun CardDetail(
    cardFile: File,
    hasRawPhoto: Boolean,
    onRawPhoto: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<Bitmap?>(null, cardFile.path) {
        value = withContext(Dispatchers.IO) {
            if (cardFile.canRead()) BitmapFactory.decodeFile(cardFile.path) else null
        }
    }
    Column(modifier = modifier.fillMaxHeight()) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(26.dp, cardShape(), clip = false)
                    .clip(cardShape()),
                contentScale = ContentScale.FillWidth
            )
        } ?: Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(EvenlightColors.NightHairline)
        )
        // The actions belong to a card that exists. On a hole, or while a card PNG cannot be read, this screen has a
        // placeholder and a caption and nothing to press - which is what the caption promises, and why an
        // unpressable `share` must not be drawn.
        if (bitmap != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                // The 48 dp target is invisible padding, not a bigger label.
                if (hasRawPhoto) CardAction("raw photo", onClick = onRawPhoto)
                CardAction("share", onClick = onShare)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "filed automatically · nothing to press",
            style = EvenlightType.caption,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun CardAction(label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
    ) {
        Text(
            label,
            style = EvenlightType.body.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline)
        )
    }
}
