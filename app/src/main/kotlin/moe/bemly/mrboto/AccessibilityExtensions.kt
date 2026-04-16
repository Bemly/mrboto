package moe.bemly.mrboto

import android.view.accessibility.AccessibilityManager
import org.json.JSONArray

fun MrbotoActivityBase.accessibilityEnabled(): Boolean {
    val am = getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return am.isEnabled
}

fun MrbotoActivityBase.accessibilityTouchExploration(): Boolean {
    val am = getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return am.isTouchExplorationEnabled
}
