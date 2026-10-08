# Evenlight Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Android app that turns one balcony sky photo into a card — eight colour bands measured from the photo's own pixels, named by a deterministic matcher, with one line of prose written by an on-device Gemma that is never allowed to choose anything.

**Architecture:** Two Gradle modules. `:core` is pure Kotlin/JVM with zero Android imports — solar maths, band sampling, matching, verification, geometry, record codec — so every decision is testable without a device and portable line-for-line to Python. `:app` holds CameraX, the Canvas renderer, Compose screens, the LiteRT-LM engine, and file storage. `tools/python/skycard.py` reimplements the `:core` pixel arithmetic and is proven equal to it against shared fixtures.

**Tech Stack:** Kotlin 2.2.21, AGP 8.6.0, Gradle 8.8, compileSdk/targetSdk 35, minSdk 27, JVM 17, Compose ui/foundation 1.9.0 + material3 1.4.0 + activity-compose 1.10.1 + lifecycle 2.9.0, CameraX 1.3.4, `com.google.ai.edge.litertlm:litertlm-android:0.16.1`, JUnit 4.13.2, Python 3 + Pillow.

**Spec:** `docs/superpowers/specs/2026-10-07-evenlight-design.md` (v2, approved 2026-10-08). Read it with this plan; where they disagree the spec wins and this file is wrong.

## Decisions taken while writing this plan

Four things the spec left open. Each is the fail-safe call, not a preference — object and they change.

1. **Balcony-session interruption counts stay out.** The spec flagged the removal "so the user can object"; no objection came. Re-adding later is one `Session.kt` plus two columns, not a restructure.
2. **Framing aid = three hairlines drawn in white with `BlendMode.Difference`** (spec §8.3), not a fixed-alpha ghost. No reference image, no alignment code.
3. **The downscale is hand-implemented, not `Bitmap.createScaledBitmap`.** The cross-language claim rests on identical arithmetic, and a platform filter's rounding is unspecified. Both languages implement the same stated bilinear formula and round half-up.
4. **Fixtures are PNG, not JPEG.** JPEG decoders differ in IDCT rounding across library versions; PNG decode is exact, so a band mismatch can only mean a sampler bug.

Resolved since those four, on the user's instruction (2026-10-08):

5. **Vocabulary count is 35, and it is enforced not asserted.** `vocabularyHasThirtyFiveWordsAndUniqueAnchors` in Task 4 checks the size, unique words, and unique anchors. The spec's original "36" was wrong when counted; the probe table's uncountable "36 words" has been de-precised rather than guessed at.
6. **`design/evenlight-mockups.html` is the visual source of truth; the rendered PNG is reference only** and is not pixel-authoritative. Its `sunset 17:52` copy was ~12 min wrong for 31.52, 74.35 and is now `17:40` in all three places (real sunset this week: 17:40 and 17:37 PKT). Copy the plan's computed values, never the mockup's, for any number.

## Global Constraints

Every task's requirements include this section.

- **`AndroidManifest.xml` contains no `INTERNET` permission.** `CAMERA` is the only runtime permission. Asserted in Task 1, not claimed.
- No notifications, widgets, exact alarms, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, usage-stats access, accessibility service, accounts, analytics, or crash reporting.
- **No gallery import, ever.** A card's pixels come from a fresh `ImageCapture` still.
- **Measured colour is never altered.** No saturation, contrast, vibrance, white balance, tint, filter, preset, or blend trick may touch a band between the sampler and the strip. The only permitted transformation is the linear blend between band centres, defined in Task 7 and reproduced in Python.
- Eight bands, ordered top → horizon, target width 64, band means arithmetic over the band's rows, rounded half-up.
- The vocabulary is exactly the 35 words in `Vocabulary.kt`. The model may never pick, rank, order, or measure; it words a line from a name it is handed.
- Model output is rejected if it contains `[0-9]`, `#`, `<`, `{`, `(`, or a newline, has **fewer than 4 or more than 8 words** (spec §7 asks for "one lowercase line of 4-8 words", so both ends are gates), is not lowercase, or omits the given name. "Omits" means the name is absent as a **word**: punctuation attached to it (`brassy,`) still counts, while `brassiness` does not. Rejection is counted, never hidden.
- Gaps are never collapsed: an evening with no photo renders as an outlined empty card with its date.
- No number may appear that the app did not measure or compute. No count in the README may be rounded up.
- UI copy: action names only (`Shoot`, `Stack`, `Setup`). **No "AI", "model", "Gemma", or "thinking" anywhere in the app's chrome.**
- Chrome colours are paper/ink only — `#F4EFE6 #FBF8F2 #2A2622 #6E6656 #D9D0BF`, night `#17150F #E9E3D6 #332F26`. No brand hue.
- Motion: shutter→card ≤ 450 ms; reveal by clip height, never by animating height; one haptic per action; Reduce Motion → 120 ms cross-fade.
- Fonts are bundled TTFs in `res/font/` (Fraunces variable, Instrument Sans variable, both SIL OFL). Downloadable-font APIs are forbidden — no network.
- Build from the repo root with the cached Gradle: `/c/Users/User/.gradle/wrapper/dists/gradle-8.8-bin/dl7vupf4psengwqhwktix4v1/gradle-8.8/bin/gradle.bat`. Prefix `adb` remote paths with `MSYS_NO_PATHCONV=1`. Python is `/f/Ancoda/python.exe`.
- **Commits are local only.** `git push` and creating `Burry071/evenlight` need an explicit go-ahead (Task 12).
- **Commit steps written as `git commit -am` are shorthand, not an instruction.** `-a` stages only paths Git already tracks, so a task that creates new files commits nothing (or, worse, commits its edits to existing files while silently dropping the new ones) and still exits 0. Always stage explicit paths, then confirm with `git status --short` that nothing the task created is left untracked before reporting the commit.

### Checkpoint before Task 1 step 6 — cleared

The first build needs the network: `androidx.camera` and the Compose compiler plugin are **not** in this machine's Gradle cache (verified 2026-10-08). Compose 1.9.0, material3 1.4.0, activity-compose 1.10.1, lifecycle 2.9.0, core-ktx 1.15.0 and JUnit 4.13.2 **are** cached. Expect a few tens of MB of AARs, no model weights.

**Cleared 2026-10-08 by the user's explicit word for this build's dependencies only.** The 584 MB Gemma weights in Task 11 are a *separate* ask and are not covered by this — ask again before they are downloaded or pushed to a device. Running Task 1 step 6 without `--offline` is authorized; adding a large-download step to any later task is not.

---

## File Structure

```
evenlight/                       repo root = the current workspace
├─ settings.gradle.kts  build.gradle.kts  gradle.properties
├─ core/src/main/kotlin/com/burry071/evenlight/core/
│  ├─ SunWindow.kt        T2   sunset instant + sun altitude
│  ├─ Pixels.kt           T3   packed-RGB helpers, WCAG luminance, round-half-up
│  ├─ BandSampler.kt      T3   frame -> 8 ordered bands + mean luminance
│  ├─ Vocabulary.kt       T4   35 words, one anchor hex each
│  ├─ Matcher.kt          T4   winner band, nearest word
│  ├─ Verifier.kt         T5   gates the model's line
│  ├─ Json.kt             T6   dependency-free encoder + strict parser
│  ├─ EveningRecord.kt    T6   the record + codec
│  └─ CardGeometry.kt     T7   Palette + layout numbers + strip-ink rule
├─ core/src/test/kotlin/com/burry071/evenlight/core/   (one Test per file above)
├─ app/src/main/AndroidManifest.xml
├─ app/src/main/res/font/{fraunces_variable,instrument_sans_variable}.ttf
├─ app/src/main/java/com/burry071/evenlight/
│  ├─ EvenlightApp.kt  MainActivity.kt  EvenlightController.kt   T1, T8
│  ├─ data/{Store,Settings}.kt                                   T8
│  ├─ capture/FrameSource.kt                                     T8
│  ├─ card/CardRenderer.kt                                       T7
│  ├─ wording/{EngineHolder,Wording}.kt                          T11
│  └─ ui/theme/{Color,Type,Theme}.kt  ui/{Tonight,Shoot,Card,Stack,Setup}Screen.kt
│                                    ui/FramingGuide.kt          T9
├─ tools/python/{skycard.py,test_skycard.py}                     T10
├─ fixtures/{make_fixtures.py,sky_ramp.png,sky_blocks.png,sky_wide.png,sky_narrow.png,*.bands.json}  T10
├─ models/GET_MODEL.md  README.md  LICENSE                       T12
└─ design/evenlight-mockups.html  (exists — the visual source of truth)
```

`:core` has no Android dependency at all, which makes "every decision is testable on a JVM" a structural fact rather than an intention.

---

## Task 1: Repo scaffold that builds and asserts the permission claim

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `core/build.gradle.kts`, `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/{strings,themes}.xml`, `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/java/com/burry071/evenlight/{MainActivity,EvenlightApp}.kt`
- Test: `app/src/test/java/com/burry071/evenlight/ManifestTest.kt`

**Interfaces:**
- Produces: a two-module build where `:core` compiles as plain JVM and `:app` depends on `project(":core")`. Package root `com.burry071.evenlight`.

- [ ] **Step 1: Write the failing test.** The project's central claim is checkable in one file, so assert it before anything else exists.

```kotlin
package com.burry071.evenlight

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestTest {
    private val manifest: String by lazy { File("src/main/AndroidManifest.xml").readText() }

    @Test fun declaresNoInternetPermission() {
        assertFalse("INTERNET must never appear", manifest.contains("android.permission.INTERNET"))
    }

    @Test fun declaresNoNetworkStatePermission() {
        assertFalse(manifest.contains("android.permission.ACCESS_NETWORK_STATE"))
    }

    @Test fun cameraIsTheOnlyRuntimePermission() {
        val permissions = Regex("android\\.permission\\.([A-Z_]+)")
            .findAll(manifest).map { it.groupValues[1] }.toSet()
        assertTrue("expected CAMERA, found $permissions", permissions == setOf("CAMERA"))
    }
}
```

- [ ] **Step 2: Run it and watch it fail** for the right reason (no Gradle build yet):
`/c/Users/User/.gradle/wrapper/dists/gradle-8.8-bin/dl7vupf4psengwqhwktix4v1/gradle-8.8/bin/gradle.bat :app:testDebugUnitTest --offline -q`

- [ ] **Step 3: Write the Gradle files.**

`settings.gradle.kts`:

```kotlin
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "evenlight"
include(":core", ":app")
```

`build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.21" apply false
}
```

`gradle.properties`:

```
org.gradle.jvmargs=-Xmx3072m
android.useAndroidX=true
kotlin.code.style=official
```

`core/build.gradle.kts` — no Android in this module, on purpose:

```kotlin
plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { jvmToolchain(17) }
dependencies { testImplementation("junit:junit:4.13.2") }
```

`app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.burry071.evenlight"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.burry071.evenlight"
        minSdk = 27
        targetSdk = 35
    }
    buildTypes { getByName("debug") { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.compose.ui:ui:1.9.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.0")
    implementation("androidx.compose.foundation:foundation:1.9.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.1")
    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 4: Write the manifest, Application, and a placeholder Activity.**

`app/src/main/AndroidManifest.xml` — no `INTERNET` line, and none is implied:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera.any" android:required="true" />
    <application
        android:name=".EvenlightApp"
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:allowBackup="false"
        android:supportsRtl="true"
        android:theme="@style/Theme.Evenlight">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:theme="@style/Theme.Evenlight">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`res/values/strings.xml`: `<resources><string name="app_name">Evenlight</string></resources>`
`res/values/themes.xml`: `<resources><style name="Theme.Evenlight" parent="android:Theme.Material.NoActionBar" /></resources>`
`res/mipmap-anydpi-v26/ic_launcher.xml`: an `<adaptive-icon>` with `@android:color/black` background and `@android:color/white` foreground — without it the manifest merge fails.

`EvenlightApp.kt`: `class EvenlightApp : Application()`

`MainActivity.kt`:

```kotlin
package com.burry071.evenlight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Evenlight") } }
    }
}
```

- [ ] **Step 5: Ask the user for the network checkpoint** (header), then run the first build without `--offline`.

- [ ] **Step 6: Verify.**
`... gradle.bat :app:testDebugUnitTest :core:test -q` → 3 tests PASS.
`... gradle.bat :app:assembleDebug -q && MSYS_NO_PATHCONV=1 adb install -r app/build/outputs/apk/debug/app-debug.apk` → "Evenlight" on screen.

- [ ] **Step 7: Commit.**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties core app
git commit -m "build: two-module Evenlight scaffold with a manifest test for the no-network claim"
```

---

## Task 2: `SunWindow` — sunset and altitude, validated against published times

**Files:**
- Create: `core/src/main/kotlin/com/burry071/evenlight/core/SunWindow.kt`
- Test: `core/src/test/kotlin/com/burry071/evenlight/core/SunWindowTest.kt`

**Interfaces:**
- Produces: `data class GeoPoint(val latDeg: Double, val lonDeg: Double)`;
  `SunWindow.sunsetUtcMinutes(date: LocalDate, g: GeoPoint, tzOffsetHours: Double): Double`;
  `SunWindow.sunset(date, g, zone): ZonedDateTime`;
  `SunWindow.altitudeAt(instantUtc: LocalDateTime, g: GeoPoint): Double` (degrees, negative below);
  `SunWindow.altitudeForDisplay(instantUtc, g): Int`;
  `SunWindow.inSunsetWindow(now: ZonedDateTime, g, minutes: Long = 20): Boolean`.
- Consumed by: Task 8 (shutter → altitude), Task 9 (countdown), Task 10 (Python mirror).

- [ ] **Step 1: Write the failing test.** These constants are measured, not invented: computed 2026-10-08 from the NOAA series for 31.52, 74.35 and compared against `api.sunrise-sunset.org` (published **12:40:48Z** and **12:37:11Z**).

