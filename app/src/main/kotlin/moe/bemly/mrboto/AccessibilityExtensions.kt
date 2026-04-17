package moe.bemly.mrboto

import android.app.Activity
import android.content.Context
import android.view.accessibility.AccessibilityManager

interface AccessibilityMixin {
    val mruby: MRuby

    fun accessibilityEnabled(): Boolean {
        val activity = this as Activity
        val am = activity.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.isEnabled
    }

    fun accessibilityTouchExploration(): Boolean {
        val activity = this as Activity
        val am = activity.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.isTouchExplorationEnabled
    }
}
