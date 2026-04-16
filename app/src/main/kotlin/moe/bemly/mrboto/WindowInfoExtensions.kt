package moe.bemly.mrboto

import android.app.ActivityManager
import android.util.Log
import org.json.JSONObject

fun MrbotoActivityBase.getCurrentActivityName(): String {
    return try {
        javaClass.name
    } catch (e: Exception) {
        Log.w("Mrboto", "getCurrentActivityName failed: ${e.message}")
        ""
    }
}

fun MrbotoActivityBase.getTopActivityPackage(): String {
    return try {
        packageName ?: ""
    } catch (e: Exception) {
        ""
    }
}

fun MrbotoActivityBase.getCurrentLayoutInfo(): String {
    return try {
        val wm = window ?: return "{}"
        val decorView = wm.decorView
        val obj = JSONObject()
        obj.put("width", decorView.width)
        obj.put("height", decorView.height)
        obj.put("class", decorView.javaClass.name)
        val vg = decorView as? android.view.ViewGroup
        if (vg != null) {
            obj.put("childCount", vg.childCount)
        }
        obj.toString()
    } catch (_: Exception) { "{}" }
}
