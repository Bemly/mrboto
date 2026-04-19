package moe.bemly.mrboto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.Reader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File

/**
 * QR code scanning and generation functionality.
 *
 * Uses ZXing library to detect QR codes in images and generate
 * new QR code images from text data.
 */
interface QRCodeMixin {
    val mruby: MRuby

    /**
     * Scan for QR codes in an image file.
     * @param imagePath Absolute path to the image file
     * @return JSON array string containing all detected QR code texts
     */
    fun scanQRCode(imagePath: CharSequence): String {
        val activity = this as Activity
        return try {
            val file = File(imagePath.toString())
            if (!file.exists()) return "[]"

            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeFile(file.absolutePath, options)
            } else {
                BitmapFactory.decodeFile(file.absolutePath)
            }

            if (bitmap == null) return "[]"

            val results = scanQRCodeFromBitmap(bitmap)
            bitmap.recycle()

            val arr = org.json.JSONArray()
            for (result in results) {
                arr.put(result)
            }
            arr.toString()
        } catch (e: Exception) {
            Log.w("Mrboto", "scanQRCode failed: ${e.message}")
            "[]"
        }
    }

    /**
     * Generate a QR code image from text.
     * @param text Text content to encode in the QR code
     * @param outputPath Relative path in cache directory for output image
     * @param size Size of the QR code image in pixels (default: 300)
     * @return true if image was successfully generated and saved
     */
    fun generateQRCode(text: CharSequence, outputPath: CharSequence, size: Int = 300): Boolean {
        val activity = this as Activity
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(text.toString(), BarcodeFormat.QR_CODE, size, size)

            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y])
                        android.graphics.Color.BLACK
                    else
                        android.graphics.Color.WHITE)
                }
            }

            val outputFile = File(activity.cacheDir, outputPath.toString())
            outputFile.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "generateQRCode failed: ${e.message}")
            false
        }
    }

    /**
     * Internal method to scan QR codes from a Bitmap object.
     * @param bitmap Image data
     * @return List of detected QR code text contents
     */
    private fun scanQRCodeFromBitmap(bitmap: Bitmap): List<String> {
        val results = mutableListOf<String>()

        try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source: LuminanceSource = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val reader: Reader = QRCodeMultiReader()
            val hints = mapOf<DecodeHintType, Any>(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            )

            val decodeResults = reader.decodeMultiple(binaryBitmap, hints)
            for (result in decodeResults) {
                results.add(result.text)
            }
        } catch (e: Exception) {
            Log.w("Mrboto", "scanQRCodeFromBitmap failed: ${e.message}")
        }

        return results
    }
}
