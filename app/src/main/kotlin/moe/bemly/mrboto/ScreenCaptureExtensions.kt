package moe.bemly.mrboto

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.File

private var mediaProjection: MediaProjection? = null
private var virtualDisplay: VirtualDisplay? = null
private var imageReader: ImageReader? = null
private var mediaRecorder: MediaRecorder? = null

fun MrbotoActivityBase.requestScreenCapture(callbackId: Int): Boolean {
    return try {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mpm.createScreenCaptureIntent()
        startActivityForResult(intent, 9100)
        true
    } catch (e: Exception) {
        Log.w("Mrboto", "requestScreenCapture failed: ${e.message}")
        false
    }
}

fun MrbotoActivityBase.captureScreen(outPath: CharSequence): Boolean {
    return try {
        val mp = mediaProjection ?: return false
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
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
        vd.release()
        reader.close()
        true
    } catch (e: Exception) {
        Log.w("Mrboto", "captureScreen failed: ${e.message}")
        false
    }
}

fun MrbotoActivityBase.startRecordScreen(outPath: CharSequence): Boolean {
    return try {
        val mp = mediaProjection ?: return false
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
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
        mediaRecorder = recorder
        virtualDisplay = vd
        true
    } catch (e: Exception) {
        Log.w("Mrboto", "startRecordScreen failed: ${e.message}")
        false
    }
}

fun MrbotoActivityBase.stopRecordScreen(): Boolean {
    return try {
        mediaRecorder?.stop()
        mediaRecorder?.release()
        mediaRecorder = null
        virtualDisplay?.release()
        virtualDisplay = null
        true
    } catch (_: Exception) { false }
}
