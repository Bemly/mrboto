package moe.bemly.mrboto

import android.os.Build

interface PredictiveBackMixin {
    val mruby: MRuby

    fun predictiveBackEnabled(): Boolean {
        return Build.VERSION.SDK_INT >= 33
    }
}
