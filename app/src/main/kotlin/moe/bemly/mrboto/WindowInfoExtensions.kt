package moe.bemly.mrboto

import android.app.Activity
import android.util.Log
import android.view.ViewGroup

interface WindowInfoMixin {
    val mruby: MRuby

    fun getCurrentActivityName(): String {
        return try {
            javaClass.name
        } catch (e: Exception) {
            Log.w("Mrboto", "getCurrentActivityName failed: ${e.message}")
            ""
        }
    }

    fun getTopActivityPackage(): String {
        val activity = this as Activity
        return try {
            activity.packageName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun getCurrentLayoutInfo(): String {
        val activity = this as Activity
        return try {
            val wm = activity.window ?: return "{}"
            val decorView = wm.decorView
            val obj = org.json.JSONObject()
            obj.put("width", decorView.width)
            obj.put("height", decorView.height)
            obj.put("class", decorView.javaClass.name)
            val vg = decorView as? ViewGroup
            if (vg != null) {
                obj.put("childCount", vg.childCount)
            }
            obj.toString()
        } catch (_: Exception) { "{}" }
    }
}
