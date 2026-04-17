package moe.bemly.mrboto

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.ViewConfiguration

interface GestureMixin {
    val mruby: MRuby

    fun gestureClick(x: Int, y: Int): Boolean {
        return dispatchGesture {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            GestureDescription.Builder().addStroke(
                GestureDescription.StrokeDescription(path, 0, 100)
            ).build()
        }
    }

    fun gestureLongClick(x: Int, y: Int): Boolean {
        return dispatchGesture {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            GestureDescription.Builder().addStroke(
                GestureDescription.StrokeDescription(path, 0, ViewConfiguration.getLongPressTimeout().toLong())
            ).build()
        }
    }

    fun gestureSwipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 300): Boolean {
        return dispatchGesture {
            val path = Path().apply {
                moveTo(x1.toFloat(), y1.toFloat())
                lineTo(x2.toFloat(), y2.toFloat())
            }
            GestureDescription.Builder().addStroke(
                GestureDescription.StrokeDescription(path, 0, durationMs.toLong())
            ).build()
        }
    }

    fun gestureScroll(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 300): Boolean {
        return gestureSwipe(x1, y1, x2, y2, durationMs)
    }

    private fun dispatchGesture(builder: () -> GestureDescription): Boolean {
        return try {
            // Requires AccessibilityService — placeholder for now
            Log.d("Mrboto", "gesture dispatch requires AccessibilityService")
            false
        } catch (_: Exception) { false }
    }
}