```kotlin
package com.burry071.evenlight.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SunWindowTest {
    private val home = GeoPoint(31.52, 74.35)
    private val pkt = ZoneId.of("Asia/Karachi")

    @Test fun sunsetOn2026Oct08IsWithinTwoMinutesOfPublished() {
        assertEquals(12 * 60 + 40.8, SunWindow.sunsetUtcMinutes(LocalDate.of(2026, 10, 8), home, 5.0), 2.0)
    }

    @Test fun sunsetOn2026Oct11IsWithinTwoMinutesOfPublished() {
        assertEquals(12 * 60 + 37.2, SunWindow.sunsetUtcMinutes(LocalDate.of(2026, 10, 11), home, 5.0), 2.0)
    }

    @Test fun sunsetMovesEarlierAcrossTheWeek() {
        val a = SunWindow.sunsetUtcMinutes(LocalDate.of(2026, 10, 8), home, 5.0)
        val b = SunWindow.sunsetUtcMinutes(LocalDate.of(2026, 10, 11), home, 5.0)
        assertTrue("expected $b < $a", b < a)
    }

    @Test fun altitudeAtTheRoundedMinuteSunsetReturnsCarriesBothBiases() {
        // Two error sources, both deliberate, both named so a future reader stops guessing:
        // (a) the solver uses ZENITH_SUNSET = 90.833 (34' refraction + 16' semidiameter) but evaluates
        //     decl/eqt once at local noon, so it does not land exactly on -0.833;
        // (b) sunset() quantizes to the nearest whole minute (12:40:00Z for 2026-10-08), and this
        //     assertion evaluates at that rounded minute, not at the exact instant.
        // Measured from the compiled Kotlin, not derived by hand: -0.988.
        val sunsetLocal = SunWindow.sunset(LocalDate.of(2026, 10, 8), home, pkt)
        val utc = sunsetLocal.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime()
        assertEquals(-0.988, SunWindow.altitudeAt(utc, home), 0.05)
    }

    @Test fun sunsetDisplayedMinuteIsWithinAMinuteOfPublished() {
        // The home screen prints "sunset HH:mm", so the displayed minute must agree with reality. Assert
        // proximity, not an exact string: today's computed instant sits 3.4 s from the half-minute
        // ROUNDING TIE (26.6 s from the minute boundary it lands on), so a pinned "17:40" would certify a
        // near-tie and break the day the maths gets MORE accurate. Truncating sunset() instead of rounding
        // puts the display 108 s out - which fails here, and that regression is why this test exists.
        // This 60 s bound is deliberately TIGHTER than the ±2-minute solver gates above: they measure the
        // accuracy of the raw solve, this measures whether the printed minute is the one the solver
        // computed. Red here with those gates green means bias passed 1 minute - a tuning signal, not a
        // spec break.
        val s = SunWindow.sunset(LocalDate.of(2026, 10, 8), home, pkt)
        val published = ZonedDateTime.of(2026, 10, 8, 12, 40, 48, 0, ZoneId.of("UTC"))
            .withZoneSameInstant(pkt)
        val drift = abs(Duration.between(s, published).toSeconds())
        assertTrue("displayed $s is ${drift}s from published $published", drift <= 60)
    }

    @Test fun altitudeForDisplayRoundsToWholeDegrees() {
        // The app shows "sun 6°" - the second and last solar number it displays. Nothing else covers it.
        assertEquals(52, SunWindow.altitudeForDisplay(LocalDateTime.of(2026, 10, 8, 7, 0), home))
        assertEquals(-1, SunWindow.altitudeForDisplay(LocalDateTime.of(2026, 10, 8, 12, 40), home))
    }

    @Test fun noonAltitudeMatchesTheKnownValue() {
        assertEquals(52.49, SunWindow.altitudeAt(LocalDateTime.of(2026, 10, 8, 7, 0), home), 0.05)
    }

    @Test fun eveningAltitudeIsWellBelowTheHorizon() {
        assertEquals(-24.386, SunWindow.altitudeAt(LocalDateTime.of(2026, 10, 8, 14, 30), home), 0.05)
    }

    @Test fun windowOpensTwentyMinutesEarlyAndClosesTwentyLate() {
        val s = SunWindow.sunset(LocalDate.of(2026, 10, 8), home, pkt)
        assertTrue(SunWindow.inSunsetWindow(s.minusMinutes(19), home))
        assertTrue(SunWindow.inSunsetWindow(s.plusMinutes(19), home))
        assertFalse(SunWindow.inSunsetWindow(s.minusMinutes(21), home))
        assertFalse(SunWindow.inSunsetWindow(s.plusMinutes(21), home))
    }
}
```

- [ ] **Step 2: Run it.** `... gradle.bat :core:test --offline -q --tests "*SunWindowTest*"` → FAIL, unresolved `SunWindow`.

- [ ] **Step 3: Implement.**

```kotlin
package com.burry071.evenlight.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

data class GeoPoint(val latDeg: Double, val lonDeg: Double)

/**
 * NOAA solar-position approximation. Pure computation: this is what lets the app know when the
 * light is interesting while its manifest carries no INTERNET permission. Measured bias against
 * published times at 31.52,74.35 is -1.2 min - inside the +/-2 min the plan allows, immaterial to
 * a +/-20 min window.
 */
object SunWindow {

    private const val ZENITH_SUNSET = 90.833
    private const val EPOCH_TO_JD = 2440587.5   // 1970-01-01Z as a Julian Day

    private fun julianDay(utc: LocalDateTime): Double =
        utc.toLocalDate().toEpochDay() + EPOCH_TO_JD +
            (utc.hour * 3600 + utc.minute * 60 + utc.second) / 86400.0

    /** Solar declination (radians) and equation of time (minutes). */
    private fun solar(jd: Double): Pair<Double, Double> {
        val t = (jd - 2451545.0) / 36525.0
        val meanLong = norm(280.46646 + 36000.76983 * t + 0.0003032 * t * t)
        val meanAnom = norm(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
        val e = 0.016708634 - 0.000042037 * t
        val m = Math.toRadians(meanAnom)
        val trueLong = norm(
            meanLong + (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(m) +
                (0.019993 - 0.000101 * t) * sin(2 * m) + 0.000289 * sin(3 * m)
        )
        val omega = norm(125.04 - 1934.136 * t)
        val lambda = norm(trueLong - 0.00569 - 0.00478 * sin(Math.toRadians(omega)))
        val eps0 = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val eps = Math.toRadians(eps0 + 0.00256 * cos(Math.toRadians(omega)))
        val decl = asin(sin(eps) * sin(Math.toRadians(lambda)))
        val y = tan(eps / 2.0).let { it * it }
        val l0 = Math.toRadians(meanLong)
        val eqTime = 4.0 * Math.toDegrees(
            y * sin(2 * l0) - 2 * e * sin(m) + 4 * e * y * sin(m) * cos(2 * l0) -
                0.5 * y * y * sin(4 * l0) - 1.25 * e * e * sin(2 * m)
        )
        return decl to eqTime
    }

    private fun norm(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

    /** Minutes from 00:00 UTC. Evaluated once at local noon; the residual is the bias above. */
    fun sunsetUtcMinutes(date: LocalDate, g: GeoPoint, tzOffsetHours: Double): Double {
        val noonUtc = LocalDateTime.of(date, LocalTime.NOON).minusHours(tzOffsetHours.toLong())
        val (decl, eqTime) = solar(julianDay(noonUtc))
        val lat = Math.toRadians(g.latDeg)
        val cosH = (cos(Math.toRadians(ZENITH_SUNSET)) - sin(lat) * sin(decl)) / (cos(lat) * cos(decl))
        val hourAngle = Math.toDegrees(acos(cosH.coerceIn(-1.0, 1.0)))
        return 720.0 - 4.0 * (g.lonDeg - hourAngle) - eqTime
    }

    fun sunset(date: LocalDate, g: GeoPoint, zone: ZoneId): ZonedDateTime {
        val offset = zone.rules.getOffset(java.time.Instant.now()).totalSeconds / 3600.0
        val minutesUtc = sunsetUtcMinutes(date, g, offset)
        return LocalDateTime.of(date, LocalTime.MIDNIGHT)
            .plusMinutes(minutesUtc.roundToInt().toLong())
            .atZone(ZoneId.of("UTC"))
            .withZoneSameInstant(zone)
    }

    /** Sun altitude in degrees at a UTC instant. Negative below the horizon. */
    fun altitudeAt(instantUtc: LocalDateTime, g: GeoPoint): Double {
        val (decl, eqTime) = solar(julianDay(instantUtc))
        val utcMinutes = instantUtc.hour * 60.0 + instantUtc.minute + instantUtc.second / 60.0
        val trueSolar = (utcMinutes + eqTime + 4.0 * g.lonDeg) % 1440.0
        val hourAngle = trueSolar / 4.0 - 180.0
        val lat = Math.toRadians(g.latDeg)
        val cosZenith = sin(lat) * sin(decl) + cos(lat) * cos(decl) * cos(Math.toRadians(hourAngle))
        return 90.0 - Math.toDegrees(acos(cosZenith.coerceIn(-1.0, 1.0)))
    }

    fun altitudeForDisplay(instantUtc: LocalDateTime, g: GeoPoint): Int = altitudeAt(instantUtc, g).roundToInt()

    fun inSunsetWindow(now: ZonedDateTime, g: GeoPoint, minutes: Long = 20):Boolean =
        abs(java.time.Duration.between(now, sunset(now.toLocalDate(), g, now.zone)).toMinutes()) <= minutes
}
```

- [ ] **Step 4: Run it.** → PASS, 7 tests. **If either sunset assertion misses by more than 2 minutes, stop and report**: this is the spec's probe #1 gate, and the sampling rule depends on it.

- [ ] **Step 5: Commit.** `git add core && git commit -m "feat: NOAA sunset and sun altitude, validated against published Lahore times"`

---

## Task 3: `Pixels` + `BandSampler` — the eight ordered bands

**Files:**
- Create: `core/.../Pixels.kt`, `core/.../BandSampler.kt`
- Test: `core/src/test/kotlin/.../BandSamplerTest.kt`

**Interfaces:**
- Produces:
  - `object Pixels { r(c) g(c) b(c): Int; rgb(r,g,b): Int; hex(c): String; parseHex(s): Int; relativeLuminance(c): Double; roundHalfUp(v: Double): Int }` — colours are packed `0x00RRGGBB` ints everywhere in `:core`. `hex` builds its string from an explicit ASCII digit table, never a locale-resolved format: Task 8 persists that exact string and Task 10 diffs it against Python's ASCII `%02X`. There is deliberately no `luma()` — a second luminance formula with `roundToInt` would invite a caller to mix rounding conventions.
  - `data class Bands(val ordered: List<Int>, val meanLuminance: Double)` — `ordered.size == 8`, index 0 is the **top** of the frame.
  - `object BandSampler { const val TARGET_WIDTH = 64; const val BAND_COUNT = 8; fun sample(pixels: IntArray, width: Int, height: Int): Bands }`
- Consumed by: Tasks 4, 7, 8, and 10 — **this signature is the cross-language contract.**

- [ ] **Step 1: Write the failing test.** Every expectation is hand-computable, which is the point: a golden nobody can derive is a bug you cannot read.

```kotlin
package com.burry071.evenlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BandSamplerTest {
    private fun rows(w: Int, values: List<Int>): IntArray {
        val h = values.size
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) out[y * w + x] = values[y]
        return out
    }

    @Test fun eightRowRampReturnsItsOwnEightColoursInOrder() {
        val v = (0 until 8).map { Pixels.rgb(10 + it * 20, 200 - it * 10, 30 + it * 5) }
        val bands = BandSampler.sample(rows(64, v), 64, 8)
        assertEquals(v, bands.ordered)
        assertEquals("#1EBE23", Pixels.hex(bands.ordered[1]))  // rgb(30,190,35): 190 = 0xBE, not 0xC8
    }

    @Test fun topOfFrameIsBandZero() {
        val v = listOf(Pixels.rgb(20, 24, 40), Pixels.rgb(20, 24, 40),
                       Pixels.rgb(200, 160, 90), Pixels.rgb(200, 160, 90))
        val bands = BandSampler.sample(rows(64, v), 64, 4)
        assertEquals(8, bands.ordered.size)
        assertEquals(Pixels.rgb(20, 24, 40), bands.ordered[0])
        assertEquals(Pixels.rgb(200, 160, 90), bands.ordered[7])
    }

    @Test fun fewerRowsThanBandsRepeatsTheNearestRowInsteadOfCrashing() {
        val bands = BandSampler.sample(rows(8, listOf(Pixels.rgb(255, 0, 0), Pixels.rgb(0, 0, 255))), 8, 2)
        assertEquals(8, bands.ordered.size)
        repeat(4) { assertEquals(Pixels.rgb(255, 0, 0), bands.ordered[it]) }
        repeat(4) { assertEquals(Pixels.rgb(0, 0, 255), bands.ordered[4 + it]) }
    }

    @Test fun uniformFrameGivesEightIdenticalBands() {
        val c = Pixels.rgb(120, 130, 140)
        val bands = BandSampler.sample(IntArray(64 * 48) { c }, 64, 48)
        assertEquals(List(8) { c }, bands.ordered)
        assertEquals(Pixels.relativeLuminance(c), bands.meanLuminance, 1e-9)
    }

    @Test fun bilinearDownscaleOfAHalfBlackHalfWhiteRowIsTheMidpoint() {
        val w = 128
        val px = IntArray(2 * w) { x -> val col = x % w; if (col < w / 2) Pixels.rgb(0, 0, 0) else Pixels.rgb(255, 255, 255) }
        assertEquals(Pixels.rgb(128, 128, 128), BandSampler.sample(px, w, 2).ordered[0])
    }

    @Test fun meanLuminanceIsWithinUnitRange() {
        val v = (0 until 8).map { Pixels.rgb(it * 30, 255 - it * 30, 128) }
        assertTrue(BandSampler.sample(rows(64, v), 64, 8).meanLuminance in 0.0..1.0)
    }
}
```

- [ ] **Step 2: Run it** → FAIL, unresolved `BandSampler`.

- [ ] **Step 3: Implement `Pixels.kt`.**

```kotlin
package com.burry071.evenlight.core

import kotlin.math.pow
import kotlin.math.roundToInt

/** Colours are packed as 0x00RRGGBB ints. No Android types anywhere in this package. */
object Pixels {
    fun r(c: Int): Int = (c shr 16) and 0xFF
    fun g(c: Int): Int = (c shr 8) and 0xFF
    fun b(c: Int): Int = c and 0xFF
    fun rgb(r: Int, g: Int, b: Int): Int = (r shl 16) or (g shl 8) or b

    fun hex(c: Int): String = "#%02X%02X%02X".format(r(c), g(c), b(c))

    fun parseHex(s: String): Int {
        val t = s.removePrefix("#")
        require(t.length == 6) { "not a hex colour: $s" }
        return (t.substring(0, 2).toInt(16) shl 16) or (t.substring(2, 4).toInt(16) shl 8) or t.substring(4, 6).toInt(16)
    }

    /** WCAG relative luminance, 0..1. Used only to pick a text colour - never to alter a colour. */
    fun relativeLuminance(c: Int): Double {
        fun lin(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin(r(c)) + 0.7152 * lin(g(c)) + 0.0722 * lin(b(c))
    }

    /** Round-half-up. Python mirrors this with floor(x + 0.5); do NOT use banker's rounding. */
    fun roundHalfUp(v: Double): Int = Math.floor(v + 0.5).toInt()
}
```

- [ ] **Step 4: Implement `BandSampler.kt`.** Written out rather than delegated to a platform filter because both languages must run the same arithmetic.

```kotlin
package com.burry071.evenlight.core

import kotlin.math.max
import kotlin.math.min

data class Bands(val ordered: List<Int>, val meanLuminance: Double)

/** Frame -> eight ordered band colours, index 0 at the top of the frame. */
object BandSampler {
    const val TARGET_WIDTH = 64
    const val BAND_COUNT = 8

    fun sample(pixels: IntArray, width: Int, height: Int): Bands {
        require(pixels.size == width * height) { "expected ${width * height} pixels, got ${pixels.size}" }
        val dw = TARGET_WIDTH
        // The min(height, ...) clamp is load-bearing: width < TARGET_WIDTH makes height*dw/width > height,
        // and dh > height means the bilinear pass INVENTS interpolated rows instead of repeating the
        // nearest source row. It is the identity for every width >= 64, so the downscale path Python
        // mirrors (Task 10) is bit-for-bit unchanged. fixtures/sky_narrow.png is the only <64 case.
        val dh = max(1, min(height, Pixels.roundHalfUp(height.toDouble() * dw / width)))
        val small = bilinear(pixels, width, height, dw, dh)

        var lumSum = 0.0
        val bands = ArrayList<Int>(BAND_COUNT)
        for (i in 0 until BAND_COUNT) {
            val y0 = i * dh / BAND_COUNT
            val y1 = (i + 1) * dh / BAND_COUNT
            val from = min(y0, dh - 1)
            val to = min(max(y1, y0 + 1) - 1, dh - 1)
            var rs = 0L; var gs = 0L; var bs = 0L; var n = 0
            for (y in from..to) {
                val row = y * dw
                for (x in 0 until dw) {
                    val c = small[row + x]
                    rs += Pixels.r(c); gs += Pixels.g(c); bs += Pixels.b(c); n++
                }
            }
            val band = Pixels.rgb(
                Pixels.roundHalfUp(rs.toDouble() / n),
                Pixels.roundHalfUp(gs.toDouble() / n),
                Pixels.roundHalfUp(bs.toDouble() / n)
            )
            bands += band
            lumSum += Pixels.relativeLuminance(band)
        }
        return Bands(bands, lumSum / BAND_COUNT)
    }

    /** Half-pixel-centre bilinear, per-channel round-half-up. Python mirrors this exactly. */
    private fun bilinear(src: IntArray, sw: Int, sh: Int, dw: Int, dh: Int): IntArray {
        if (sw == dw && sh == dh) return src.copyOf()
        val out = IntArray(dw * dh)
        val scaleX = sw.toDouble() / dw
        val scaleY = sh.toDouble() / dh
        for (dy in 0 until dh) {
            val fy = ((dy + 0.5) * scaleY - 0.5).coerceIn(0.0, (sh - 1).toDouble())
            val y0 = fy.toInt(); val y1 = min(y0 + 1, sh - 1); val wy = fy - y0
            for (dx in 0 until dw) {
                val fx = ((dx + 0.5) * scaleX - 0.5).coerceIn(0.0, (sw - 1).toDouble())
                val x0 = fx.toInt(); val x1 = min(x0 + 1, sw - 1); val wx = fx - x0
                val a = src[y0 * sw + x0]; val b = src[y0 * sw + x1]
                val c = src[y1 * sw + x0]; val d = src[y1 * sw + x1]
                val w00 = (1 - wx) * (1 - wy); val w01 = wx * (1 - wy)
                val w10 = (1 - wx) * wy; val w11 = wx * wy
                fun mix(shift: Int): Int = Pixels.roundHalfUp(
                    ((a shr shift) and 0xFF) * w00 + ((b shr shift) and 0xFF) * w01 +
                        ((c shr shift) and 0xFF) * w10 + ((d shr shift) and 0xFF) * w11
                ).coerceIn(0, 255)
                out[dy * dw + dx] = Pixels.rgb(mix(16), mix(8), mix(0))
            }
        }
        return out
    }
}
```

