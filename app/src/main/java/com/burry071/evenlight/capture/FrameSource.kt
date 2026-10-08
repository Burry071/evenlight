package com.burry071.evenlight.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.Surface
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

/** Sampling never needs 12 megapixels; drawing a card never needs the decoded pixels at all. */
const val MAX_SAMPLE_EDGE = 1024

/**
 * Top level rather than a companion member, and this file's only JVM-pure function, so a unit test can reach it
 * without loading a class that holds CameraX types. Powers of two only - that is what a decoder actually honours.
 */
fun sampleSize(width: Int, height: Int): Int {
    var s = 1
    while (maxOf(width, height) / s > MAX_SAMPLE_EDGE) s *= 2
    return s
}

class FrameSource(private val context: Context, private val executor: Executor) {

    var imageCapture: ImageCapture? = null
        private set

    fun start(provider: ProcessCameraProvider, previewView: PreviewView, owner: LifecycleOwner) {
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .setTargetRotation(Surface.ROTATION_0)   // see the ruling above: the manual rotation must be the only one
            .build()
        val preview = Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
        provider.unbindAll()
        provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
    }

    /**
     * A camera failure is thrown, not folded into an empty `Still`: `shoot()` is wrapped in `runCatching`, so the
     * exception reaches the screen as its own message. Returning an empty frame instead would make permission
     * revocation, a missing camera and an encoding failure all read "capture produced no pixels" - on a step whose
     * entire purpose is a failure legible without logcat.
     */
    suspend fun take(rotationDegrees: Int): Still {
        val capture = imageCapture ?: throw IllegalStateException("camera not bound yet")
        val target = File(context.cacheDir, "shot.jpg")
        val jpeg = suspendCoroutine { cont ->
            val options = ImageCapture.OutputFileOptions.Builder(target).build()
            capture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(o: ImageCapture.OutputFileResults) {
                    cont.resume(if (target.exists()) target.readBytes() else ByteArray(0))
                }
                override fun onError(exc: ImageCaptureException) {
                    cont.resumeWith(Result.failure(IllegalStateException("capture failed: ${exc.imageCaptureError}", exc)))
                }
            })
        }
        if (jpeg.isEmpty()) throw IllegalStateException("capture wrote an empty file")
        return decode(jpeg, rotationDegrees)
    }

    companion object {
        fun decode(jpeg: ByteArray, rotationDegrees: Int): Still {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
            var bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
                ?: return Still(IntArray(0), 0, 0, jpeg)
            if (rotationDegrees != 0) {
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true)
            }
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            val w = bmp.width; val h = bmp.height
            bmp.recycle()
            return Still(px, w, h, jpeg)
        }

        /**
         * Display rotation -> the degrees the decoded bitmap must be turned to stand up. The `0 -> 90` row is now
         * measured rather than assumed: the JPEG on the device carried EXIF orientation 6 (rotate 90 CW to display)
         * and re-deriving the band means from that file matched the stored bands only under a clockwise 90. The
         * other three rows are still a table nobody has exercised - the device was never rotated off its natural
         * orientation, so 1, 2 and 3 remain hypotheses.
         */
        fun rotationFor(displayRotation: Int): Int = when (displayRotation) {
            0 -> 90; 1 -> 0; 2 -> 180; else -> 270
        }
    }
}
