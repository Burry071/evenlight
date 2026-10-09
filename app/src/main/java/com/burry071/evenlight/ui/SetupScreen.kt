package com.burry071.evenlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.burry071.evenlight.data.Settings
import com.burry071.evenlight.data.Store
import com.burry071.evenlight.ui.theme.EvenlightColors
import com.burry071.evenlight.ui.theme.EvenlightType
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Setup: two fields, and the app knows where the sky is.
 *
 * No location permission anywhere - the manifest's runtime set is exactly `CAMERA`, and a hostel balcony does not
 * move. Coordinates are stored to two decimals (about 1.1 km), which is far coarser than the sun maths needs and is
 * what the brief specifies. One field holds `lat, lon` because that pair is what a user copies out of a map.
 *
 * `KeyboardType.NumberPassword` is the signed-decimal keyboard Android offers without a custom input filter: it is
 * the only keyboard type in this Compose version that puts a minus sign on the face, which negative latitudes need.
 */
@Composable
fun SetupScreen(
    store: Store,
    current: Settings,
    onSaved: (Settings) -> Unit,
    modifier: Modifier = Modifier
) {
    var place by remember { mutableStateOf(current.place) }
    var coords by remember {
        mutableStateOf(if (current.isSet) "${plain(current.lat)}, ${plain(current.lon)}" else "")
    }
    val parsed = remember(coords) { parseCoords(coords) }
    val canSave = place.isNotBlank() && parsed != null

    Column(
        modifier
            .fillMaxSize()
            .background(EvenlightColors.Paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp)
    ) {
        // The eyebrow class is uppercase in the design (mockups:31, :55), and Tonight's already follows it; this
        // screen's label was the one exception.
        Text("SETUP", style = EvenlightType.eyebrow, color = EvenlightColors.Muted)
        Text(
            "the sun line needs a place",
            style = EvenlightType.title,
            color = EvenlightColors.Ink,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            "Nothing is looked up and nothing is fetched: the app carries no INTERNET permission, so these two " +
                "numbers are the whole of what it knows about where you are standing.",
            style = EvenlightType.body,
            color = EvenlightColors.Muted,
            modifier = Modifier.padding(top = 9.dp)
        )

        OutlinedTextField(
            value = place,
            onValueChange = { place = it },
            label = { Text("place", style = EvenlightType.body) },
            textStyle = EvenlightType.body,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp)
        )
        OutlinedTextField(
            value = coords,
            onValueChange = { coords = it },
            label = { Text("lat, lon", style = EvenlightType.body) },
            textStyle = EvenlightType.body,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )

        // A pair this file cannot parse is a pair the sun maths cannot use, so the action waits for real numbers
        // rather than filing a place that would print an altitude for the wrong sky. The label is `Save`, not the
        // shutter's word: Setup performs a save, and the brief's copy rule is action names only.
        ShootPill(
            label = "Save",
            enabled = canSave,
            onClick = {
                val (lat, lon) = parsed ?: return@ShootPill
                store.saveSettings(Settings(place.trim(), round2(lat), round2(lon)))
                onSaved(Settings(place.trim(), round2(lat), round2(lon)))
            },
            modifier = Modifier.padding(top = 22.dp)
        )

        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(
                if (canSave) "saved to this device only" else "needs a place and a pair like 31.52, 74.35",
                style = EvenlightType.caption,
                color = EvenlightColors.Muted
            )
        }
    }
}

/** `null` unless the text is two finite numbers in range; a half-typed pair is not a place. */
private fun parseCoords(text: String): Pair<Double, Double>? {
    val parts = text.split(',', ';')
    if (parts.size != 2) return null
    val lat = parts[0].trim().toDoubleOrNull() ?: return null
    val lon = parts[1].trim().toDoubleOrNull() ?: return null
    if (lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return lat to lon
}

/**
 * Two decimals, away from zero, matching `Pixels.roundHalfUp` rather than banker's rounding: this is the number that
 * goes into `settings.json` and then into the sun maths, and a rounding rule that ties-to-even would disagree with
 * the renderer's rule on exactly the values that sit on a `.5`.
 */
private fun round2(v: Double): Double = (if (v < 0) -1.0 else 1.0) *
    (abs(v) * 100.0).roundToInt() / 100.0

/** The stored pair, printed back without a trailing `.0` and without a locale-sensitive formatter. */
private fun plain(v: Double): String {
    val scaled = round2(v)
    val sign = if (scaled < 0) "-" else ""
    val cents = (abs(scaled) * 100.0).roundToInt()
    val whole = cents / 100
    val frac = cents % 100
    return when {
        frac == 0 -> "$sign$whole"
        frac < 10 -> "$sign$whole.0$frac"
        else -> "$sign$whole.$frac"
    }
}