- [ ] **Step 5: Run it.** → PASS, 6 tests.

- [ ] **Step 6: Commit.** `git commit -am "feat: hand-rolled bilinear band sampler with hand-computable goldens"`

---

## Task 4: `Vocabulary` + `Matcher` — the choices the model is not allowed to make

**Files:**
- Create: `core/.../Vocabulary.kt`, `core/.../Matcher.kt`
- Test: `core/src/test/kotlin/.../MatcherTest.kt`

**Interfaces:**
- Produces: `data class VocabWord(val word: String, val anchorHex: String) { val anchor: Int }`;
  `object Vocabulary { val words: List<VocabWord>; val size: Int /* 35 */ }`;
  `data class Match(val winnerIndex: Int, val name: String, val neighbours: List<Int>)`;
  `object Matcher { chromaRange(c): Int; winner(bands: List<Int>): Int; nameFor(c: Int): String; match(bands): Match }`
- Consumed by: Tasks 6, 7, 11.

- [ ] **Step 1: Write the failing test.**

```kotlin
package com.burry071.evenlight.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MatcherTest {
    private val grey = (0 until 8).map { Pixels.rgb(10 + it * 12, 10 + it * 12, 10 + it * 12) }

    @Test fun winnerIsTheMostChromaticBand() {
        val b = grey.toMutableList().also { it[5] = Pixels.rgb(194, 133, 74) }
        assertEquals(5, Matcher.winner(b))
    }

    @Test fun chromaTieGoesToTheBrighterBand() {
        assertEquals(1, Matcher.winner(listOf(Pixels.rgb(10, 60, 110), Pixels.rgb(150, 200, 250))))
    }

    @Test fun fullTieGoesToTheLowestIndex() {
        val c = Pixels.rgb(100, 150, 200)
        assertEquals(0, Matcher.winner(listOf(c, c, c)))
    }

    @Test fun anchorsAreNamedForTheirOwnColour() {
        assertEquals("brassy", Matcher.nameFor(Pixels.parseHex("#C2854A")))
        assertEquals("slate", Matcher.nameFor(Pixels.parseHex("#5C6B82")))
        assertEquals("chalk", Matcher.nameFor(Pixels.parseHex("#E3DED4")))
        assertEquals("sunken", Matcher.nameFor(Pixels.parseHex("#262A33")))
    }

    @Test fun neighboursAreAboveThenBelow() {
        val b = grey.toMutableList().also { it[3] = Pixels.rgb(194, 133, 74) }
        val m = Matcher.match(b)
        assertEquals(3, m.winnerIndex)
        assertEquals(listOf(b[2], b[4]), m.neighbours)
    }

    @Test fun winnerAtTheTopEdgeHasOneNeighbour() {
        val b = grey.toMutableList().also { it[0] = Pixels.rgb(250, 120, 20) }
        val m = Matcher.match(b)
        assertEquals(0, m.winnerIndex)
        assertEquals(listOf(b[1]), m.neighbours)
    }

    @Test fun vocabularyHasThirtyFiveWordsAndUniqueAnchors() {
        assertEquals(35, Vocabulary.size)
        assertEquals(35, Vocabulary.words.map { it.word }.toSet().size)
        // Parsed colours, not hex strings: two entries spelling the same colour differently would keep the
        // matcher's count at 35 while one anchor silently shadows the other.
        assertEquals(35, Vocabulary.words.map { it.anchor }.toSet().size)
    }

    @Test fun exactEquidistanceResolvesToTheEarlierVocabularyEntry() {
        // Determinism is the whole argument for matching in code, and `d < best` only holds because the list
        // order is fixed: milky (#CFC9BE) precedes linen (#D9CFC0), so this colour must be "milky". Such
        // colours are not exotic — sweeping every integral (r, g) against that pair finds 953 colours
        // exactly equidistant between milky and linen with no closer anchor. Task 10's Python must agree, so
        // both languages need the same strict `<` and the same list order.
        assertEquals("milky", Matcher.nameFor(Pixels.parseHex("#D4CCBF")))
    }

    @Test fun allThreeChannelsParticipateInTheDistance() {
        // One grey and three single-channel moves. A metric that ignored a channel would name every one of
        // these "flint", which is how a drop-blue regression hides behind an anchor-named-itself test.
        assertEquals("flint", Matcher.nameFor(Pixels.rgb(128, 128, 128)))
        assertEquals("copper", Matcher.nameFor(Pixels.rgb(128, 128, 0)))
        assertEquals("still", Matcher.nameFor(Pixels.rgb(0, 128, 128)))
        assertEquals("bruised", Matcher.nameFor(Pixels.rgb(128, 0, 128)))
    }
}
```

- [ ] **Step 2: Run it** → FAIL, unresolved `Matcher`.

- [ ] **Step 3: Implement `Vocabulary.kt`.** The spec's 35 words, one anchor hex each.

```kotlin
package com.burry071.evenlight.core

/**
 * The evening vocabulary. A word is chosen by nearest neighbour in RGB, so the app is
 * structurally incapable of inventing one - which is why the model does not pick names
 * (spec section 7: 18/18 obedient on format, 1/6 reliable on judgement).
 */
object Vocabulary {
    val words: List<VocabWord> = listOf(
        VocabWord("still", "#4A5560"), VocabWord("smothered", "#5A5148"),
        VocabWord("clearing", "#A8C0CC"), VocabWord("brassy", "#C2854A"),
        VocabWord("tarnished", "#6B5D4A"), VocabWord("milky", "#CFC9BE"),
        VocabWord("flint", "#7E7A72"), VocabWord("ember", "#B4552C"),
        VocabWord("copper", "#A96A3C"), VocabWord("chalk", "#E3DED4"),
        VocabWord("slate", "#5C6B82"), VocabWord("ash", "#9B968D"),
        VocabWord("wax", "#D8B878"), VocabWord("honey", "#C89A46"),
        VocabWord("bruised", "#5A4560"), VocabWord("waited", "#6E5F52"),
        VocabWord("low", "#3A322A"), VocabWord("thin", "#BFC6CB"),
        VocabWord("wide", "#7C93A8"), VocabWord("close", "#44403A"),
        VocabWord("dusty", "#A08A72"), VocabWord("wet", "#2E3A42"),
        VocabWord("salt", "#D5D2C8"), VocabWord("iron", "#4C4A49"),
        VocabWord("paper", "#EDE8DE"), VocabWord("linen", "#D9CFC0"),
        VocabWord("wool", "#B8A894"), VocabWord("amber", "#B08A5E"),
        VocabWord("smoke", "#6A6A6A"), VocabWord("held", "#7A6A55"),
        VocabWord("lifted", "#9FB6C4"), VocabWord("sunken", "#262A33"),
        VocabWord("gathering", "#55606B"), VocabWord("fading", "#C79A7A"),
        VocabWord("bright", "#E8D9B8")
    )
    val size: Int get() = words.size
}

data class VocabWord(val word: String, val anchorHex: String) {
    val anchor: Int get() = Pixels.parseHex(anchorHex)
}
```

- [ ] **Step 4: Implement `Matcher.kt`.**

```kotlin
package com.burry071.evenlight.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class Match(val winnerIndex: Int, val name: String, val neighbours: List<Int>)

object Matcher {
    fun chromaRange(c: Int): Int {
        val r = Pixels.r(c); val g = Pixels.g(c); val b = Pixels.b(c)
        return max(r, max(g, b)) - min(r, min(g, b))
    }

    /** Max chroma range; tie -> higher relative luminance; tie -> lowest index. Deterministic. */
    fun winner(bands: List<Int>): Int {
        require(bands.isNotEmpty())
        var best = 0
        var bestRange = chromaRange(bands[0])
        var bestLum = Pixels.relativeLuminance(bands[0])
        for (i in 1 until bands.size) {
            val range = chromaRange(bands[i])
            val lum = Pixels.relativeLuminance(bands[i])
            if (range > bestRange || (range == bestRange && lum > bestLum)) {
                best = i; bestRange = range; bestLum = lum
            }
        }
        return best
    }

    /** Euclidean RGB distance, unweighted. Ties keep the earlier vocabulary entry. */
    fun nameFor(c: Int): String {
        var word = Vocabulary.words.first().word
        var best = Double.MAX_VALUE
        for (w in Vocabulary.words) {
            val dr = (Pixels.r(c) - Pixels.r(w.anchor)).toDouble()
            val dg = (Pixels.g(c) - Pixels.g(w.anchor)).toDouble()
            val db = (Pixels.b(c) - Pixels.b(w.anchor)).toDouble()
            val d = sqrt(dr * dr + dg * dg + db * db)
            if (d < best) { best = d; word = w.word }
        }
        return word
    }

    fun match(bands: List<Int>): Match {
        val i = winner(bands)
        return Match(i, nameFor(bands[i]), listOfNotNull(bands.getOrNull(i - 1), bands.getOrNull(i + 1)))
    }
}
```

- [ ] **Step 5: Run it** → PASS, 9 tests. **Step 6: Commit** — the three files are new, so `git commit -am`
  would stage nothing. `git add core/src/main/kotlin/com/burry071/evenlight/core/Vocabulary.kt
  core/src/main/kotlin/com/burry071/evenlight/core/Matcher.kt
  core/src/test/kotlin/com/burry071/evenlight/core/MatcherTest.kt` then
  `git commit -m "feat: deterministic winner band and 35-word nearest-neighbour vocabulary"`.

---

## Task 5: `Verifier` — the gate on the model's one line

**Files:** Create `core/.../Verifier.kt`; Test `core/src/test/kotlin/.../VerifierTest.kt`

**Interfaces:** Produces `sealed class Verdict { object Accept; data class Reject(val reason: String) }` and `Verifier.check(line: String, name: String): Verdict`. Consumed by Task 11.

- [ ] **Step 1: Write the failing test.** A digit is the failure that matters — it is the only way an unmeasured number could reach a card.

```kotlin
package com.burry071.evenlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifierTest {
    @Test fun acceptsALineThatCarriesTheName() {
        assertEquals(Verdict.Accept, Verifier.check("brassy light holding at the edge", "brassy"))
    }

    @Test fun rejectsAnyDigit() {
        assertTrue(Verifier.check("brassy light at 17 degrees", "brassy") is Verdict.Reject)
    }

    @Test fun rejectsAHashSoHexCannotLeakOntoACard() {
        assertTrue(Verifier.check("brassy under #c2854a", "brassy") is Verdict.Reject)
    }

    @Test fun rejectsMarkup() {
        for (s in listOf("<start> brassy", "brassy {light}", "brassy (light)")) {
            assertTrue(s, Verifier.check(s, "brassy") is Verdict.Reject)
        }
    }

    @Test fun markupIsTheReasonAndNotAnIncidentalOne() {
        // Every string in rejectsMarkup also fails a later gate ("<start> brassy" is two words), so deleting
        // the markup guard leaves them all rejected and the suite green - which would silently drop a reason
        // category from the wording_rejected/wording_total count Task 11 publishes. These two lines pass
        // every other rule, so only the guard can reject them.
        assertEquals(Verdict.Reject("markup"), Verifier.check("brassy light holding (low)", "brassy"))
        assertEquals(Verdict.Reject("markup"), Verifier.check("brassy light holding #low", "brassy"))
    }

    @Test fun rejectsMoreThanOneLine() {
        assertTrue(Verifier.check("brassy light\nand then", "brassy") is Verdict.Reject)
    }

    @Test fun rejectsMoreThanEightWords() {
        assertTrue(Verifier.check("brassy light holding at the edge of the wall tonight", "brassy") is Verdict.Reject)
    }

    @Test fun rejectsALineMissingTheGivenName() {
        assertEquals(Verdict.Reject("name absent"), Verifier.check("a quiet blue evening", "brassy"))
    }

    @Test fun rejectsFewerThanFourWords() {
        // The spec's wording task is "one lowercase line of 4-8 words". The ceiling alone would let a card
        // print a single word, so the floor is as much a contract as the ceiling - and it must be checked
        // before the name, since "brassy" on its own satisfies every other rule.
        assertEquals(Verdict.Reject("too-short"), Verifier.check("brassy light holds", "brassy"))
        assertEquals(Verdict.Accept, Verifier.check("brassy light holds low", "brassy"))
    }

    @Test fun theNameCountsAsAWordEvenWithPunctuationAttached() {
        // A 1B model writes commas and periods. Matching the name as a lowercase-letter token accepts
        // "brassy," without accepting "brassiness", so `wording_rejected` reports the model refusing the
        // contract rather than reporting a parsing artifact of ours.
        assertEquals(Verdict.Accept, Verifier.check("brassy, light holding low", "brassy"))
        assertEquals(Verdict.Reject("name absent"), Verifier.check("brassiness holds the low sky", "brassy"))
    }

    @Test fun anEmbeddedStemIsNotTheName() {
        // "brassiness" proves the suffix side. This proves the prefix side, and is the only test that dies if
        // the token match is traded for line.contains(name) - a refactor that would accept "unbrassy" as
        // carrying the word brassy.
        assertEquals(Verdict.Reject("name absent"), Verifier.check("unbrassy holds the low sky", "brassy"))
    }

    @Test fun rejectsBlankOutput() {
        assertEquals(Verdict.Reject("blank"), Verifier.check("   ", "brassy"))
    }

    @Test fun rejectsUpperCaseBecauseTheHouseStyleIsLowercase() {
        assertEquals(Verdict.Reject("not-lowercase"), Verifier.check("Brassy Light Holding Low", "brassy"))
    }
}
```

- [ ] **Step 2: Run it** → FAIL, unresolved `Verifier`.

- [ ] **Step 3: Implement.** Ordering matters: the first reason is what gets recorded and counted.

```kotlin
package com.burry071.evenlight.core

sealed class Verdict {
    object Accept : Verdict()
    data class Reject(val reason: String) : Verdict()
}

object Verifier {
    private val banned = charArrayOf('#', '<', '{', '(')

    fun check(line: String, name: String): Verdict {
        if (line.isBlank()) return Verdict.Reject("blank")
        if (line.any { it == '\n' || it == '\r' }) return Verdict.Reject("multiline")
        if (line.any { it.isDigit() }) return Verdict.Reject("digit")
        if (line.any { banned.contains(it) }) return Verdict.Reject("markup")
        if (line != line.lowercase()) return Verdict.Reject("not-lowercase")
        // Two different splits, on purpose. `words` counts whitespace runs, which is what "4-8 words" means
        // to a reader and to the prompt. `tokens` strips every non-letter, which is what makes "brassy," the
        // word brassy without letting "brassiness" pass as it. Collapsing them into one split would either
        // count "brassy," as two words or accept a stem inside a longer word.
        val words = line.trim().split(Regex("\\s+"))
        if (words.size > 8) return Verdict.Reject("too-long")
        if (words.size < 4) return Verdict.Reject("too-short")
        val tokens = line.split(Regex("[^a-z]+"))
        if (tokens.none { it == name }) return Verdict.Reject("name absent")
        return Verdict.Accept
    }
}
```

