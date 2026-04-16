package moe.bemly.mrboto

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

fun MrbotoActivityBase.getColorAt(bitmapPath: CharSequence, x: Int, y: Int): String {
    return try {
        val bmp = android.graphics.BitmapFactory.decodeFile(bitmapPath.toString())
            ?: return ""
        val pixel = bmp.getPixel(x, y)
        bmp.recycle()
        String.format("#%06X", 0xFFFFFF and pixel)
    } catch (_: Exception) { "" }
}

fun MrbotoActivityBase.findColor(bitmapPath: CharSequence, colorHex: CharSequence, region: CharSequence = ""): String {
    return try {
        val bmp = android.graphics.BitmapFactory.decodeFile(bitmapPath.toString()) ?: return "[]"
        val targetColor = Color.parseColor(colorHex.toString())
        val r = parseRegion(region.toString(), bmp.width, bmp.height)
        val results = JSONArray()
        for (y in r[1] until r[3]) {
            for (x in r[0] until r[2]) {
                if (bmp.getPixel(x, y) == targetColor) {
                    results.put(JSONObject().put("x", x).put("y", y))
                }
            }
        }
        bmp.recycle()
        results.toString()
    } catch (_: Exception) { "[]" }
}

fun MrbotoActivityBase.findColorFuzzy(bitmapPath: CharSequence, colorHex: CharSequence, threshold: Int = 32, region: CharSequence = ""): String {
    return try {
        val bmp = android.graphics.BitmapFactory.decodeFile(bitmapPath.toString()) ?: return "[]"
        val targetColor = Color.parseColor(colorHex.toString())
        val tr = Color.red(targetColor)
        val tg = Color.green(targetColor)
        val tb = Color.blue(targetColor)
        val r = parseRegion(region.toString(), bmp.width, bmp.height)
        val results = JSONArray()
        for (y in r[1] until r[3]) {
            for (x in r[0] until r[2]) {
                val pixel = bmp.getPixel(x, y)
                if (kotlin.math.abs(Color.red(pixel) - tr) <= threshold &&
                    kotlin.math.abs(Color.green(pixel) - tg) <= threshold &&
                    kotlin.math.abs(Color.blue(pixel) - tb) <= threshold) {
                    results.put(JSONObject().put("x", x).put("y", y))
                }
            }
        }
        bmp.recycle()
        results.toString()
    } catch (_: Exception) { "[]" }
}

private fun parseRegion(region: String, w: Int, h: Int): IntArray {
    if (region.isEmpty()) return intArrayOf(0, 0, w, h)
    return try {
        val arr = JSONArray(region)
        intArrayOf(arr.getInt(0), arr.getInt(1), arr.getInt(2), arr.getInt(3))
    } catch (_: Exception) { intArrayOf(0, 0, w, h) }
}
