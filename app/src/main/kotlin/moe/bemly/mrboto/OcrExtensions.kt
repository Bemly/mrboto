package moe.bemly.mrboto

import android.util.Log

// PaddleOCR reserved interface — requires PaddleOCR SDK integration
// These methods are placeholders and will return empty results until SDK is connected

fun MrbotoActivityBase.ocrRecognize(imagePath: CharSequence): String {
    Log.w("Mrboto", "ocrRecognize: PaddleOCR not integrated yet")
    return ""
}

fun MrbotoActivityBase.ocrRecognizeFromPath(imagePath: CharSequence): String {
    return ocrRecognize(imagePath)
}