Note on what `check` does NOT do: a single trailing newline on `line` tallies as `multiline`. That is
deliberate — the guard is one character wide, and a `\n` anywhere means the model wrote more than one line.
The caller normalises instead: Task 11's `GemmaWording.line()` already returns `….trim().lowercase()`, so
surrounding whitespace never reaches `check`, while an interior newline still rejects. Trimming inside the
verifier would be worse — a whitespace-only output would slide past `blank`.

- [ ] **Step 4: Run** → PASS, 13 tests (42 in `:core`). **Step 5: Commit** `git add core/src/main/kotlin/com/burry071/evenlight/core/Verifier.kt core/src/test/kotlin/com/burry071/evenlight/core/VerifierTest.kt && git commit -m "feat: wording verifier that cannot let an unmeasured number reach a card"` (`git commit -am` stages nothing for these two untracked files).

---

## Task 6: `Json` + `EveningRecord` — the record a card can be re-derived from

**Files:** Create `core/.../Json.kt`, `core/.../EveningRecord.kt`; Test `JsonTest.kt`, `EveningRecordTest.kt`

**Interfaces:**
- Produces: `sealed class Json { Obj(fields: List<Pair<String,Json>>), Arr(items), Str(v), Num(v), Bool(v) }` with `Json.encode(j): String`, `Json.tryParse(s): Json?`, and accessors `field/str/num/int/bool/arr(name)`;
  `data class EveningRecord(date, shutter, bandsHex: List<String>, winnerIndex, name, sunAltitudeDeg, meanLuminance, wording, wordingSource, wordingRejected, attempt, clockSuspect)` with `encode(): String` and `EveningRecord.decode(text): EveningRecord?`.
- Consumed by: Task 8 (`Store`), Task 10 (Python `json.load`), Task 12 (README numbers).

- [ ] **Step 1: Write the failing tests.** Round-trip plus hostile inputs; a codec that only sees good data is untested.

```kotlin
package com.burry071.evenlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class JsonTest {
    @Test fun roundTripsEveryScalarShape() {
        val obj = Json.Obj(
            listOf(
                "a" to Json.Str("b"), "n" to Json.Num(1.5), "t" to Json.Bool(true),
                "list" to Json.Arr(listOf(Json.Str("#AABBCC"), Json.Str("#010203"))),
                "empty" to Json.Arr(emptyList())
            )
        )
        assertEquals(obj, Json.tryParse(Json.encode(obj)))
    }

    @Test fun escapesWhatWouldOtherwiseBreakAString() {
        val s = Json.Str("quote \" back\\slash newline\n tab\t")
        assertEquals(s, Json.tryParse(Json.encode(s)))
    }

    @Test fun rejectsTrailingGarbageRatherThanSilentlyIgnoringIt() { assertNull(Json.tryParse("{\"a\":1} oops")) }

    @Test fun rejectsMalformedInput() {
        assertNull(Json.tryParse("{"))
        assertNull(Json.tryParse("[1,]"))
    }

    @Test fun nullIsNotSilentlyAnEmptyString() {
        // This codec has no Json.Null case, so `null` has nowhere honest to land - and a record file holding
        // `"wording": null` must not read back as a record with an empty wording. `null` is therefore absent
        // from this grammar: it falls through to number(), consumes no characters, and throws. Python's
        // json.load reads such a file happily; the app cannot tell the two apart, so it must refuse.
        assertNull(Json.tryParse("null"))
        assertNull(Json.tryParse("{\"wording\":null}"))
    }

    @Test fun rejectsAPlusPrefixedNumber() {
        // `+1.5` is not JSON, and Python's json.load rejects it. The parser's job is to agree with the reader
        // that shares these files, so a leading plus is a malformed number, not a positive one.
        assertNull(Json.tryParse("+1.5"))
        assertEquals(Json.Num(1.5), Json.tryParse("1.5"))
    }

    @Test fun controlCharactersSurviveARoundTrip() {
        // Python's strict reader refuses a raw control character inside a string, so escaping them is what
        // keeps these files loadable. Named escapes own newline/tab/return, which leaves the four-hex-digit
        // branch otherwise unexercised. `%04x` is not locale-parameterised the way `%04d` is, so its output
        // stays a pure function of the input.
        val s = Json.Str("a\u0001b\u001Fc")
        assertEquals(s, Json.tryParse(Json.encode(s)))
        assertEquals("\"a\\u0001b\\u001fc\"", Json.encode(s))
    }

    @Test fun readsAFileWithSurroundingWhitespace() {
        // A record file another tool terminated with a newline, or padded, is still ours to read. Task 8
        // depends on this: it reads whatever came off the disk, not what the encoder wrote.
        assertNotNull(Json.tryParse(" {\"a\":1}\r\n"))
        assertEquals(Json.Obj(listOf("a" to Json.Num(1.0))), Json.tryParse("\t{\"a\":1} "))
    }

    @Test fun namedEscapesHaveAFixedWireForm() {
        // The bytes on disk are the contract with Python, so the escape spellings are pinned rather than
        // round-tripped - a round trip would still pass if a named escape quietly became a \uXXXX one.
        val odd = "q\" s\\ n\n t\t r\r"
        assertEquals("\"q\\\" s\\\\ n\\n t\\t r\\r\"", Json.encode(Json.Str(odd)))
    }

    @Test fun aNearMissLiteralIsRefused() {
        // `literal()` matches a prefix, so without the check `[tru,]` would read as `[true,]`. A truncated
        // word is corruption, not a value.
        assertNull(Json.tryParse("[tru,]"))
        assertNull(Json.tryParse("[fals,]"))
    }

    @Test fun emptyContainersParseAsEmpty() {
        // The encoder writes `[]` for an empty list; if the parser stopped accepting it, a field that went
        // empty would become an unreadable file instead of an empty list.
        assertEquals(Json.Obj(emptyList()), Json.tryParse("{}"))
        assertEquals(Json.Arr(emptyList()), Json.tryParse("[]"))
    }
}
```

```kotlin
package com.burry071.evenlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EveningRecordTest {
    private val sample = EveningRecord(
        date = "2026-10-08",
        shutter = "2026-10-08--1759",
        bandsHex = listOf("#1E2438", "#2A3350", "#3C4A67", "#5C6B82", "#8A8375", "#B08A5E", "#C2854A", "#3A322A"),
        winnerIndex = 6, name = "brassy", sunAltitudeDeg = 9, meanLuminance = 0.117,
        wording = "brassy light holding at the edge of the wall",
        wordingSource = "model", wordingRejected = false, attempt = 1, clockSuspect = false
    )

    @Test fun survivesARoundTrip() { assertEquals(sample, EveningRecord.decode(sample.encode())) }

    @Test fun bandsSurviveAsAnArrayOfEightHexStrings() {
        val o = Json.tryParse(sample.encode()) as Json.Obj
        assertEquals(sample.bandsHex, o.arr("bands")!!.map { (it as Json.Str).v })
    }

    @Test fun aTemplateSourcedRecordStillDecodes() {
        val r = EveningRecord.decode(sample.encode().replace("\"model\"", "\"template\""))
        assertEquals("template", r!!.wordingSource)
    }

    @Test fun truncatedFileDecodesToNullInsteadOfThrowing() {
        assertNull(EveningRecord.decode(sample.encode().take(40)))
    }

    @Test fun aNullFieldFailsTheDecodeRatherThanInventingAValue() {
        // A file whose `"model"` became `null` was edited by something other than this app, and decoding it
        // into a record with an invented source is the failure this codec exists to avoid. The substitution
        // targets the encoder's own output, so the test cannot pass by accident if the formatting changes.
        val corrupted = sample.encode().replace("\"wording_source\":\"model\"", "\"wording_source\":null")
        assertNotEquals(sample.encode(), corrupted)
        assertNull(EveningRecord.decode(corrupted))
    }

    @Test fun theWireFileIsExactlyWhatPythonReads() {
        // Every key spelling, the order, the absence of spaces and the integral form of `winner` are the
        // contract Task 10's Python reads. A symmetric rename of a key, or `"winner":6.0`, keeps every other
        // test here green: Python would load the file happily and then do `bands[record["winner"]]` on a
        // float, and the two languages would disagree about the same bytes forever.
        assertEquals(
            """{"date":"2026-10-08","shutter":"2026-10-08--1759","bands":["#1E2438","#2A3350","#3C4A67","#5C6B82","#8A8375","#B08A5E","#C2854A","#3A322A"],"winner":6,"name":"brassy","sun_altitude_deg":9,"mean_luminance":0.117,"wording":"brassy light holding at the edge of the wall","wording_source":"model","wording_rejected":false,"attempt":1,"clock_suspect":false}""",
            sample.encode()
        )
    }

    @Test fun aWrongTypeFieldFailsTheDecode() {
        // Absent is tolerated for two fields; present-but-wrong-type is not. A default cannot tell them apart,
        // so `{"clock_suspect":"no"}` would otherwise read back as "the clock was fine" - a corrupt record
        // inventing the trustworthy value, out of the door that Task 12 counts through.
        val w = sample.encode()
        assertNull(EveningRecord.decode(w.replace("\"clock_suspect\":false", "\"clock_suspect\":\"no\"")))
        assertNull(EveningRecord.decode(w.replace("\"attempt\":1", "\"attempt\":\"x\"")))
        assertNull(EveningRecord.decode(w.replace("\"winner\":6", "\"winner\":\"6\"")))
        assertNull(EveningRecord.decode(w.replace("\"wording_rejected\":false", "\"wording_rejected\":0")))
    }

    @Test fun aMissingFieldFailsTheDecode() {
        val w = sample.encode()
        // Every field except the two whose absence is deliberately tolerated. Each of these guards is a
        // `?: return null`; replace one with a default and this is the test that notices.
        val frags = listOf(
            "\"date\":\"2026-10-08\",", "\"shutter\":\"2026-10-08--1759\",", "\"name\":\"brassy\",",
            "\"wording\":\"brassy light holding at the edge of the wall\",",
            "\"wording_source\":\"model\",", "\"mean_luminance\":0.117,"
        )
        for (frag in frags) {
            val broken = w.replace(frag, "")
            assertNotEquals(w, broken)
            assertNull(EveningRecord.decode(broken))
        }
    }

    @Test fun bandsMustBeExactlyEightStrings() {
        val w = sample.encode()
        val seven = w.replace(",\"#3A322A\"", "")
        assertNotEquals(w, seven)
        assertNull(EveningRecord.decode(seven))
        val nine = w.replace("\"#3A322A\"", "\"#3A322A\",\"#3A322A\"")
        assertNotEquals(w, nine)
        assertNull(EveningRecord.decode(nine))
        // Not "throws": the element check is the only thing between a numeric band and a ClassCastException
        // from the map below it, and decode's whole promise is null.
        assertNull(EveningRecord.decode(w.replace("\"#1E2438\"", "123")))
        assertNull(EveningRecord.decode(w.replace("\"#1E2438\"", "true")))
    }

    @Test fun anOutOfRangeWinnerFailsTheDecode() {
        val w = sample.encode()
        assertNull(EveningRecord.decode(w.replace("\"winner\":6", "\"winner\":99")))
        assertNull(EveningRecord.decode(w.replace("\"winner\":6", "\"winner\":-1")))
        assertNull(EveningRecord.decode(w.replace("\"winner\":6", "\"winner\":6.7")))
    }

    @Test fun aRecordCarryingNoDefaultValuesRoundTrips() {
        // Every other test here uses a sample whose `attempt`, `clockSuspect` and `wordingRejected` happen to
        // equal the two defaults and `false`, so a decode that hard-coded those three reads would stay green.
        // `wording_rejected` is the honest "the model's line was refused" flag; hard-coding it would silently
        // erase every rejection from the published count.
        val r = sample.copy(
            wordingSource = "none", wordingRejected = true, attempt = 3, clockSuspect = true,
            winnerIndex = 0, sunAltitudeDeg = -4, meanLuminance = 0.0005
        )
        assertEquals(r, EveningRecord.decode(r.encode()))
    }

    @Test fun theTwoToleratedAbsencesAreDeliberate() {
        // Written before `attempt` and `clock_suspect` existed, records still have to read. Pinning the
        // tolerance says which way the defaults go: attempt 1, clock trustworthy.
        val w = sample.encode()
        val noAttempt = w.replace("\"attempt\":1,", "")
        val noClock = w.replace(",\"clock_suspect\":false", "")
        assertNotEquals(w, noAttempt)
        assertNotEquals(w, noClock)
        assertEquals(1, EveningRecord.decode(noAttempt)!!.attempt)
        assertEquals(false, EveningRecord.decode(noClock)!!.clockSuspect)
    }
}
```

- [ ] **Step 2: Run** → FAIL, unresolved `Json`.

- [ ] **Step 3: Implement `Json.kt`** — ~110 lines, zero dependencies. It exists so `:core` needs no JSON library and the app has no stubbed-`org.json` problem in unit tests.

