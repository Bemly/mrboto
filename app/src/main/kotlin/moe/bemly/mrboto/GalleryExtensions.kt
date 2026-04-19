package moe.bemly.mrboto

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File

private var galleryCallbackId: Int = -1
private var selectedImageUri: Uri? = null

/**
 * Gallery image picking functionality.
 *
 * Allows selecting images from the device gallery and copying them
 * to app cache for further processing (e.g., QR code scanning).
 */
interface GalleryMixin {
    val mruby: MRuby

    /**
     * Open the device gallery to pick an image.
     * @param callbackId Callback ID that will receive (success, uri_string)
     * @return true if intent was successfully launched
     */
    fun pickImageFromGallery(callbackId: Int): Boolean {
        val activity = this as Activity
        return try {
            galleryCallbackId = callbackId
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
            }
            activity.startActivityForResult(intent, 9003)
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "pickImageFromGallery failed: ${e.message}")
            false
        }
    }

    /**
     * Copy the selected image to the app cache directory.
     * @param outputPath Relative path in cache directory
     * @return Absolute path of the copied file, empty string on failure
     */
    fun copySelectedImageToCache(outputPath: CharSequence): String {
        val activity = this as Activity
        val uri = selectedImageUri ?: return ""
        return try {
            val inputStream = activity.contentResolver.openInputStream(uri) ?: return ""
            val outputFile = File(activity.cacheDir, outputPath.toString())
            inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            outputFile.absolutePath
        } catch (e: Exception) {
            Log.w("Mrboto", "copySelectedImageToCache failed: ${e.message}")
            ""
        }
    }
}
