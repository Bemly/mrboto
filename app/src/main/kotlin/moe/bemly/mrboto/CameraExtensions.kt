package moe.bemly.mrboto

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

interface CameraMixin {
    val mruby: MRuby

    fun cameraAvailable(): Boolean {
        val activity = this as Activity
        return try {
            val cm = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.cameraIdList.isNotEmpty()
        } catch (_: Exception) { false }
    }

    fun cameraInfo(): String {
        val activity = this as Activity
        return try {
            val cm = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val arr = org.json.JSONArray()
            for (id in cm.cameraIdList) {
                val chars = cm.getCameraCharacteristics(id)
                val obj = org.json.JSONObject()
                obj.put("id", id)
                obj.put("facing", chars.get(CameraCharacteristics.LENS_FACING) ?: -1)
                obj.put("hasFlash", chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false)
                arr.put(obj)
            }
            arr.toString()
        } catch (_: Exception) { "[]" }
    }

    fun cameraTakePhoto(callbackId: Int): Boolean {
        val activity = this as Activity
        return try {
            val file = File(activity.cacheDir, "mrboto_photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                activity, "${activity.packageName}.mrboto.fileprovider", file
            )
            if (activity is MrbotoActivityBase) {
                activity._photoCallbackId = callbackId
                activity._photoUri = uri
                // Store the absolute file path for OCR access
                activity._photoFilePath = file.absolutePath
                activity.photoLauncher.launch(uri)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w("Mrboto", "cameraTakePhoto failed: ${e.message}")
            false
        }
    }

    fun cameraRecordVideo(callbackId: Int): Boolean {
        val activity = this as Activity
        return try {
            if (activity is MrbotoActivityBase) {
                activity._videoCallbackId = callbackId
                val file = File(activity.cacheDir, "mrboto_video_${System.currentTimeMillis()}.mp4")
                val uri = FileProvider.getUriForFile(
                    activity, "${activity.packageName}.mrboto.fileprovider", file
                )
                activity._videoOutputUri = uri
                activity.videoLauncher.launch(uri)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w("Mrboto", "cameraRecordVideo failed: ${e.message}")
            false
        }
    }

    fun cameraOpenPreview(surfaceViewId: Int): Boolean {
        // Camera2 preview requires SurfaceView/TextureView — placeholder
        return false
    }

    fun cameraClosePreview() {
        // placeholder
    }
}