```kotlin
package com.burry071.evenlight.core

sealed class Json {
    data class Obj(val fields: List<Pair<String, Json>>) : Json()
    data class Arr(val items: List<Json>) : Json()
    data class Str(val v: String) : Json()
    data class Num(val v: Double) : Json()
    data class Bool(val v: Boolean) : Json()

    fun field(name: String): Json? = (this as? Obj)?.fields?.firstOrNull { it.first == name }?.second
    fun str(name: String): String? = (field(name) as? Str)?.v
    fun num(name: String): Double? = (field(name) as? Num)?.v
    fun int(name: String): Int? = (field(name) as? Num)?.v?.let {
        // Integral-or-nothing, not truncating. The only callers read stored integers (`winner`,
        // `sun_altitude_deg`, `attempt`); a file holding 6.7 there is corrupt, and quietly reading it as 6
        // would turn a broken record into a plausible one. NaN falls out here too, since NaN != anything.
        if (it == it.toLong().toDouble()) it.toInt() else null
    }
    fun bool(name: String): Boolean? = (field(name) as? Bool)?.v
    fun arr(name: String): List<Json>? = (field(name) as? Arr)?.items

    companion object {
        fun encode(j: Json): String = buildString { write(j, this) }
        fun tryParse(s: String): Json? = try { Parser(s).document() } catch (e: Exception) { null }

        private fun write(j: Json, out: StringBuilder) {
            when (j) {
                is Str -> out.append('"').append(escape(j.v)).append('"')
                is Num -> out.append(if (j.v == j.v.toLong().toDouble()) j.v.toLong().toString() else j.v.toString())
                is Bool -> out.append(j.v.toString())
                is Arr -> {
                    out.append('[')
                    j.items.forEachIndexed { i, v -> if (i > 0) out.append(','); write(v, out) }
                    out.append(']')
                }
                is Obj -> {
                    out.append('{')
                    j.fields.forEachIndexed { i, (k, v) ->
                        if (i > 0) out.append(',')
                        out.append('"').append(escape(k)).append("\":")
                        write(v, out)
                    }
                    out.append('}')
                }
            }
        }

        private fun escape(s: String): String = buildString {
            for (c in s) when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                '\r' -> append("\\r")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
    }
}

private class Parser(private val s: String) {
    private var i = 0

    fun document(): Json { val v = value(); ws(); require(i == s.length) { "trailing content" }; return v }

    private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }

    private fun value(): Json {
        ws()
        require(i < s.length) { "unexpected end" }
        return when (s[i]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> Json.Str(string())
            't' -> literal("true") { Json.Bool(true) }
            'f' -> literal("false") { Json.Bool(false) }
            // No 'n' case on purpose. This codec cannot write null, so a file containing one was not written
            // by us; `null` falls through to number(), which consumes nothing and throws. See the test.
            else -> number()
        }
    }

    private fun literal(text: String, make: () -> Json): Json {
        require(s.startsWith(text, i)) { "bad literal" }
        i += text.length
        return make()
    }

    private fun number(): Json {
        val start = i
        if (i < s.length && s[i] == '-') i++
        // Without this guard the scan below happily starts on a `+`, because `+` has to be legal inside an
        // exponent (`1e+5`) - so `+1.5` would parse, while Python's json.load rejects it. A number must begin
        // with a digit or a minus, and this says so before the permissive scan runs.
        require(i < s.length && s[i].isDigit()) { "number must start with a digit" }
        while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
        return Json.Num(s.substring(start, i).toDouble())
    }

    private fun string(): String {
        require(s[i] == '"') { "expected a string" }
        i++
        val out = StringBuilder()
        while (true) {
            require(i < s.length) { "unterminated string" }
            when (val c = s[i++]) {
                '"' -> return out.toString()
                '\\' -> {
                    val e = s[i++]
                    out.append(
                        when (e) {
                            '"' -> '"'; '\\' -> '\\'; '/' -> '/'; 'b' -> '\b'; 'f' -> '\u000C'
                            'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
                            'u' -> { val h = s.substring(i, i + 4); i += 4; h.toInt(16).toChar() }
                            else -> throw IllegalArgumentException("bad escape")
                        }
                    )
                }
                else -> out.append(c)
            }
        }
    }

    private fun arr(): Json {
        i++
        val items = ArrayList<Json>()
        ws()
        if (i < s.length && s[i] == ']') { i++; return Json.Arr(items) }
        while (true) {
            items += value()
            ws()
            require(i < s.length) { "unterminated array" }
            when (s[i++]) {
                ',' -> continue
                ']' -> return Json.Arr(items)
                else -> throw IllegalArgumentException("expected , or ]")
            }
        }
    }

    private fun obj(): Json {
        i++
        val fields = ArrayList<Pair<String, Json>>()
        ws()
        if (i < s.length && s[i] == '}') { i++; return Json.Obj(fields) }
        while (true) {
            ws()
            val k = string()
            ws()
            require(i < s.length && s[i] == ':') { "expected :" }
            i++
            fields += k to value()
            ws()
            require(i < s.length) { "unterminated object" }
            when (s[i++]) {
                ',' -> continue
                '}' -> return Json.Obj(fields)
                else -> throw IllegalArgumentException("expected , or }")
            }
        }
    }
}
```

- [ ] **Step 4: Implement `EveningRecord.kt`.**

```kotlin
package com.burry071.evenlight.core

/**
 * One evening. Written before the card is rendered, so a render failure cannot lose the
 * observation. Python reads the same file with json.load.
 */
data class EveningRecord(
    val date: String,
    val shutter: String,
    val bandsHex: List<String>,
    val winnerIndex: Int,
    val name: String,
    val sunAltitudeDeg: Int,
    val meanLuminance: Double,
    val wording: String,
    val wordingSource: String,      // "model" | "template" | "none"
    val wordingRejected: Boolean,
    val attempt: Int,
    val clockSuspect: Boolean
) {
    fun encode(): String = Json.encode(
        Json.Obj(
            listOf(
                "date" to Json.Str(date),
                "shutter" to Json.Str(shutter),
                "bands" to Json.Arr(bandsHex.map { Json.Str(it) }),
                "winner" to Json.Num(winnerIndex.toDouble()),
                "name" to Json.Str(name),
                "sun_altitude_deg" to Json.Num(sunAltitudeDeg.toDouble()),
                "mean_luminance" to Json.Num(meanLuminance),
                "wording" to Json.Str(wording),
                "wording_source" to Json.Str(wordingSource),
                "wording_rejected" to Json.Bool(wordingRejected),
                "attempt" to Json.Num(attempt.toDouble()),
                "clock_suspect" to Json.Bool(clockSuspect)
            )
        )
    )

    companion object {
        /**
         * Null for anything this app did not write. Two absences are tolerated - `attempt` and
         * `clock_suspect` - because records written before those fields existed are otherwise unreadable; a
         * field that is PRESENT but the wrong type is not, because inventing a value for it would produce a
         * plausible record out of a corrupt file. That distinction is what `field(x) == null` is for below.
         */
        fun decode(text: String): EveningRecord? {
            val o = Json.tryParse(text) as? Json.Obj ?: return null
            val bands = o.arr("bands") ?: return null
            if (bands.size != 8 || bands.any { it !is Json.Str }) return null
            val winnerIndex = o.int("winner") ?: return null
            if (winnerIndex !in bands.indices) return null
            return EveningRecord(
                date = o.str("date") ?: return null,
                shutter = o.str("shutter") ?: return null,
                bandsHex = bands.map { (it as Json.Str).v },
                winnerIndex = winnerIndex,
                name = o.str("name") ?: return null,
                sunAltitudeDeg = o.int("sun_altitude_deg") ?: return null,
                meanLuminance = o.num("mean_luminance") ?: return null,
                wording = o.str("wording") ?: return null,
                wordingSource = o.str("wording_source") ?: return null,
                wordingRejected = o.bool("wording_rejected") ?: return null,
                attempt = if (o.field("attempt") == null) 1 else o.int("attempt") ?: return null,
                clockSuspect = if (o.field("clock_suspect") == null) false else o.bool("clock_suspect") ?: return null
            )
        }
    }
}
```

- [ ] **Step 5: Run both classes** (`--tests "*JsonTest*" --tests "*EveningRecordTest*"`) → PASS, 23 tests (11 + 12), 65 in `:core`. **Step 6: Commit** `git add core/src/main/kotlin/com/burry071/evenlight/core/Json.kt core/src/main/kotlin/com/burry071/evenlight/core/EveningRecord.kt core/src/test/kotlin/com/burry071/evenlight/core/JsonTest.kt core/src/test/kotlin/com/burry071/evenlight/core/EveningRecordTest.kt && git commit -m "feat: dependency-free JSON codec and the evening record"` (`git commit -am` stages nothing for four untracked files).

---

## Task 7: `CardGeometry` + `CardRenderer` — the artefact

**Files:** Create `core/.../CardGeometry.kt`, `app/src/main/java/com/burry071/evenlight/card/CardRenderer.kt`; Test `core/src/test/kotlin/.../CardGeometryTest.kt`

**Interfaces:**
- Produces: `object Palette` (packed ARGB ints, listed in Global Constraints, plus `opaque(c): Int` — the one
  crossing from the measurement packing `0x00RRGGBB` to the drawing packing `0xAARRGGBB`);
  `data class CardGeometry(val width: Int = 1080, val height: Int = 1440)` with `fieldHeight: Int`, `paperTop: Int`, `bandHeight: Double`, `stripHeight: Int`, `stripTop: Int`, `bandCentreY(i): Double`, `fieldBlend(y: Int): Float`, `inkOn(band: Int): Int`, `blendOf(bands: List<Int>, blend: Float): Int`;
  `class CardRenderer(displayFont: Typeface, textFont: Typeface) { fun render(bands: List<Int>, header: Header): Bitmap }` with `data class Header(name, dateLabel, sunAltitudeDeg, wording, winnerIndex: Int, neighbours: List<Int>, night: Boolean)`.
- Consumed by: Tasks 8, 9, 10 (Python mirrors the same numbers).

- [ ] **Step 1: Write the failing test.** The colour rules are asserted here because they are the ones that could silently fake a hue.

```kotlin
package com.burry071.evenlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardGeometryTest {
    private val g = CardGeometry()

    @Test fun fieldTakesSixtyTwoPercentOfTheCard() {
        assertEquals(893, g.fieldHeight)
        assertEquals(893, g.paperTop)
    }

    @Test fun bandCentresAreMonotonicAndInsideTheField() {
        val centres = (0 until 8).map { g.bandCentreY(it) }
        assertTrue(centres.zipWithNext().all { (a, b) -> b > a })
        assertTrue(centres.first() >= 0.0 && centres.last() < g.fieldHeight)
    }

    @Test fun aboveTheFirstCentreIsPurelyBandZero() {
        assertEquals(0f, g.fieldBlend(0), 1e-6f)
        assertEquals(0f, g.fieldBlend(40), 1e-6f)
    }

    @Test fun belowTheLastCentreIsPurelyBandSeven() {
        assertEquals(7f, g.fieldBlend(g.fieldHeight - 1), 1e-6f)
    }

    @Test fun halfwayBetweenTwoCentresIsAnEvenBlend() {
        val mid = ((g.bandCentreY(2) + g.bandCentreY(3)) / 2).toInt()
        assertEquals(2.5f, g.fieldBlend(mid), 0.02f)
    }

    @Test fun blendingTwoBandsAveragesTheChannelsAndRoundsHalfUp() {
        val b = MutableList(8) { Pixels.rgb(0, 0, 0) }.also { it[1] = Pixels.rgb(200, 100, 40) }
        assertEquals(Palette.opaque(Pixels.rgb(0, 0, 0)), g.blendOf(b, 0f))
        assertEquals(Palette.opaque(Pixels.rgb(200, 100, 40)), g.blendOf(b, 1f))
        assertEquals(Palette.opaque(Pixels.rgb(100, 50, 20)), g.blendOf(b, 0.5f))
        // The three cases above average to exact integers, so none of them exercises a rounding rule. Odd
        // channels land the average on exactly .5, which is where Kotlin and Python can part company: half-up
        // gives 101/51/21, banker's rounding gives 100/50/20. Task 10 mirrors this line in Python, so the tie
        // direction is cross-language contract, not local style.
        val odd = MutableList(8) { Pixels.rgb(0, 0, 0) }.also { it[1] = Pixels.rgb(201, 101, 41) }
        assertEquals(Palette.opaque(Pixels.rgb(101, 51, 21)), g.blendOf(odd, 0.5f))
    }

    @Test fun everyColourTheRendererIsGivenIsOpaque() {
        // BandSampler (T3) packs a measured band as 0x00RRGGBB, and that packing is the record's and Python's.
        // Canvas and Compose read the high byte as alpha, so drawing one of those ints paints nothing: the
        // field, the winner strip and the neighbour chips all vanish, and no int-equality test here can see it
        // because none of them asks which byte is which. Palette.opaque is the single crossing point, so the
        // alpha claim is checked where the crossing happens - and the first assertion checks the trap is real.
        fun alpha(c: Int) = (c ushr 24) and 0xFF
        val bands = List(8) { Pixels.rgb(201, 101, 41) }
        assertEquals(0, alpha(bands[0]))
        for (t in floatArrayOf(0f, 0.5f, 2.7f, 7f)) assertEquals(0xFF, alpha(g.blendOf(bands, t)))
        assertEquals(0xFF, alpha(g.inkOn(bands[0])))
        assertEquals(0xFF, alpha(Palette.opaque(bands[0])))
        // Promoting must not shift or clip a channel, or the strip stops being the measured colour.
        assertEquals(bands[0], Palette.opaque(bands[0]) and 0x00FFFFFF)
    }

    @Test fun aDarkWinnerStripCarriesPaper() {
        assertEquals(Palette.PAPER, g.inkOn(Pixels.parseHex("#C2854A")))   // luminance 0.287
    }

    @Test fun aPaleWinnerStripCarriesInk() {
        assertEquals(Palette.INK, g.inkOn(Pixels.parseHex("#E3DED4")))
    }

    @Test fun theThresholdSitsAtFortyFivePercentLuminance() {
        val dark = Pixels.rgb(180, 150, 120)
        val pale = Pixels.rgb(210, 210, 205)
        assertTrue(Pixels.relativeLuminance(dark) < 0.45)
        assertTrue(Pixels.relativeLuminance(pale) > 0.45)
        assertEquals(Palette.PAPER, g.inkOn(dark))
        assertEquals(Palette.INK, g.inkOn(pale))
    }

    @Test fun aSmallerCardKeepsTheSameProportions() {
        assertEquals(446, CardGeometry(540, 720).fieldHeight)   // 0.62 * 720 = 446.4 -> half-up
    }

    @Test fun theCardIsTheArtefactSizeAndTheFieldIsEightBands() {
        // 1080x1440 is the exported artefact, and `bandHeight` is what every centre and blend is built from.
        // Neither is asserted anywhere else, so a default changed in either direction reads as a style tweak.
        assertEquals(1080, g.width)
        assertEquals(1440, g.height)
        assertEquals(893 / 8.0, g.bandHeight, 1e-9)
    }

    @Test fun theWinnerStripIsTheBottomOfTheField() {
        // 96 px at a 1440 card is the mockup's number, and the strip is where a measured colour is shown
        // unmixed - so its geometry is a colour-honesty fact, not a layout nicety. Nothing else pins it.
        assertEquals(96, g.stripHeight)
        assertEquals(797, g.stripTop)
        assertEquals(g.fieldHeight - g.stripHeight, g.stripTop)
    }

    @Test fun theInkThresholdIsExactlyWhereTheDesignSays() {
        // The two colours in the tests above sit at 0.287 and 0.73 luminance, so any threshold between them
        // passes - which means 0.45 itself is unpoliced. Grey 178 is 0.4452 and grey 179 is 0.4508, so these
        // four lines pin the boundary to within one grey level.
        assertTrue(Pixels.relativeLuminance(Pixels.rgb(178, 178, 178)) < 0.45)
        assertTrue(Pixels.relativeLuminance(Pixels.rgb(179, 179, 179)) > 0.45)
        assertEquals(Palette.PAPER, g.inkOn(Pixels.rgb(178, 178, 178)))
        assertEquals(Palette.INK, g.inkOn(Pixels.rgb(179, 179, 179)))
    }

    @Test fun thePaletteIsTheDesignSystemVerbatim() {
        // The app's chrome reads these constants, not the hex literals in the design system, so a mistyped
        // digit here is invisible to every other test - and a brand hue leaking into chrome is the failure
        // the spec forbids. Pinned as packed ARGB, including the alpha, because 0xF4EFE6 would compile too.
        assertEquals(0xFFF4EFE6.toInt(), Palette.PAPER)
        assertEquals(0xFFFBF8F2.toInt(), Palette.CARD)
        assertEquals(0xFF2A2622.toInt(), Palette.INK)
        assertEquals(0xFF6E6656.toInt(), Palette.MUTED)
        assertEquals(0xFFD9D0BF.toInt(), Palette.HAIRLINE)
        assertEquals(0xFF17150F.toInt(), Palette.NIGHT_SURFACE)
        assertEquals(0xFFE9E3D6.toInt(), Palette.NIGHT_TEXT)
        assertEquals(0xFF332F26.toInt(), Palette.NIGHT_HAIRLINE)
    }
}
```

- [ ] **Step 2: Run** → FAIL, unresolved `CardGeometry`.

- [ ] **Step 3: Implement `CardGeometry.kt`.** Keep all eight constants: `HAIRLINE` is the only one this task's renderer does not read, and it is consumed by Task 9's Compose theme (`Color(Palette.HAIRLINE)`), so "unused here" is not dead code and deleting it would break that task's build.

