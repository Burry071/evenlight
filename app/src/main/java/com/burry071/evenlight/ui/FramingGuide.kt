package com.burry071.evenlight.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Two strokes per guide line, one white and one dark offset by a pixel, so the line stays legible over a black band
 * and over a blown-out cloud.
 *
 * That is the property the mockup asked `mix-blend-mode: difference` for, and CSS could get it in one line because
 * the blend sees the pixels underneath. Android cannot: the camera frames arrive through `PreviewView` in a
 * different layer, so a `BlendMode.Difference` drawn inside this Canvas would invert only what Compose drew in this
 * same pass - a white line over the sky, invisible on the bright half of it. Ledger R6.
 *
 * `Color.White` and a dark alpha are the only colours in the app that are not `Palette` constants, and they clear
 * `PaletteDisciplineTest` because they are not hex: they sit over the camera rather than on the paper.
 */
@Composable
fun FramingGuide(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val t = 1f
        listOf(size.width / 3f, 2f * size.width / 3f).forEach { x ->
            drawLine(Color.Black.copy(alpha = 0.55f), Offset(x + t, 0f), Offset(x + t, size.height), t)
            drawLine(Color.White.copy(alpha = 0.9f), Offset(x, 0f), Offset(x, size.height), t)
        }
        val y = size.height / 2f
        drawLine(Color.Black.copy(alpha = 0.55f), Offset(0f, y + t), Offset(size.width, y + t), t)
        drawLine(Color.White.copy(alpha = 0.9f), Offset(0f, y), Offset(size.width, y), t)
    }
}
