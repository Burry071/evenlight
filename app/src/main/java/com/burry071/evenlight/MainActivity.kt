package com.burry071.evenlight

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.burry071.evenlight.capture.FrameSource
import com.burry071.evenlight.core.EveningRecord
import com.burry071.evenlight.data.Settings
import com.burry071.evenlight.data.Store
import com.burry071.evenlight.ui.Nav
import com.burry071.evenlight.ui.EvenlightContent
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The shell for the four screens, and the only place that knows what an `Activity` is.
 *
 * Two things Task 8 built survive into here, deliberately:
 *  - the camera binding. `PreviewView` is created inside `ShootScreen`'s `AndroidView`, and the provider it binds to
 *    is awaited on `Dispatchers.IO` before the bind is handed back to the main thread, because camera-lifecycle
 *    1.3.4 has no `ProcessCameraProvider.awaitInstance` (that lands in 1.4.0) and no dependency may be fetched.
 *  - `ContextCompat.getMainExecutor` rather than `Context.getMainExecutor`, which is API 28 and this module's minSdk
 *    is 27.
 *  - the `RequestPermission` contract for `CAMERA`, the app's only runtime permission.
 *
 * File and record reads go to `Dispatchers.IO` because `readRecords()` walks the filesystem on the same thread that
 * would otherwise be drawing the first frame.
 */
class MainActivity : ComponentActivity() {

    private lateinit var store: Store
    private lateinit var frame: FrameSource
    private var provider: ProcessCameraProvider? = null
    private var preview: PreviewView? = null

    private var settings by mutableStateOf(Settings.UNSET)
    private var records by mutableStateOf<List<EveningRecord>>(emptyList())
    private var held by mutableStateOf<Set<String>>(emptySet())
    private var controller by mutableStateOf<EvenlightController?>(null)
    private var hasCamera by mutableStateOf(false)

    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
        if (granted) bindCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(filesDir)
        settings = store.loadSettings()
        frame = FrameSource(this, ContextCompat.getMainExecutor(this))
        controller = EvenlightController.forDevice(this, store, settings, frame)
        hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

        setContent {
            val nav = Nav.rememberNav()
            EvenlightContent(
                nav = nav,
                store = store,
                settings = settings,
                records = records,
                held = held,
                controller = controller ?: return@setContent,
                hasCamera = hasCamera,
                onEnsureCamera = { ensureCamera() },
                onPreviewReady = { view -> preview = view; bindCamera() },
                onOpenRaw = { record -> openRaw(record) },
                onShareCard = { date -> shareCard(date) },
                // The same predicate `openRaw` guards with, so the action is hidden exactly when pressing it would
                // have done nothing. One stat call per visible card, not a per-frame read.
                canReadRaw = { record -> store.rawFile(record.shutter, record.attempt).canRead() },
                onSettingsSaved = { s ->
                    settings = s
                    controller = EvenlightController.forDevice(this, store, s, frame)
                    reload()
                },
                reloadData = { reload() },
                reduceMotion = reduceMotion()
            )
        }
        reload()
    }

    /**
     * The filesystem is the truth and this snapshot is a copy of it, so the copy is refreshed whenever the activity
     * comes back: a settings file written while the app was backgrounded, or an evening filed by the nightly path
     * minutes ago, would otherwise stay invisible until the process restarted.
     */
    override fun onResume() {
        super.onResume()
        reload()
    }

    /** Every read this screen does, in one place, so a shot that files a card cannot leave the stack on old data. */
    private fun reload() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { store.readRecords() to store.eveningsWithCards() }
            records = loaded.first
            held = loaded.second
        }
    }

    private fun ensureCamera() {
        if (hasCamera) bindCamera() else requestCamera.launch(Manifest.permission.CAMERA)
    }

    /**
     * Idempotent, and called from both sides of the race that matters: the preview arriving, and the permission (or
     * the provider) arriving. `FrameSource.start` unbinds before binding, so a second call after a rotation is a
     * re-bind and not a leak.
     */
    private fun bindCamera() {
        val view = preview ?: return
        val bound = provider
        if (bound != null) {
            frame.start(bound, view, this)
            return
        }
        lifecycleScope.launch {
            val got = withContext(Dispatchers.IO) {
                try {
                    ProcessCameraProvider.getInstance(this@MainActivity).get()
                } catch (t: Throwable) {
                    null
                }
            }
            if (got != null) {
                provider = got
                if (preview === view) frame.start(got, view, this@MainActivity)
            }
        }
    }

    /**
     * `raw photo` and `share` both hand a file to another app, and a `file://` extra throws
     * `FileUriExposedException` from API 24 up, so both go through the `androidx.core.content.FileProvider`
     * declared in the manifest with authority `${applicationId}.fileprovider`. The grant is per-URI and read-only:
     * the runtime permission set stays exactly `{CAMERA}`.
     */
    private fun openRaw(record: EveningRecord) {
        val file = store.rawFile(record.shutter, record.attempt)
        if (!file.canRead()) return
        startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                type = "image/jpeg"
                data = uriFor(file)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }

    private fun shareCard(date: String) {
        val file = store.cardFile(date)
        if (!file.canRead()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uriFor(file))
            putExtra(Intent.EXTRA_SUBJECT, "Evenlight $date")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, null))
    }

    private fun uriFor(file: File) =
        FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

    /**
     * The brief names two triggers, and only one of them exists as an API. Measured, not assumed: `javap` on the
     * compile SDK's `android-35.jar` shows `android.view.accessibility.AccessibilityManager` has no reduce-motion
     * method (its motion-adjacent surface is `isTouchExplorationEnabled` and `isAudioDescriptionRequested`), and the
     * only animation scales in `Settings.Global`, `Settings.Secure` and `Settings.System` are `ANIMATOR_DURATION_SCALE`,
     * `TRANSITION_ANIMATION_SCALE` and `WINDOW_ANIMATION_SCALE`. Android's accessibility "Remove animations" toggle
     * therefore has no public read path here, and this app does not guess at a hidden settings key to fake one.
     *
     * What this does honour is the developer-option scale, which is the switch that actually kills the filmstrip's
     * animation on API 27 up. Whether the a11y toggle also writes this scale is Step 10's question on a real device;
     * this host's emulator screencap cannot show a camera frame at all, so nothing about the strip's motion has been
     * observed here yet.
     */
    private fun reduceMotion(): Boolean =
        android.provider.Settings.Global.getFloat(
            contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
}
