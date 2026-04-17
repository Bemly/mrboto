package moe.bemly.mrboto

import android.graphics.Bitmap
import android.util.Log
import com.equationl.ncnnandroidppocr.OCR
import com.equationl.ncnnandroidppocr.bean.Device
import com.equationl.ncnnandroidppocr.bean.DrawModel
import com.equationl.ncnnandroidppocr.bean.ImageSize
import com.equationl.ncnnandroidppocr.bean.ModelType

// PaddleOCR v5 (NCNN) integration

private var ocrInstance: OCR? = null

fun MrbotoActivityBase.ocrInit(): Boolean {
    if (ocrInstance != null) return true
    return try {
        val ocr = OCR()
        val ok = ocr.initModelFromAssert(assets, ModelType.Mobile, ImageSize.Size720, Device.CPU)
        if (ok) ocrInstance = ocr
        ok
    } catch (e: Exception) {
        Log.w("Mrboto", "ocrInit failed: ${e.message}")
        false
    }
}

fun MrbotoActivityBase.ocrRecognize(imagePath: CharSequence): String {
    val ocr = ocrInstance ?: return ""
    return try {
        val result = ocr.detectImagePath(imagePath.toString(), DrawModel.None)
        result?.text ?: ""
    } catch (e: Exception) {
        Log.w("Mrboto", "ocrRecognize failed: ${e.message}")
        ""
    }
}

fun MrbotoActivityBase.ocrRecognizeFromPath(imagePath: CharSequence): String {
    return ocrRecognize(imagePath)
}

fun MrbotoActivityBase.ocrDetect(imagePath: CharSequence): String {
    return ocrRecognize(imagePath)
}

fun MrbotoActivityBase.ocrDetectBitmap(bitmap: Bitmap): String {
    val ocr = ocrInstance ?: return ""
    return try {
        val result = ocr.detectBitmap(bitmap, DrawModel.None)
        result?.text ?: ""
    } catch (e: Exception) {
        Log.w("Mrboto", "ocrDetectBitmap failed: ${e.message}")
        ""
    }
}

fun MrbotoActivityBase.ocrRelease(): Boolean {
    return try {
        ocrInstance?.release()
        ocrInstance = null
        true
    } catch (e: Exception) {
        Log.w("Mrboto", "ocrRelease failed: ${e.message}")
        false
    }
}