```kotlin
package com.burry071.evenlight.core

import kotlin.math.max
import kotlin.math.min

/** The only colours allowed on chrome. Every saturated pixel on a card is a measured band. */
object Palette {
    const val PAPER = 0xFFF4EFE6.toInt()
    const val CARD = 0xFFFBF8F2.toInt()
    const val INK = 0xFF2A2622.toInt()
    const val MUTED = 0xFF6E6656.toInt()
    const val HAIRLINE = 0xFFD9D0BF.toInt()
    const val NIGHT_SURFACE = 0xFF17150F.toInt()
    const val NIGHT_TEXT = 0xFFE9E3D6.toInt()
    const val NIGHT_HAIRLINE = 0xFF332F26.toInt()

    /**
     * The one place the measurement domain becomes the drawing domain. `Pixels` packs a measured colour as
     * 0x00RRGGBB (T3) because that is what the record stores and what Python reads, while `Canvas` and
     * Compose read the high byte as alpha - so a measured colour drawn as-is paints nothing. Every palette
     * constant above is already opaque; this is how a band becomes drawable without touching a channel.
     */
    fun opaque(c: Int): Int = (0xFF000000.toInt()) or (c and 0x00FFFFFF)
}

/**
 * Card layout as pure numbers, so the Android renderer and the Python reference tool cannot
 * drift. The field is a linear blend between band centres; that blend is the ONLY permitted
 * transformation of a measured colour, and it is defined here rather than by a shader.
 */
data class CardGeometry(val width: Int = 1080, val height: Int = 1440) {
    val fieldHeight: Int = Pixels.roundHalfUp(height * 0.62)
    val paperTop: Int get() = fieldHeight
    val bandHeight: Double get() = fieldHeight / 8.0
    val stripHeight: Int = Pixels.roundHalfUp(height * 0.0667)
    val stripTop: Int get() = fieldHeight - stripHeight

    fun bandCentreY(i: Int): Double = (i + 0.5) * bandHeight

    /** Integer part = the lower band index; fraction = how far toward the next one. */
    fun fieldBlend(y: Int): Float {
        if (y <= bandCentreY(0)) return 0f
        if (y >= bandCentreY(7)) return 7f
        for (i in 0 until 7) {
            val a = bandCentreY(i); val b = bandCentreY(i + 1)
            if (y < b) return (i + (y - a) / (b - a)).toFloat()
        }
        return 7f
    }

    /**
     * Text colour for the winner strip: paper on a dark band, ink on a pale one, so the measured
     * hue is displayed untouched. Clears WCAG 3:1 for large text but not 4.5:1 for body, so the
     * strip carries display type only - never body copy.
     */
    fun inkOn(band: Int): Int = if (Pixels.relativeLuminance(band) < 0.45) Palette.PAPER else Palette.INK

    fun blendOf(bands: List<Int>, blend: Float): Int {
        val i = max(0, min(blend.toInt(), bands.size - 2))
        val t = (blend - i).coerceIn(0f, 1f)
        val a = bands[i]; val b = bands[i + 1]
        fun mix(shift: Int) = Pixels.roundHalfUp(
            (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toDouble()
        ).coerceIn(0, 255)
        // Alpha is asserted here rather than left to the caller: the field is 893 drawn lines, and a blend that
        // returned 0x00RRGGBB would make the whole card invisible while every int comparison here still passed.
        return Palette.opaque(Pixels.rgb(mix(16), mix(8), mix(0)))
    }
}
```

- [ ] **Step 4: Run** → PASS, 15 tests (`:core` total 80).

- [ ] **Step 5: Implement `CardRenderer.kt`.** It draws; it does not decide. There is no saturation, contrast, tint, or white-balance call in this file, and adding one breaks the project's premise.

```kotlin
package com.burry071.evenlight.card

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.burry071.evenlight.core.CardGeometry
import com.burry071.evenlight.core.Palette

class CardRenderer(private val displayFont: Typeface, private val textFont: Typeface) {

    data class Header(
        val name: String,
        val dateLabel: String,
        val sunAltitudeDeg: Int,
        val wording: String,
        val winnerIndex: Int,
        val neighbours: List<Int>,
        val night: Boolean = false
    )

    private val geo = CardGeometry()

    fun render(bands: List<Int>, header: Header): Bitmap {
        val bmp = Bitmap.createBitmap(geo.width, geo.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val paper = if (header.night) Palette.NIGHT_SURFACE else Palette.CARD
        val text = if (header.night) Palette.NIGHT_TEXT else Palette.INK
        val quiet = if (header.night) Palette.NIGHT_HAIRLINE else Palette.MUTED
        c.drawColor(paper)

        for (y in 0 until geo.fieldHeight) {
            p.color = geo.blendOf(bands, geo.fieldBlend(y))
            c.drawLine(0f, y.toFloat(), geo.width.toFloat(), y.toFloat(), p)
        }

        // The winner comes in on the header rather than being recomputed here, because the record stores
        // `winner` and Task 10 re-derives cards in Python from that stored field. A renderer that decided for
        // itself could draw a strip that disagrees with the record it was built from - which is the one drift
        // this file exists to prevent, and "it draws, it does not decide" means exactly this line.
        val winner = Palette.opaque(bands[header.winnerIndex])
        p.color = winner
        c.drawRect(0f, geo.stripTop.toFloat(), geo.width.toFloat(), geo.fieldHeight.toFloat(), p)
        p.color = geo.inkOn(winner)
        p.typeface = displayFont
        p.textSize = 116f
        c.drawText(header.name, 56f, geo.stripTop + geo.stripHeight * 0.72f, p)

        p.color = text; p.typeface = displayFont; p.textSize = 62f
        c.drawText(header.dateLabel, 56f, geo.paperTop + 120f, p)
        p.color = quiet; p.typeface = textFont; p.textSize = 40f
        c.drawText("sun ${header.sunAltitudeDeg}\u00B0", 56f, geo.paperTop + 186f, p)

        var x = geo.width - 56f - 60f * header.neighbours.size
        for (n in header.neighbours) {
            p.color = Palette.opaque(n)          // measured anchors arrive as 0x00RRGGBB (T3 packing)
            c.drawRoundRect(x, geo.paperTop + 150f, x + 46f, geo.paperTop + 196f, 8f, 8f, p)
            x += 60f
        }

        p.color = text; p.typeface = textFont; p.textSize = 44f
        wrap(c, p, header.wording, 56f, geo.paperTop + 290f, geo.width - 112f, 60f)

        p.color = quiet; p.textSize = 32f
        c.drawText("measured, not adjusted \u00B7 8 bands", 56f, geo.height - 56f, p)
        return bmp
    }

    private fun wrap(c: Canvas, p: Paint, text: String, x: Float, y0: Float, maxW: Float, lineH: Float) {
        var y = y0
        var line = ""
        for (word in text.split(' ')) {
            val trial = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(trial) > maxW) { c.drawText(line, x, y, p); y += lineH; line = word }
            else line = trial
        }
        if (line.isNotEmpty()) c.drawText(line, x, y, p)
    }
}
```

- [ ] **Step 6: Do not fake a unit test for this.** `Bitmap`/`Canvas` have no JVM implementation; the render path is verified by the scripted emulator pass in Task 8 step 7, where the PNG is pulled and looked at.

- [ ] **Step 7: Commit** with explicit paths — `git add core/src/main/kotlin/com/burry071/evenlight/core/CardGeometry.kt core/src/test/kotlin/com/burry071/evenlight/core/CardGeometryTest.kt app/src/main/java/com/burry071/evenlight/card/CardRenderer.kt && git commit -m "feat: card geometry as pure numbers plus the Canvas renderer"`. Do not use `git commit -am` here: all three files are new, and `-a` only stages modifications to already-tracked paths, so it would commit nothing and report success.

---

## Task 8: Capture → sample → match → render → file

This task must land tonight; every day of slippage costs the record an evening.

**Files:** Create `app/.../data/{Store,Settings}.kt`, `app/.../capture/FrameSource.kt`, `app/.../EvenlightController.kt`; Test `app/src/test/java/com/burry071/evenlight/StoreTest.kt`

**Interfaces:**
- Consumes: `BandSampler.sample` (T3), `Matcher.match` (T4), `CardRenderer.render` (T7), `SunWindow` (T2), `EveningRecord` (T6).
- Produces:
  - `class Store(root: File)` with `root: File`, `nextShutterName(now: LocalDateTime): String`, `attemptsFor(date: String): Int`, `writeRaw(jpeg: ByteArray, shutter: String): File`, `recordFile(date: String, attempt: Int): File`, `writeRecord(r: EveningRecord, attempt: Int): File`, `readRecords(): List<EveningRecord>`, `corruptRecords(): Int`, `eveningsWithCards(): Set<String>`, `cardFile(date: String): File`, `writeCard(date: String, png: ByteArray): File`, `wordingTotal(): Int`, `wordingRejected(): Int`, `loadSettings(): Settings`, `saveSettings(s: Settings)`
  - `data class Settings(val place: String, val lat: Double, val lon: Double) { val isSet: Boolean }` with `Settings.UNSET`
  - `class FrameSource(context: Context, executor: Executor) { var imageCapture: ImageCapture?; fun start(provider, previewView, lifecycleOwner); suspend fun take(rotationDegrees: Int): Still }`, `data class Still(val pixels: IntArray, val width: Int, val height: Int, val jpeg: ByteArray)`
  - `class EvenlightController { suspend fun shoot(): Result<EveningRecord> }`

- [ ] **Step 1: Write the failing test** for the naming and gap rules — the two things that decide whether the stack can show a hole.

```kotlin
package com.burry071.evenlight

import com.burry071.evenlight.core.EveningRecord
import com.burry071.evenlight.data.Store
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreTest {
    private fun temp(): Pair<Store, File> {
        val dir = File(System.getProperty("java.io.tmpdir"), "evenlight-test-" + System.nanoTime())
        return Store(dir) to dir
    }

    private fun record(date: String) = EveningRecord(
        date, "$date--1759", List(8) { "#000000" }, 0, "low", 9, 0.0,
        "low light, measured from this sky", "template", false, 1, false
    )

    @Test fun shutterNameIsDateAndTimeToTheMinute() {
        val (store, dir) = temp()
        assertEquals("2026-10-08--1759", store.nextShutterName(LocalDateTime.of(2026, 10, 8, 17, 59, 33)))
        dir.deleteRecursively()
    }

    @Test fun aSecondShotTheSameEveningIsAttemptTwo() {
        val (store, dir) = temp()
        store.writeRecord(record("2026-10-08"), 1)
        assertEquals(1, store.attemptsFor("2026-10-08"))
        store.writeRecord(record("2026-10-08"), 2)
        assertEquals(2, store.attemptsFor("2026-10-08"))
        dir.deleteRecursively()
    }

    @Test fun anEveningWithNoRecordIsNotAnEveningWithACard() {
        val (store, dir) = temp()
        assertFalse(store.eveningsWithCards().contains("2026-10-07"))
        dir.deleteRecursively()
    }

    @Test fun corruptRecordsAreCountedNotDeleted() {
        val (store, dir) = temp()
        store.writeRecord(record("2026-10-08"), 1)
        val bad = File(dir, "evenlight/records/2026-10-06.json").apply { writeText("{ truncated") }
        val ok = store.readRecords()
        assertEquals(1, ok.size)
        assertEquals("2026-10-08", ok.first().date)
        assertEquals(1, store.corruptRecords())
        assertTrue("a parse failure must not delete evidence", bad.exists())
        dir.deleteRecursively()
    }

    @Test fun settingsRoundTripKeepsTwoDecimalCoordinates() {
        val (store, dir) = temp()
        store.saveSettings(com.burry071.evenlight.data.Settings("hostel balcony", 31.52, 74.35))
        assertEquals(com.burry071.evenlight.data.Settings("hostel balcony", 31.52, 74.35), store.loadSettings())
        dir.deleteRecursively()
    }
}
```

- [ ] **Step 2: Run** → FAIL, unresolved `Store`.

- [ ] **Step 3: Implement `Store.kt` + `Settings.kt`.** Plain files under `filesDir/evenlight`, no database, nothing that is not readable in a text editor.

```kotlin
package com.burry071.evenlight.data

import com.burry071.evenlight.core.EveningRecord
import com.burry071.evenlight.core.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Store(root: File) {
    private val base = File(root, "evenlight").apply { mkdirs() }
    private val raw = File(base, "raw").apply { mkdirs() }
    private val cards = File(base, "cards").apply { mkdirs() }
    private val records = File(base, "records").apply { mkdirs() }
    private val settingsFile = File(base, "settings.json")

    fun nextShutterName(now: LocalDateTime): String = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd--HHmm"))

    fun attemptsFor(date: String): Int =
        records.listFiles()?.count { it.name == "$date.json" || it.name.startsWith("$date-attempt-") } ?: 0

    fun writeRaw(jpeg: ByteArray, shutter: String): File = File(raw, "$shutter.jpg").apply { writeBytes(jpeg) }

    fun recordFile(date: String, attempt: Int): File =
        if (attempt <= 1) File(records, "$date.json") else File(records, "$date-attempt-$attempt.json")

    fun writeRecord(r: EveningRecord, attempt: Int): File = recordFile(r.date, attempt).apply { writeText(r.encode()) }

    fun readRecords(): List<EveningRecord> =
        records.listFiles()?.sortedBy { it.name }?.mapNotNull { EveningRecord.decode(it.readText()) } ?: emptyList()

    fun corruptRecords(): Int = records.listFiles()?.count {
        it.name.endsWith(".json") && EveningRecord.decode(it.readText()) == null
    } ?: 0

    fun cardFile(date: String): File = File(cards, "$date.png")
    fun writeCard(date: String, png: ByteArray): File = cardFile(date).apply { writeBytes(png) }
    fun eveningsWithCards(): Set<String> = cards.listFiles()?.map { it.nameWithoutExtension }?.toSet() ?: emptySet()

    fun wordingTotal(): Int = readRecords().size
    fun wordingRejected(): Int = readRecords().count { it.wordingRejected }

    fun loadSettings(): Settings {
        val o = Json.tryParse(settingsFile.runCatching { readText() }.getOrNull() ?: return Settings.UNSET) as? Json.Obj
            ?: return Settings.UNSET
        return Settings(o.str("place") ?: "", o.num("lat") ?: 0.0, o.num("lon") ?: 0.0)
    }

    fun saveSettings(s: Settings) {
        settingsFile.writeText(
            Json.encode(Json.Obj(listOf("place" to Json.Str(s.place), "lat" to Json.Num(s.lat), "lon" to Json.Num(s.lon))))
        )
    }
}
```

```kotlin
package com.burry071.evenlight.data

data class Settings(val place: String, val lat: Double, val lon: Double) {
    val isSet: Boolean get() = lat != 0.0 || lon != 0.0
    companion object { val UNSET = Settings("", 0.0, 0.0) }
}
```

- [ ] **Step 4: Run** → PASS, 5 tests.

- [ ] **Step 5: Implement `FrameSource.kt`.** CameraX, back camera, one still, rotation applied by hand so `:core` never sees an Android image type. `BitmapFactory` does **not** apply EXIF, so rotate explicitly.

```kotlin
package com.burry071.evenlight.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class Still(val pixels: IntArray, val width: Int, val height: Int, val jpeg: ByteArray)

class FrameSource(private val context: Context, private val executor: Executor) {

    var imageCapture: ImageCapture? = null
        private set

    fun start(provider: ProcessCameraProvider, previewView: PreviewView, owner: LifecycleOwner) {
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .build()
        val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
        provider.unbindAll()
        provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
    }

    suspend fun take(rotationDegrees: Int): Still {
        val capture = imageCapture ?: return Still(IntArray(0), 0, 0, ByteArray(0))
        val target = File(context.cacheDir, "shot.jpg")
        val jpeg = suspendCoroutine { cont ->
            val options = ImageCapture.OutputFileOptions.Builder(target).build()
            capture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(o: ImageCapture.OutputFileResults) {
                    cont.resume(if (target.exists()) target.readBytes() else ByteArray(0))
                }
                override fun onError(exc: ImageCaptureException) { cont.resume(ByteArray(0)) }
            })
        }
        if (jpeg.isEmpty()) return Still(IntArray(0), 0, 0, jpeg)
        return decode(jpeg, rotationDegrees)
    }

    companion object {
        fun decode(jpeg: ByteArray, rotationDegrees: Int): Still {
            var bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return Still(IntArray(0), 0, 0, jpeg)
            if (rotationDegrees != 0) {
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true)
            }
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            val w = bmp.width; val h = bmp.height
            bmp.recycle()
            return Still(px, w, h, jpeg)
        }

        /** Display rotation -> the degrees the decoded bitmap must be turned to stand up. */
        fun rotationFor(displayRotation: Int): Int = when (displayRotation) {
            0 -> 90; 1 -> 0; 2 -> 180; else -> 270
        }
    }
}
```

