package moe.bemly.mrboto

import android.os.Build

fun MrbotoActivityBase.predictiveBackEnabled(): Boolean {
    return Build.VERSION.SDK_INT >= 33
}
