package moe.bemly.mrboto

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.File

interface ScreenCaptureMixin {
    val mruby: MRuby

    fun requestScreenCapture(callbackId: Int): Boolean {
        val activity = this as Activity
        return try {
            if (activity is MrbotoActivityBase) {
                val mpm = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val intent = mpm.createScreenCaptureIntent()
                activity.screenCaptureLauncher.launch(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w("Mrboto", "requestScreenCapture failed: ${e.message}")
            false
        }
    }

    fun captureScreen(outPath: CharSequence): Boolean {
        val activity = this as Activity
        return try {
            val mp = if (activity is MrbotoActivityBase) activity._mediaProjection else null
            if (mp == null) return false
            val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            val reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, 0x1, 2)
            val vd = mp.createVirtualDisplay(
                "MrbotoCapture", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null
            )
            Thread.sleep(200)
            val image = reader.acquireLatestImage()
            val plane = image?.planes?.get(0)
            val buffer = plane?.buffer
            if (buffer != null) {
                val pixels = ByteArray(buffer.remaining())
                buffer.get(pixels)
                val bmp = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(pixels))
                File(outPath.toString()).outputStream().use {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bmp.recycle()
            }
            image?.close()
            vd?.release()
            reader.close()
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "captureScreen failed: ${e.message}")
            false
        }
    }

    fun startRecordScreen(outPath: CharSequence): Boolean {
        val activity = this as Activity
        return try {
            val mp = if (activity is MrbotoActivityBase) activity._mediaProjection else null
            if (mp == null) return false
            val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(activity) else @Suppress("DEPRECATION") MediaRecorder()
            recorder.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(metrics.widthPixels, metrics.heightPixels)
                setVideoFrameRate(30)
                setOutputFile(File(outPath.toString()).absolutePath)
                prepare()
            }
            val vd = mp.createVirtualDisplay(
                "MrbotoRecord", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, recorder.surface, null, null
            )
            recorder.start()
            if (activity is MrbotoActivityBase) {
                activity._mediaRecorder = recorder
                activity._virtualDisplay = vd
            }
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "startRecordScreen failed: ${e.message}")
            false
        }
    }

    fun stopRecordScreen(): Boolean {
        val activity = this as Activity
        return try {
            if (activity is MrbotoActivityBase) {
                activity._mediaRecorder?.stop()
                activity._mediaRecorder?.release()
                activity._mediaRecorder = null
                activity._virtualDisplay?.release()
                activity._virtualDisplay = null
            }
            true
        } catch (_: Exception) { false }
    }
}