- [ ] **Step 6: Implement `EvenlightController.shoot()`** — the whole nightly path in one readable function.

```kotlin
    suspend fun shoot(): Result<EveningRecord> = runCatching {
        val now = LocalDateTime.now()
        val bootMark = SystemClock.elapsedRealtime()
        val still = frame.take(FrameSource.rotationFor(previewRotation()))
        require(still.pixels.isNotEmpty()) { "capture produced no pixels" }

        val bands = BandSampler.sample(still.pixels, still.width, still.height)
        val match = Matcher.match(bands.ordered)
        val geoPoint = GeoPoint(settings.lat, settings.lon)
        val altitude = SunWindow.altitudeForDisplay(
            now.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(), geoPoint
        )
        val date = now.toLocalDate().toString()
        val attempt = store.attemptsFor(date) + 1

        val record = EveningRecord(
            date = date,
            shutter = store.nextShutterName(now),
            bandsHex = bands.ordered.map { Pixels.hex(it) },
            winnerIndex = match.winnerIndex,
            name = match.name,
            sunAltitudeDeg = altitude,
            meanLuminance = bands.meanLuminance,
            wording = Wording.template(match.name),        // Task 11 replaces this with the model line
            wordingSource = "none",
            wordingRejected = false,
            attempt = attempt,
            clockSuspect = clockDrifted(now, bootMark)
        )
        store.writeRaw(still.jpeg, record.shutter)
        store.writeRecord(record, attempt)                 // record BEFORE render: a render failure must not lose the evening
        store.writeCard(date, renderer.render(bands.ordered, headerFor(record, match)).pngBytes())
        record
    }
```

`clockDrifted` compares wall-clock advance against `elapsedRealtime` since boot; a backwards jump or a timezone edit marks the row rather than silently reordering the stack (spec §11). `pngBytes()` is `Bitmap.compress(PNG, 100, ByteArrayOutputStream)` — quality is irrelevant for PNG and 100 makes that explicit.

- [ ] **Step 7: Scripted emulator pass.** Grant camera, open, tap Shoot, then:

```bash
MSYS_NO_PATHCONV=1 adb shell run-as com.burry071.evenlight ls -l files/evenlight/records
MSYS_NO_PATHCONV=1 adb shell run-as com.burry071.evenlight cat files/evenlight/records/$(date +%F).json
MSYS_NO_PATHCONV=1 adb pull /data/data/com.burry071.evenlight/files/evenlight/cards /d/Temp/cards
```
Expected: one JSON with 8 hex bands and a plausible `name`, and one 1080×1440 PNG that is **not sideways** (if it is, `rotationFor` is wrong for this device — fix it here, not later) and whose bands resemble what the lens saw.

- [ ] **Step 8: Shoot tonight's actual sky on the user's phone.** That card is the first datum of the record and no test substitutes for it.

- [ ] **Step 9: Commit** `git commit -am "feat: capture to card, with the record written before the render"`.

---

## Task 9: The four screens, in the approved design system

`design/evenlight-mockups.html` is the visual source of truth for spacing, weights, and the night theme. This task is transcription, not invention — if a screen and the mockup disagree, the mockup wins unless the difference is a measured colour.

**Files:** Create `app/.../ui/theme/{Color,Type,Theme}.kt`, `ui/FramingGuide.kt`, `ui/{Tonight,Shoot,Card,Stack,Setup}Screen.kt`, `ui/EvenlightNav.kt`; Modify `MainActivity.kt`; Create `app/src/main/res/font/{fraunces_variable,instrument_sans_variable}.ttf` and `LICENSES/`

**Interfaces:**
- Consumes: `EvenlightController`, `Store`, `SunWindow`, `Palette`, `CardGeometry`.
- Produces: `object EvenlightColors`, `object EvenlightType`, `@Composable EvenlightTheme(dark: Boolean, content: @Composable () -> Unit)`, `@Composable FramingGuide(modifier: Modifier)`.

- [ ] **Step 1: Ask, then bundle the fonts.** They must ship in the APK because the app has no network. Both are SIL OFL, so bundling is permitted — but this adds third-party content to a repo that will be published, so confirm first.

```bash
mkdir -p app/src/main/res/font LICENSES
curl -L -o app/src/main/res/font/fraunces_variable.ttf \
  "https://github.com/google/fonts/raw/main/ofl/fraunces/Fraunces%5BSOFT%2CWONK%2Copsz%2Cwght%5D.ttf"
curl -L -o app/src/main/res/font/instrument_sans_variable.ttf \
  "https://github.com/google/fonts/raw/main/ofl/instrumentsans/InstrumentSans%5Bwdth%2Cwght%5D.ttf"
curl -L -o LICENSES/Fraunces.txt "https://github.com/google/fonts/raw/main/ofl/fraunces/OFL.txt"
curl -L -o LICENSES/InstrumentSans.txt "https://github.com/google/fonts/raw/main/ofl/instrumentsans/OFL.txt"
ls -l app/src/main/res/font
```
Expected: two TTFs, a few hundred KB each. If a URL 404s, **stop and say so** rather than substituting a different family — the type is the design.

- [ ] **Step 2: Theme from `Palette`, so no new colour can appear.**

```kotlin
package com.burry071.evenlight.ui.theme

import androidx.compose.ui.graphics.Color
import com.burry071.evenlight.core.Palette

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
```

A `Color(0x...)` literal in any screen file is a brand hue and fails review.

```kotlin
package com.burry071.evenlight.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.burry071.evenlight.R

private val Display = FontFamily(Font(R.font.fraunces_variable))
private val Body = FontFamily(Font(R.font.instrument_sans_variable))

object EvenlightType {
    val hero = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 44.sp,
        lineHeight = 46.sp, letterSpacing = (-0.028).em,
        fontVariationSettings = "'opsz' 72, 'SOFT' 60, 'WONK' 1"
    )
    val title = TextStyle(Display, FontWeight.Normal, 24.sp,
        fontVariationSettings = "'opsz' 40, 'SOFT' 60, 'WONK' 1")
    val strip = TextStyle(Display, FontWeight.Normal, 29.sp,
        fontVariationSettings = "'opsz' 48, 'SOFT' 70, 'WONK' 1")
    val body = TextStyle(Body, FontWeight.Normal, 15.sp, lineHeight = 22.sp)
    val caption = TextStyle(Body, FontWeight.Medium, 11.sp, letterSpacing = 0.09.em)
}
```

- [ ] **Step 3: Tonight.** One line and one number: `in 14 min` in `hero`, `sunset 17:40 · evening 4` in `caption`, the latest card as a 186 dp preview, and a pill `Shoot` enabled at any hour — the app says when the light is interesting, never whether to act. Recompute the countdown with `LaunchedEffect` + `delay(30_000)`, never a service. If `Settings.isSet` is false, this screen is the setup form instead.

- [ ] **Step 4: Shoot.** Full-bleed `PreviewView` via `AndroidView`, a top-left chip reading `sunset 17:40 · +6 min`, the centred white pill, and `FramingGuide` over it:

```kotlin
@Composable
fun FramingGuide(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val paint = androidx.compose.ui.graphics.Paint().apply {
            color = Color.White
            blendMode = BlendMode.Difference
            strokeWidth = 1f
        }
        drawLine(paint, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height))
        drawLine(paint, Offset(2f * size.width / 3f, 0f), Offset(2f * size.width / 3f, size.height))
        drawLine(paint, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f))
    }
}
```

Difference-blend white renders as the inverse of whatever is underneath, which is why three hairlines stay visible over both a dark band and a bright one where a single fixed alpha cannot. No reference image, no per-frame sampling.

- [ ] **Step 5: Card and Stack.** Detail = the rendered PNG plus two quiet text actions (`raw photo`, `share`) and the line `filed automatically · nothing to press`. Stack = a vertical `LazyColumn` covering **every date from the first card to today**, with an outlined empty card and its date wherever `eveningsWithCards()` has no entry — that set difference is what makes a hole knowable. Night theme. Cards scale `.97 → 1` and straighten `1.2° → 0°` from the scroll offset via `graphicsLayer` on the render thread; drop the rotation first if the frame budget misses 60 fps.

- [ ] **Step 6: Motion, in `ShootScreen` only.** 450 ms shutter→card exactly as the mockup's filmstrip: press-in scale `.97` at 0 ms, band reveal by clip height at 120/240 ms, winner strip at 360 ms, name opacity 0→1 over 120 ms starting at 330 ms. `spring(stiffness = 220, damping = 1)` on the clip, linear on the fade, no overshoot. Never animate height. Haptics: light tick on press-in, success when the card files, none on scroll, none in a loop, none while the model writes. If `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` or reduce-motion is on, collapse to a 120 ms cross-fade.

- [ ] **Step 7: Setup.** Two fields — place label, lat/lon to two decimals — with `KeyboardOptions(keyboardType = KeyboardType.NumberPassword)` for the coordinates and no location permission anywhere.

- [ ] **Step 8: Responsive classes.** Use `currentWindowAdaptiveInfo()` / `WindowSizeClass`: compact 1-up; medium 2-up with the card capped at 340 dp and centred; expanded ≥ 840 dp two panes (stack 38 %, detail 62 %, card max 420 dp). No bottom navigation at any width — four destinations and a back gesture. Above 1.15× font scale, cap the display size at 34 sp and drop the 1.2° rotation. Touch targets ≥ 48 dp.

- [ ] **Step 9: Manual review on the emulator.** All four screens side by side with the mockup. Fix drift in spacing, weight, or chrome colour. **Never fix drift by touching a measured band.** Check 360 dp and a 700 dp foldable window.

- [ ] **Step 10: Commit** `git commit -am "feat: four screens in the paper-and-ink system with a difference-blend framing guide"`.

---

## Task 10: `skycard.py` and the cross-language equivalence test

This is the demo: a reviewer cannot live on a Lahore balcony for a week, but they can turn their own sky photo into a card in under a minute.

**Files:** Create `tools/python/skycard.py`, `tools/python/test_skycard.py`, `fixtures/make_fixtures.py`; generate `fixtures/{sky_ramp,sky_blocks,sky_wide,sky_narrow}.png` and `fixtures/*.bands.json`; Test `core/src/test/kotlin/.../FixtureEquivalenceTest.kt`

**Interfaces:**
- Consumes: the exact arithmetic of `BandSampler` (T3), `Matcher` (T4), `CardGeometry` (T7).
- Produces: `python skycard.py PHOTO [-o out.png] [--print-json]` printing `bands`, `winner`, `name`; functions `bands_for(path) -> List[str]`, `sample(pixels,w,h)`, `winner(bands)`, `name_for(rgb)`, `VOCABULARY`.

- [ ] **Step 1: Write and run the fixture generator.** PNG, per decision 4.

```python
"""Deterministic fixtures. Run once with the Kotlin test to pin bands, then commit the output."""
from PIL import Image
import os

HERE = os.path.dirname(os.path.abspath(__file__))
BANDS = [(0x1E,0x24,0x38),(0x2A,0x33,0x50),(0x3C,0x4A,0x67),(0x5C,0x6B,0x82),
         (0x8A,0x83,0x75),(0xB0,0x8A,0x5E),(0xC2,0x85,0x4A),(0x3A,0x32,0x2A)]

def ramp():
    img = Image.new("RGB", (64, 48))
    for y in range(48):
        img.paste(BANDS[y // 6], (0, y, 64, y + 1))
    return img

def blocks():
    img = Image.new("RGB", (128, 96))
    for y in range(96):
        img.paste(BANDS[min(7, y * 8 // 96)], (0, y, 128, y + 1))
    return img

def wide():
    img = Image.new("RGB", (100, 100), (30, 32, 40))
    img.paste((200, 150, 60), (0, 80, 100, 100))
    return img

def narrow():
    # 32 x 6: the ONLY fixture with width < TARGET_WIDTH. In BandSampler/sample, dh would be
    # round_half_up(6*64/32) = 12 > the 6 source rows, so without the clamp both languages interpolate
    # rows that do not exist. Clamped to dh = 6, the eight bands repeat source rows as
    # r0,r0,r1,r2,r3,r3,r4,r5. Every other fixture is >= 64 px wide, where the clamp is the identity -
    # which is exactly why this one exists: it is the only thing that pins the clause cross-language.
    img = Image.new("RGB", (32, 6))
    for y in range(6):
        img.paste((10 + y * 40, 250 - y * 35, 20 + y * 15), (0, y, 32, y + 1))
    return img

for name, img in (("sky_ramp", ramp()), ("sky_blocks", blocks()), ("sky_wide", wide()),
                  ("sky_narrow", narrow())):
    img.save(os.path.join(HERE, "..", "..", "fixtures", name + ".png"))
```

- [ ] **Step 2: Pin the goldens from the Kotlin side**, so agreement is transitive and no cross-process call is needed.

`core/src/test/kotlin/com/burry071/evenlight/core/FixtureEquivalenceTest.kt`:

```kotlin
package com.burry071.evenlight.core

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Kotlin and Python must agree on the same pixels. Both assert against these files, so
 * agreement is transitive. PNG fixtures: a JPEG decoder difference would make this test lie.
 */
class FixtureEquivalenceTest {
    private val dir = File("../../fixtures")
    private val write = System.getProperty("evenlight.writeFixtures") == "true"

    @Test fun fixturesProduceThePinnedBands() {
        for (n in listOf("sky_ramp", "sky_blocks", "sky_wide", "sky_narrow")) {
            val img = ImageIO.read(File(dir, "$n.png"))
            val px = IntArray(img.width * img.height)
            img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
            val bands = BandSampler.sample(px, img.width, img.height).ordered.map { Pixels.hex(it) }
            val file = File(dir, "$n.bands.json")
            if (write) {
                file.writeText(
                    "{\n  \"width\": ${img.width},\n  \"height\": ${img.height},\n  \"bands\": " +
                        bands.joinToString(", ", "[", "]") { "\"$it\"" } + "\n}\n"
                )
            }
            val expected = Regex("\"bands\": \\[([^]]*)]").find(file.readText())!!.groupValues[1]
                .split(",").map { it.trim().trim('"') }
            assertEquals(n, expected, bands)
        }
    }
}
```

Create the JSON once with `-Devenlight.writeFixtures=true`, then run again **without** it and require PASS. Commit the PNGs and the JSON together.

- [ ] **Step 3: Implement the Python sampler**, mirroring `BandSampler` line for line. `round_half_up` is mandatory: Python's built-in `round` is banker's rounding and **will** diverge on `.5`.

