package moe.bemly.mrboto

import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import java.util.concurrent.ConcurrentHashMap

private val overlayViews = ConcurrentHashMap<Int, View>()

fun MrbotoActivityBase.overlayShow(viewId: Int, x: Int, y: Int, width: Int = -2, height: Int = -2): Int {
    return try {
        val wm = getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
        val overlayView = View(this)
        val params = WindowManager.LayoutParams(
            width, height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = x
        params.y = y
        wm.addView(overlayView, params)
        val id = overlayView.hashCode()
        overlayViews[id] = overlayView
        id
    } catch (e: Exception) {
        Log.w("Mrboto", "overlayShow failed: ${e.message}")
        -1
    }
}

fun MrbotoActivityBase.overlayRemove(overlayId: Int): Boolean {
    return try {
        val view = overlayViews.remove(overlayId) ?: return false
        val wm = getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
        wm.removeView(view)
        true
    } catch (_: Exception) { false }
}
