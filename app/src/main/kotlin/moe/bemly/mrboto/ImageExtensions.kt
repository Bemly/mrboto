package moe.bemly.mrboto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

interface ImageMixin {
    val mruby: MRuby

    fun imageCrop(path: CharSequence, x: Int, y: Int, w: Int, h: Int, outPath: CharSequence): Boolean {
        return try {
            val bmp = BitmapFactory.decodeFile(path.toString()) ?: return false
            val cropped = Bitmap.createBitmap(bmp, x, y, w, h)
            bmp.recycle()
            File(outPath.toString()).outputStream().use {
                cropped.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            cropped.recycle()
            true
        } catch (_: Exception) { false }
    }

    fun imageScale(path: CharSequence, newW: Int, newH: Int, outPath: CharSequence): Boolean {
        return try {
            val bmp = BitmapFactory.decodeFile(path.toString()) ?: return false
            val scaled = Bitmap.createScaledBitmap(bmp, newW, newH, true)
            bmp.recycle()
            File(outPath.toString()).outputStream().use {
                scaled.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            scaled.recycle()
            true
        } catch (_: Exception) { false }
    }

    fun imageRotate(path: CharSequence, degrees: Float, outPath: CharSequence): Boolean {
        return try {
            val bmp = BitmapFactory.decodeFile(path.toString()) ?: return false
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            bmp.recycle()
            File(outPath.toString()).outputStream().use {
                rotated.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            rotated.recycle()
            true
        } catch (_: Exception) { false }
    }

    fun imageToBase64(path: CharSequence, format: CharSequence = "png"): String {
        return try {
            val bmp = BitmapFactory.decodeFile(path.toString()) ?: return ""
            val baos = ByteArrayOutputStream()
            val fmt = if (format.toString().lowercase() == "jpg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
            bmp.compress(fmt, 90, baos)
            bmp.recycle()
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) { "" }
    }

    fun imageFromBase64(base64: CharSequence, outPath: CharSequence): Boolean {
        return try {
            val bytes = Base64.decode(base64.toString(), Base64.NO_WRAP)
            File(outPath.toString()).writeBytes(bytes)
            true
        } catch (_: Exception) { false }
    }

    fun imageSave(path: CharSequence, outPath: CharSequence): Boolean {
        return try {
            File(path.toString()).copyTo(File(outPath.toString()), overwrite = true)
            true
        } catch (_: Exception) { false }
    }

    fun imagePixelColor(path: CharSequence, x: Int, y: Int): String {
        return try {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bmp = BitmapFactory.decodeFile(path.toString(), opts) ?: return ""
            val pixel = bmp.getPixel(x, y)
            bmp.recycle()
            String.format("#%08X", pixel)
        } catch (_: Exception) { "" }
    }

    fun imageWidth(path: CharSequence): Int {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path.toString(), opts)
            opts.outWidth
        } catch (_: Exception) { -1 }
    }

    fun imageHeight(path: CharSequence): Int {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path.toString(), opts)
            opts.outHeight
        } catch (_: Exception) { -1 }
    }
}
