package com.burry071.evenlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.burry071.evenlight.core.EveningRecord
import com.burry071.evenlight.core.GeoPoint
import com.burry071.evenlight.core.SunWindow
import com.burry071.evenlight.data.Settings
import com.burry071.evenlight.ui.theme.EvenlightColors
import com.burry071.evenlight.ui.theme.EvenlightType
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

/**
 * Tonight: one line and one number, the latest card, and a shutter that is never disabled.
 *
 * The countdown is recomputed by a `LaunchedEffect` that sleeps 30 s, not by a service - the app has no background
 * component and the number only matters while the screen is open. The pill is enabled at any hour: the app says when
 * the light is interesting, never whether to act.
 */
@Composable
fun TonightScreen(
    settings: Settings,
    records: List<EveningRecord>,
    held: Set<String>,
    onShoot: () -> Unit,
    onStack: () -> Unit,
    onSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()

    // One tick per 30 s. `now` is read inside the remember so a recomposition caused by anything else cannot move
    // the number without the timer having actually run.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick++
        }
    }

    val geo = GeoPoint(settings.lat, settings.lon)
    val moment: Pair<ZonedDateTime, ZonedDateTime> = remember(tick, settings) {
        val n = ZonedDateTime.now(zone)
        n to SunWindow.sunset(n.toLocalDate(), geo, zone)
    }
    val (now, sunset) = moment
    val minutesToSunset = Duration.between(now, sunset).toMinutes()

    // "evening 4" is tonight's ordinal in the record, so it counts distinct evenings strictly before today.
    // ISO dates sort lexicographically, which is why a string comparison here is a date comparison.
    // Remembered on `records` rather than recomputed: the 30 s tick below recomposes this whole body, and a
    // derived value that does not depend on `tick` must not be re-derived by something that does.
    val isoToday = today.toString()
    val eveningNumber = remember(records, isoToday) {
        records.map { it.date }.distinct().count { it < isoToday } + 1
    }

    val latest = remember(records, held) { records.lastOrNull { it.date in held } }
    val bands = remember(latest) { latest?.let { bandColors(it.bandsHex) } }
    val largeFont = LocalDensity.current.fontScale > 1.15f

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(settings.place.ifBlank { "evenlight" }, style = EvenlightType.caption, color = EvenlightColors.Muted)

        // The mockup sets the number itself in a softer instance of the display face (`in <em>14</em> min`), so the
        // digits carry their own variation string while the words around them stay on EvenlightType.hero.
        Box(Modifier.padding(top = 12.dp)) {
            Text(
                buildAnnotatedString {
                    if (minutesToSunset >= 0) {
                        append("in ")
                        withStyle(SpanStyle(fontFeatureSettings = "'opsz' 72, 'SOFT' 90, 'WONK' 1")) {
                            append(minutesToSunset.toString())
                        }
                        append(" min")
                    } else {
                        // Past sunset the hero keeps the Shoot chip's signed vocabulary rather than inventing a
                        // second way to say the same number: "+14 min" is 14 min after 17:40 on both screens.
                        append("+${-minutesToSunset} min")
                    }
                },
                style = if (largeFont) EvenlightType.hero.copy(fontSize = 34.sp) else EvenlightType.hero,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.93f)
            )
        }

        Text(
            "sunset ${clockText(sunset.hour, sunset.minute)} · evening $eveningNumber",
            style = EvenlightType.caption,
            color = EvenlightColors.Muted,
            modifier = Modifier.padding(top = 9.dp)
        )

        latest?.let { record ->
            // The preview is centred by the box's own alignment: `BoxWithConstraintsScope` in ui 1.9.0 offers no
            // `Modifier.align(Alignment.Horizontal)`, because it is a `Box` scope, not a `RowColumnScope`.
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // Compact 186 dp, medium 240 dp ("Tonight keeps one column with the preview card at 240 dp"), and
                // the same centred column at expanded width: the preview is one object and it never stretches.
                val previewWidth = if (maxWidth >= 600.dp) 240.dp else 186.dp
                PreviewCard(
                    record = record,
                    bands = bands ?: return@BoxWithConstraints,
                    width = previewWidth,
                    rotated = !largeFont,
                    modifier = Modifier.padding(top = 26.dp)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        ShootPill(label = "Shoot", onClick = onShoot)

        // Four destinations and a back gesture, no bottom bar at any width (ledger R2). The two quiet actions sit
        // under the pill rather than in a chrome the mockup does not have.
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally)
        ) {
            GhostAction("Stack", onClick = onStack)
            GhostAction("Setup", onClick = onSetup)
        }
    }
}

/** The card, small: the band field the record measured and the two words the record stores about it. */
@Composable
private fun PreviewCard(
    record: EveningRecord,
    bands: List<Color>,
    width: Dp,
    rotated: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .width(width)
            .graphicsLayer {
                if (rotated) rotationZ = 1.2f
            }
            .clip(cardShape())
            .background(MaterialTheme.colorScheme.surface)
    ) {
        BandField(bands, Modifier.fillMaxWidth().height(width * 0.634f))
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Text(
                record.name,
                style = EvenlightType.title.copy(fontSize = 19.sp, letterSpacing = (-0.018).em),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${shortDate(LocalDate.parse(record.date))} · sun ${record.sunAltitudeDeg}°",
                style = EvenlightType.caption.copy(fontSize = 10.5.sp, letterSpacing = 0.em),
                color = EvenlightColors.Muted,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

/**
 * The shutter: 52 dp, pill, ink on paper, and enabled whatever hour it is.
 *
 * The label is a parameter because Tonight's pill and Setup's submit are the same object in the mockup's shape
 * language, and one pill that says `Shoot` in both places would put the app's loudest action on a form. UI copy is
 * action names only; `Save` is the action Setup performs.
 */
@Composable
fun ShootPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(PillShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = EvenlightType.body.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun GhostAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = EvenlightType.body.copy(fontSize = 13.sp), color = EvenlightColors.Muted)
    }
}
