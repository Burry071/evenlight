package com.burry071.evenlight.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.burry071.evenlight.EvenlightController
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
import kotlinx.coroutines.launch

/**
 * The viewfinder and the one delight.
 *
 * The camera binding is Task 8's, not new: the `PreviewView` arrives through `AndroidView` and the caller binds
 * `ProcessCameraProvider` to it (`MainActivity.bindCamera`), because camera-lifecycle 1.3.4 has no `awaitInstance`
 * and no dependency may be fetched. The permission prompt stays `ActivityResultContracts.RequestPermission`, the
 * app's only runtime permission.
 *
 * Nothing samples the frame while it moves: no reference image, no per-frame work. The only pixels this screen ever
 * reads are the ones the shutter already captured.
 */
@Composable
fun ShootScreen(
    settings: Settings,
    controller: EvenlightController,
    hasCamera: Boolean,
    onEnsureCamera: () -> Unit,
    onPreviewReady: (PreviewView) -> Unit,
    onFiled: (EveningRecord) -> Unit,
    reloadData: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var shot by remember { mutableStateOf<EveningRecord?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pressScale = remember { mutableFloatStateOf(1f) }
    val interaction = remember { MutableInteractionSource() }

    LaunchedEffect(hasCamera) { if (!hasCamera) onEnsureCamera() }

    // Press-in is the haptic moment, on the same frame as the visual: the interaction channel reports the press
    // before the click resolves, so the scale and the tick cannot disagree about when the action started.
    LaunchedEffect(interaction) {
        interaction.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> {
                    pressScale.floatValue = 0.97f
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> pressScale.floatValue = 1f
                else -> Unit
            }
        }
    }

    val zone = ZoneId.systemDefault()
    val now = ZonedDateTime.now(zone)
    val sunset = SunWindow.sunset(now.toLocalDate(), GeoPoint(settings.lat, settings.lon), zone)
    val delta = Duration.between(sunset, now).toMinutes()

    Box(modifier = modifier.fillMaxSize().background(EvenlightColors.NightSurface)) {
        // Full bleed, under the bars: the preview is the only surface in the app that runs beneath the status bar,
        // so the screen root takes no insets and the overlays inset themselves (the chip below, the shutter above).
        AndroidView(
            factory = { context ->
                PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                    .also { onPreviewReady(it) }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (shot == null) FramingGuide(Modifier.fillMaxSize())

        Text(
            "sunset ${clockText(sunset.hour, sunset.minute)} · ${signedMinutes(delta)}",
            style = EvenlightType.caption.copy(letterSpacing = 0.05.em),
            color = EvenlightColors.Paper,
            modifier = Modifier
                .align(Alignment.TopStart)
                // The preview runs under the status bar (edge-to-edge at targetSdk 35), and so did this chip: on the
                // device the clock and battery icons sat on top of "sunset 17:40".
                .statusBarsPadding()
                .padding(start = 16.dp, top = 14.dp)
                .clip(chipShape())
                .background(EvenlightColors.NightSurface.copy(alpha = 0.5f))
                .padding(horizontal = 11.dp, vertical = 5.dp)
        )

        failure?.let { message ->
            // Task 8's stopgap wording, kept: the brief gives no failure copy, and a legible reason without logcat
            // was the whole point of that step.
            Text(
                "failed: $message",
                style = EvenlightType.caption,
                color = EvenlightColors.Paper,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 86.dp)
                    .clip(chipShape())
                    .background(EvenlightColors.NightSurface.copy(alpha = 0.5f))
                    .padding(horizontal = 11.dp, vertical = 5.dp)
            )
        }

        val revealRecord = shot
        if (revealRecord != null) {
            RevealCard(
                record = revealRecord,
                reduceMotion = reduceMotion,
                onCardFiled = {
                    // The card is on disk by the time `shoot()` returns, so this is the frame the success tick is
                    // true about. CONFIRM is API 30: below it the tick is skipped rather than replaced by a
                    // different constant's feel.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                },
                onDone = { onFiled(revealRecord) },
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    // The preview stays full bleed; the control on top of it does not, or the gesture bar covers it.
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp)
                    .graphicsLayer {
                        scaleX = pressScale.floatValue
                        scaleY = pressScale.floatValue
                    }
                    // The design's shutter hugs its label (`.shutter div`: padding 0 40px, height 52px,
                    // box-shadow 0 6px 18px -8px) and is centred by the wrapper, not stretched across it. Compose's
                    // shadow takes an elevation only - no offset, no spread - so 8dp stands in for that one blurred
                    // offset layer; the press scale lives on the wrapper so the pill and its shadow shrink together.
                    .shadow(8.dp, PillShape, clip = false)
                    .heightIn(min = 52.dp)
                    .clip(PillShape)
                    .background(EvenlightColors.Card)
                    .padding(horizontal = 40.dp)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = !busy,
                        onClick = {
                            busy = true
                            failure = null
                            // CancellationException is not caught here and not folded: `fileShot()` rethrows it,
                            // and a destroyed window must not be asked to draw its own cancellation as a failure.
                            scope.launch {
                                controller.fileShot()
                                    .onSuccess { filed ->
                                        // Spec 8, un-inverted (finding 1.4): the card the shutter just filed is
                                        // revealed as soon as it is filed. The model's line is an upgrade that
                                        // lands afterwards - with a model installed `shoot()` used to hold the
                                        // reveal behind ~4.5 s of init plus ~1.2 s per line, for a card that was
                                        // on disk in the first few hundred milliseconds.
                                        shot = filed
                                        scope.launch {
                                            // Detached and best-effort: upgradeWording rethrows only
                                            // cancellation. When it finishes, the record and the card on disk
                                            // are newer than the activity's snapshot, so the reload is what
                                            // swaps the revealed card to the upgraded PNG. `shot` is
                                            // deliberately NOT reassigned: the reveal animation must not
                                            // restart when the wording arrives, and the filmstrip never drew
                                            // the line anyway - the mockup keeps it static for exactly this
                                            // reason.
                                            controller.upgradeWording(filed)
                                            reloadData()
                                        }
                                    }
                                    .onFailure { failure = it.message ?: "capture failed" }
                                busy = false
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Shoot",
                    style = EvenlightType.bodyStrong,
                    color = EvenlightColors.Ink
                )
            }
        }
    }
}

/** "+6 min" / "-14 min" from sunset - the chip's signed vocabulary, which Tonight's hero reuses after sunset. */
fun signedMinutes(minutesFromSunset: Long): String =
    "${if (minutesFromSunset < 0) "-" else "+"}${if (minutesFromSunset < 0) -minutesFromSunset else minutesFromSunset} min"

/**
 * The filmstrip from the mockup, drawn with the frame the shutter just produced: the field wipes down behind a clip,
 * the strip lands at 360 ms, the name fades in over 120 ms from 330 ms, and it is all inside 450 ms. Reduce Motion
 * collapses every part of it into one 120 ms cross-fade.
 *
 * The reveal is a `graphicsLayer` clip whose rectangle's height is the animated fraction. Animating the box's own
 * height instead would put the card's layout on the animation curve, which is the thing the plan forbids: reveal by
 * clip height, never by animating height.
 */
@Composable
private fun RevealCard(
    record: EveningRecord,
    reduceMotion: Boolean,
    onCardFiled: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bands = bandColors(record.bandsHex)
    val winner = record.bandsHex.getOrNull(record.winnerIndex)?.let { opaqueBand(it) }
    // Named `wipe`, not `clip`: inside `graphicsLayer { clip = true }` the property is the layer's own, and a local
    // val of the same name shadows it into "cannot reassign a val".
    val wipe = remember { Animatable(0f) }
    val strip = remember { Animatable(0f) }
    val name = remember { Animatable(0f) }
    val entry = remember { Animatable(0f) }
    val settled = reduceMotion || bands == null || winner == null

    LaunchedEffect(record) {
        onCardFiled()
        if (settled) {
            entry.animateTo(1f, tween(120, easing = LinearEasing))
        } else {
            launch { entry.animateTo(1f, tween(120, easing = LinearEasing)) }
            launch { wipe.animateTo(1f, spring(stiffness = 220f, dampingRatio = 1f)) }
            launch { strip.animateTo(1f, tween(90, delayMillis = 270, easing = LinearEasing)) }
            name.animateTo(1f, tween(120, delayMillis = 330, easing = LinearEasing))
        }
        onDone()
    }

    val fraction = if (settled) 1f else wipe.value
    val revealShape: Shape = if (settled) {
        cardShape()
    } else {
        GenericShape { size, _ -> addRect(Rect(0f, 0f, size.width, size.height * fraction)) }
    }

    Column(
        modifier
            .width(186.dp)
            .graphicsLayer {
                alpha = entry.value
                scaleX = 0.96f + 0.04f * fraction
                scaleY = 0.96f + 0.04f * fraction
            }
            .clip(cardShape())
            .graphicsLayer {
                clip = true
                shape = revealShape
            }
            .background(EvenlightColors.Card)
    ) {
        if (bands != null) BandField(bands, Modifier.fillMaxWidth().height(118.dp))
        winner?.let { band ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = strip.value }
                    .background(Color(band))
                    .padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 13.dp)
            ) {
                // 16 sp here, not the artefact's 29: the mockup's filmstrip draws the strip at 16 on a card this
                // wide. `strip` is the style - Fraunces, negative tracking - not the pixel count.
                Text(
                    record.name,
                    style = EvenlightType.strip.copy(fontSize = 16.sp, letterSpacing = (-0.02).em),
                    color = stripInk(band),
                    modifier = Modifier.graphicsLayer { alpha = name.value }
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp)) {
            Text(
                longDate(LocalDate.parse(record.date)),
                style = EvenlightType.title.copy(fontSize = 15.sp),
                color = EvenlightColors.Ink
            )
            Text(
                "sun ${record.sunAltitudeDeg}°",
                style = EvenlightType.caption,
                color = EvenlightColors.Muted,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}
