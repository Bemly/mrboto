package moe.bemly.mrboto

import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File

fun MrbotoActivityBase.cameraAvailable(): Boolean {
    return try {
        val cm = getSystemService(android.content.Context.CAMERA_SERVICE) as CameraManager
        cm.cameraIdList.isNotEmpty()
    } catch (_: Exception) { false }
}

fun MrbotoActivityBase.cameraInfo(): String {
    return try {
        val cm = getSystemService(android.content.Context.CAMERA_SERVICE) as CameraManager
        val arr = org.json.JSONArray()
        for (id in cm.cameraIdList) {
            val chars = cm.getCameraCharacteristics(id)
            val obj = JSONObject()
            obj.put("id", id)
            obj.put("facing", chars.get(CameraCharacteristics.LENS_FACING) ?: -1)
            obj.put("hasFlash", chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false)
            arr.put(obj)
        }
        arr.toString()
    } catch (_: Exception) { "[]" }
}

private var photoUri: Uri? = null
private var photoCallbackId: Int = -1

fun MrbotoActivityBase.cameraTakePhoto(callbackId: Int): Boolean {
    return try {
        val file = File(cacheDir, "mrboto_photo_${System.currentTimeMillis()}.jpg")
        photoUri = androidx.core.content.FileProvider.getUriForFile(
            this, "${packageName}.mrboto.fileprovider", file
        )
        photoCallbackId = callbackId
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        }
        startActivityForResult(intent, 9001)
        true
    } catch (e: Exception) {
        Log.w("Mrboto", "cameraTakePhoto failed: ${e.message}")
        false
    }
}

private var videoCallbackId: Int = -1

fun MrbotoActivityBase.cameraRecordVideo(callbackId: Int): Boolean {
    return try {
        videoCallbackId = callbackId
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        startActivityForResult(intent, 9002)
        true
    } catch (e: Exception) {
        Log.w("Mrboto", "cameraRecordVideo failed: ${e.message}")
        false
    }
}

fun MrbotoActivityBase.cameraOpenPreview(surfaceViewId: Int): Boolean {
    // Camera2 preview requires SurfaceView/TextureView — placeholder
    return false
}

fun MrbotoActivityBase.cameraClosePreview() {
    // placeholder
}