```python
import math

TARGET_WIDTH = 64
BAND_COUNT = 8

def round_half_up(v):
    return math.floor(v + 0.5)

def relative_luminance(rgb):
    def lin(v):
        s = v / 255.0
        return s / 12.92 if s <= 0.03928 else ((s + 0.055) / 1.055) ** 2.4
    r, g, b = rgb
    return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)

def to_pixels(img):
    """ARGB-less packed ints, top row first, matching BitmapFactory.getPixels minus alpha."""
    w, h = img.size
    data = img.convert("RGB").tobytes()
    return [(data[i] << 16) | (data[i + 1] << 8) | data[i + 2] for i in range(0, len(data), 3)], w, h

def bilinear(src, sw, sh, dw, dh):
    if (sw, sh) == (dw, dh):
        return list(src)
    out = []
    sx = sw / dw
    sy = sh / dh
    for dy in range(dh):
        fy = min(max((dy + 0.5) * sy - 0.5, 0.0), sh - 1)
        y0 = int(fy); y1 = min(y0 + 1, sh - 1); wy = fy - y0
        for dx in range(dw):
            fx = min(max((dx + 0.5) * sx - 0.5, 0.0), sw - 1)
            x0 = int(fx); x1 = min(x0 + 1, sw - 1); wx = fx - x0
            a = src[y0 * sw + x0]; b = src[y0 * sw + x1]
            c = src[y1 * sw + x0]; d = src[y1 * sw + x1]
            w00 = (1 - wx) * (1 - wy); w01 = wx * (1 - wy)
            w10 = (1 - wx) * wy;       w11 = wx * wy
            chans = []
            for shift in (16, 8, 0):
                v = (((a >> shift) & 0xFF) * w00 + ((b >> shift) & 0xFF) * w01 +
                     ((c >> shift) & 0xFF) * w10 + ((d >> shift) & 0xFF) * w11)
                chans.append(min(255, max(0, round_half_up(v))))
            out.append((chans[0] << 16) | (chans[1] << 8) | chans[2])
    return out

def sample(src, width, height):
    dw = TARGET_WIDTH
    # min(height, ...) mirrors BandSampler.kt: without it, width < TARGET_WIDTH makes dh exceed the
    # source row count and the bilinear pass invents rows. Identity for width >= 64; sky_narrow pins it.
    dh = max(1, min(height, round_half_up(height * dw / width)))
    small = bilinear(src, width, height, dw, dh)
    bands = []
    for i in range(BAND_COUNT):
        y0 = i * dh // BAND_COUNT
        y1 = (i + 1) * dh // BAND_COUNT
        frm = min(y0, dh - 1)
        to = min(max(y1, y0 + 1) - 1, dh - 1)
        rs = gs = bs = 0; n = 0
        for y in range(frm, to + 1):
            row = y * dw
            for x in range(dw):
                c = small[row + x]
                rs += (c >> 16) & 0xFF; gs += (c >> 8) & 0xFF; bs += c & 0xFF
                n += 1
        bands.append((round_half_up(rs / n), round_half_up(gs / n), round_half_up(bs / n)))
    lum = sum(relative_luminance(b) for b in bands) / BAND_COUNT
    return bands, lum

def hex_of(rgb):
    return "#%02X%02X%02X" % rgb

def chroma_range(rgb):
    return max(rgb) - min(rgb)

def winner(bands):
    best = 0
    for i in range(1, len(bands)):
        ra, rl = chroma_range(bands[i]), relative_luminance(bands[i])
        ba, bl = chroma_range(bands[best]), relative_luminance(bands[best])
        if ra > ba or (ra == ba and rl > bl):
            best = i
    return best

def name_for(rgb, vocabulary=None):
    vocab = vocabulary or VOCABULARY
    best_word, best_d = vocab[0][0], None
    for word, anchor in vocab:
        d = sum((a - b) ** 2 for a, b in zip(rgb, anchor)) ** 0.5
        if best_d is None or d < best_d:
            best_d, best_word = d, word
    return best_word

def bands_for(path):
    from PIL import Image
    src, w, h = to_pixels(Image.open(path))
    return [hex_of(b) for b in sample(src, w, h)[0]]
```

`VOCABULARY` must be copied from `Vocabulary.kt` verbatim — same 35 words, same order, same anchors, as `(word, (r, g, b))` tuples. A divergent anchor makes a sampler bug look like a fixture problem. Add `render_card(bands, out_path)` reproducing Task 7's numbers (`field_height = round_half_up(h * 0.62)`, blend between band centres, strip, ink rule) with Pillow rectangles and `ImageDraw.text`.

- [ ] **Step 4: Write the Python test and make it pass.**

```python
import json, os, sys, unittest
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import skycard

FIX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "fixtures")

class Equivalence(unittest.TestCase):
    def test_bands_match_the_kotlin_goldens(self):
        for name in ("sky_ramp", "sky_blocks", "sky_wide", "sky_narrow"):
            with self.subTest(name):
                expected = json.load(open(os.path.join(FIX, name + ".bands.json")))["bands"]
                self.assertEqual([s.upper() for s in expected], [s.upper() for s in skycard.bands_for(os.path.join(FIX, name + ".png"))])

    def test_winner_and_name_follow_the_kotlin_rules(self):
        self.assertEqual("brassy", skycard.name_for((0xC2, 0x85, 0x4A)))
        self.assertEqual("slate", skycard.name_for((0x5C, 0x6B, 0x82)))

    def test_vocabulary_is_the_same_thirty_five_words(self):
        self.assertEqual(35, len(skycard.VOCABULARY))
        self.assertEqual(35, len({w for w, _ in skycard.VOCABULARY}))

if __name__ == "__main__":
    unittest.main()
```

Run: `F:/Ancoda/python.exe tools/python/test_skycard.py -v` → 3 tests PASS. **If the band test fails, that failure is the point** — diff the two implementations' rounding and filter conventions until they agree. Do not weaken the assertion to get green.

- [ ] **Step 5: Prove the CLI.**

```bash
F:/Ancoda/python.exe tools/python/skycard.py fixtures/sky_blocks.png -o /tmp/card.png --print-json
```
Expected: eight hex values, `"winner": 6`, `"name": "brassy"`, a 1080×1440 PNG, and the whole thing in well under a minute — that promise is in the spec.

- [ ] **Step 6: Commit** `git commit -am "test: kotlin/python band equivalence pinned to shared PNG fixtures"`.

---

## Task 11: `Wording` — the model's one line, optional by construction

**Files:** Create `app/.../wording/{Wording,EngineHolder}.kt`; Modify `EvenlightController.kt`; Test `app/src/test/java/com/burry071/evenlight/wording/WordingTest.kt`

**Interfaces:**
- Consumes: `Match.name` (T4), `Verifier.check` (T5), `Store.wordingRejected()`.
- Produces: `object Wording { const val SYSTEM; prompt(name, bands: List<String>, altitudeDeg: Int): String; template(name): String }`; `class EngineHolder(context) { val modelFile: File; suspend fun ensure(): Boolean; suspend fun line(name, bands, altitudeDeg): String?; fun close() }`.

- [ ] **Step 1: Write the failing test.** The prompt and fallback are pure strings, so they are testable without 1.02 GB of weights.

```kotlin
package com.burry071.evenlight.wording

import com.burry071.evenlight.core.Verdict
import com.burry071.evenlight.core.Verifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordingTest {
    private val bands = listOf("#1E2438", "#2A3350", "#3C4A67", "#5C6B82", "#8A8375", "#B08A5E", "#C2854A", "#3A322A")

    @Test fun templateCarriesTheNameAndPassesTheVerifier() {
        assertEquals(Verdict.Accept, Verifier.check(Wording.template("brassy"), "brassy"))
    }

    @Test fun templateContainsNoDigit() {
        assertTrue(Wording.template("low").none { it.isDigit() })
    }

    @Test fun promptHandsOverTheNameAndForbidsDigits() {
        val p = Wording.prompt("brassy", bands, 9)
        assertTrue(p.contains("brassy"))
        assertTrue(p.lowercase().contains("do not use any digit"))
    }

    @Test fun promptNeverAsksTheModelToChooseOrMeasure() {
        val p = Wording.prompt("brassy", bands, 9).lowercase()
        for (forbidden in listOf("choose", "pick", "rank", "which", "estimate", "measure")) {
            assertFalse("prompt says '$forbidden'", p.contains(forbidden))
        }
    }
}
```

- [ ] **Step 2: Run** → FAIL, unresolved `Wording`.

- [ ] **Step 3: Implement `Wording.kt`.**

```kotlin
package com.burry071.evenlight.wording

object Wording {
    const val SYSTEM =
        "You write one short line of plain English about an evening sky. " +
        "Use the name you are given. Do not use any digit, hash sign, bracket, or newline. " +
        "Do not invent a colour, an object, a person, or a place. " +
        "Reply with one lowercase line of four to eight words and nothing else."

    /** The name is handed over, never asked for: choosing measured 1/6 consistent (spec section 7). */
    fun prompt(name: String, bands: List<String>, altitudeDeg: Int): String = buildString {
        appendLine("name: $name")
        appendLine("bands: ${bands.joinToString(" ")}")
        appendLine("sun: ${altitudeDeg} degrees")
        append("Write one line of four to eight words that contains the word $name. ")
        append("Do not use any digit and do not add a colour that is not listed.")
    }

    fun template(name: String): String = "$name light, measured from this sky"
}
```

- [ ] **Step 4: Implement `EngineHolder.kt`** with the spike's measured behaviour: CPU backend, lazy init off the main thread, every failure route landing on the template.

```kotlin
package com.burry071.evenlight.wording

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class EngineHolder(private val context: Context) {
    private var engine: Engine? = null
    private var ready = false

    /** Measured on an x86_64 API-35 AVD: init 4,566 ms, peak RSS 1,269 MB, ~1.2 s per line. */
    val modelFile: File
        get() = File(context.filesDir, "models/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm")

    suspend fun ensure(): Boolean {
        if (ready) return true
        if (!modelFile.exists()) return false
        val e = Engine(EngineConfig(modelPath = modelFile.absolutePath, backend = Backend.CPU()))
        val ok = withContext(Dispatchers.Default) {
            try { withTimeoutOrNull(30_000) { e.initialize(); true } ?: false } catch (t: Throwable) { false }
        }
        if (!ok) runCatching { e.close() } else { engine = e; ready = true }
        return ok
    }

    /** Null on any failure. The caller falls back to Wording.template; nothing blocks on this. */
    suspend fun line(name: String, bands: List<String>, altitudeDeg: Int): String? {
        val e = engine ?: return null
        return try {
            withContext(Dispatchers.Default) {
                withTimeoutOrNull(10_000) {
                    val cfg = ConversationConfig(
                        systemInstruction = Contents.of(Wording.SYSTEM),
                        samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.7)
                    )
                    e.createConversation(cfg).use { conv ->
                        conv.sendMessage(Wording.prompt(name, bands, altitudeDeg))
                            .contents.contents.filterIsInstance<Content.Text>()
                            .joinToString(" ") { it.text }.trim().lowercase()
                    }
                }
            }
        } catch (t: Throwable) { null }
    }

    fun close() { runCatching { engine?.close() }; engine = null; ready = false }
}
```

`initialize()` is blocking, which is why it is wrapped in `withContext(Dispatchers.Default)` — a `withTimeoutOrNull` around a blocking call does not actually cancel it.

- [ ] **Step 5: Wire it in after the card is filed**, never before, so the ~1.2 s is invisible to the shutter:

```kotlin
        val candidate = if (engine.ensure()) engine.line(match.name, record.bandsHex, altitude) else null
        val verdict = candidate?.let { Verifier.check(it, match.name) }
        val finalRecord = when {
            candidate != null && verdict is Verdict.Accept ->
                record.copy(wording = candidate, wordingSource = "model", wordingRejected = false)
            candidate != null ->
                record.copy(wording = Wording.template(match.name), wordingSource = "template", wordingRejected = true)
            else ->
                record.copy(wording = Wording.template(match.name), wordingSource = "none", wordingRejected = false)
        }
        store.writeRecord(finalRecord, attempt)
        store.writeCard(date, renderer.render(bands.ordered, headerFor(finalRecord, match)).pngBytes())
```

The bands and the name never change; only the wording line and its provenance do.

- [ ] **Step 6: Prove the no-model path with the weights absent.** Install, shoot: a card appears immediately, `wording_source` is `"none"`, nothing crashed. Then push the file and shoot again:

```bash
MSYS_NO_PATHCONV=1 adb push /d/trailwalk/models/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm \
  /data/data/com.burry071.evenlight/files/models/
MSYS_NO_PATHCONV=1 adb shell chmod 644 /data/data/com.burry071.evenlight/files/models/*.litertlm
```
Expected: `wording_source` becomes `"model"` or `"template"` with `wording_rejected: true`. **Both are correct outcomes** — the rejection rate is a result the README reports, not a bug to hide. Record `wording_rejected/wording_total` as you go; Task 12 quotes it.

- [ ] **Step 7: Commit** `git commit -am "feat: optional on-device wording with a verifier gate and a counted fallback"`.

---

## Task 12: README, `GET_MODEL.md`, and the release checklist

**Files:** Create `README.md`, `models/GET_MODEL.md`, `LICENSE`, `.gitignore`; Modify nothing in code.

**Interfaces:**
- Consumes: every measured number from Tasks 2–11 and the user's real cards.
- Produces: a repo a stranger can reproduce from.

- [ ] **Step 1: Write `.gitignore`** — `*.litertlm`, `build/`, `.gradle/`, `local.properties`, `*.apk`, plus a comment stating why the weights are ignored (GitHub's hard 100 MB per-file limit means the 584 MB file cannot be committed at all).

- [ ] **Step 2: Write `models/GET_MODEL.md`** with the exact facts, no more: HuggingFace URL for `litert-community/Gemma3-1B-IT-ONNX`, filename `Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm`, size **584,417,280 B**, sha256 `1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be`, that it is `gated=auto` under Gemma Terms of Use so they must accept in their own browser, the destination path `files/models/`, and that **total on-disk cost is 1,092,957,056 B ≈ 1.02 GiB** because the engine writes a 508,539,776 B XNNPack cache beside it. State plainly that the app is complete without it.

- [ ] **Step 3: Write the README** in this order, each item traceable to a file: what it is; how to run `tools/python/skycard.py` on your own photo (the demo); the sampler and vocabulary as code, not description; the model section quoting the probe table — **18/18 obedient on format, 1/6 consistent at choosing, so it was moved off judgement** — plus `wording_rejected/wording_total` from real use; the measured install cost; the permission list with the `AndroidManifest.xml` line; and the record with its holes stated exactly.

- [ ] **Step 4: Read the target phone before writing any performance sentence.** `adb shell getprop ro.product.model` and `adb shell cat /proc/meminfo | head -1`. The probe numbers are emulator numbers (spec §12 correction 3); the user's self-reported "Pixel 7 6 GB" does not match the official 8 GB spec, so quote the device.

- [ ] **Step 5: Screenshots.** Four emulator shots matching the mockup's four screens, plus the real cards as one strip.

- [ ] **Step 6: Verify the whole thing from a clean checkout.**

```bash
git clone . /tmp/el-check && cd /tmp/el-check
F:/Ancoda/python.exe tools/python/test_skycard.py -v
/c/Users/User/.gradle/wrapper/dists/gradle-8.8-bin/dl7vupf4psengwqhwktix4v1/gradle-8.8/bin/gradle.bat :core:test :app:testDebugUnitTest --offline -q
```
Expected: everything green with no model file present — that is the proof the app is optional-model by construction.

- [ ] **Step 7: Stop and ask before any shared-state action.** Creating `Burry071/evenlight`, pushing, and publishing the DEV article are all outside this plan's authority and need explicit approval, and the repo must exist inside the Oct 5–11 window.

- [ ] **Step 8: Commit** `git commit -am "docs: README, model fetch instructions, and the measured probe numbers"`.

---

## Self-review notes

- **Spec coverage:** §8.1 setup → T9 step 7; §8.2 sunset → T2, T9 step 3; §8.3 capture and framing → T8, T9 step 4; §8.4 sampling → T3, T4; §8.5 low light → T8 (`mean_luminance` recorded, nothing dropped); §6 card → T7; §6.1 design system → T9; §7 model and verifier → T5, T11; §9 files → T6, T8; §10 testing → T2–T7, T10; §11 errors → T8 step 6 (record-before-render, clock drift, attempts), T9 (no camera permission), T11 (no model); §15 repo/licence → T12. **No §-of-the-spec is unimplemented except the two things the spec itself excludes.**
- **Cut order if Oct 10 arrives unfinished** (spec §13): T11 → drop Gemma and let the 1/6 measurement be the content; then T10 → ship Kotlin-only with the published golden table; then the neighbour squares and two-word name → bare band stack plus date. Never cut capture, the measured-colour rule, the visible gaps, the no-`INTERNET` manifest, or the exact count with holes.
- **Known risk to watch in T3/T10:** the equivalence test is the one that can fail for a reason neither language caused. If Kotlin and Python disagree on a fixture, check rounding mode and the half-pixel convention before suspecting the algorithm.
