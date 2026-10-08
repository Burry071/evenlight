package com.burry071.evenlight

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.burry071.evenlight.capture.FrameSource
import com.burry071.evenlight.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Task 8's stopgap: the nightly path (capture -> sample -> match -> render -> files) has to be tappable tonight so
 * Step 7's device pass can exercise it at all. Deliberately ugly - setContentView twice, no state, no theme. Task 9
 * replaces this whole file with the Compose screens; nothing here is a contract except
 * [EvenlightController.forDevice].
 */
class MainActivity : ComponentActivity() {
    private lateinit var frame: FrameSource
    private lateinit var controller: EvenlightController
    private lateinit var preview: PreviewView

    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) bindCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(filesDir)
        // Task 9's Setup screen owns this. Until then a device can be pointed at the sky by writing
        // files/evenlight/settings.json through adb, and a phone with no coordinates simply refuses to shoot.
        val settings = store.loadSettings()
        // ContextCompat rather than Context.getMainExecutor(), which is API 28 and this module's minSdk is 27.
        frame = FrameSource(this, ContextCompat.getMainExecutor(this))
        controller = EvenlightController.forDevice(this, store, settings, frame)
        preview = PreviewView(this)
        val shoot = Button(this).apply { text = "Shoot" }
        setContentView(LinearLayout(this).apply {
            // VERTICAL is load-bearing, not cosmetic: a LinearLayout defaults to HORIZONTAL, where the preview's
            // (0, 0, weight 1) means "zero width, zero height, take the leftover WIDTH" - a collapsed view that
            // never shows a frame to aim by. CameraX still captures, which is why Step 7 passed without noticing.
            orientation = LinearLayout.VERTICAL
            addView(preview, LinearLayout.LayoutParams(0, 0, 1f))
            addView(shoot, LinearLayout.LayoutParams(-1, -2))
        })
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            bindCamera()
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
        shoot.setOnClickListener {
            lifecycleScope.launch {
                controller.shoot().fold(
                    onSuccess = { setContentView(TextView(this@MainActivity).apply { text = "${it.name} / ${it.bandsHex}" }) },
                    onFailure = { setContentView(TextView(this@MainActivity).apply { text = "failed: ${it.message}" }) }
                )
            }
        }
    }

    /**
     * camera-lifecycle 1.3.4 has no `ProcessCameraProvider.awaitInstance` (it lands in 1.4.0) and no new
     * dependency may be fetched, so the ListenableFuture is awaited off the main thread and the use-case bind
     * happens back on it - the fallback the plan names: `getInstance(this).get()`.
     */
    private fun bindCamera() {
        lifecycleScope.launch {
            val provider = withContext(Dispatchers.IO) {
                try { ProcessCameraProvider.getInstance(this@MainActivity).get() } catch (t: Throwable) { null }
            }
            if (provider != null) frame.start(provider, preview, this@MainActivity)
        }
    }
}
