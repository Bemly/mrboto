package moe.bemly.mrboto

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import java.util.concurrent.ConcurrentHashMap

private val overlayViews = ConcurrentHashMap<Int, View>()

interface OverlayMixin {
    val mruby: MRuby

    /**
     * Check if overlay permission is granted.
     * Called from Ruby via call_java_method("checkOverlayPermission").
     */
    fun checkOverlayPermission(): Boolean {
        val activity = this as Activity
        return android.provider.Settings.canDrawOverlays(activity)
    }

    /**
     * Open system settings page for overlay permission.
     * Called from Ruby via call_java_method("openOverlaySettings").
     */
    fun openOverlaySettings(): Boolean {
        val activity = this as Activity
        return try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "openOverlaySettings failed: ${e.message}")
            false
        }
    }

    fun overlayShow(viewId: Int, x: Int, y: Int, width: Int = -2, height: Int = -2): Int {
        val activity = this as Activity
        return try {
            val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val overlayView = View(activity)
            val params = WindowManager.LayoutParams(
                width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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

    fun overlayRemove(overlayId: Int): Boolean {
        val activity = this as Activity
        return try {
            val view = overlayViews.remove(overlayId) ?: return false
            val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(view)
            true
        } catch (_: Exception) { false }
    }
}
