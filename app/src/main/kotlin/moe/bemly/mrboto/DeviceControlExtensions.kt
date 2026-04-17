package moe.bemly.mrboto

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log

interface DeviceControlMixin {
    val mruby: MRuby

    fun setVolume(streamType: Int, level: Int, showUi: Boolean = false): Boolean {
        val activity = this as Activity
        return try {
            val am = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
            am.setStreamVolume(streamType, level, flags)
            true
        } catch (_: Exception) { false }
    }

    fun getVolume(streamType: Int): Int {
        val activity = this as Activity
        return try {
            val am = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.getStreamVolume(streamType)
        } catch (_: Exception) { -1 }
    }

    fun setBrightness(level: Int): Boolean {
        val activity = this as Activity
        return try {
            val lp = activity.window.attributes
            lp.screenBrightness = level / 255f
            activity.window.attributes = lp
            true
        } catch (_: Exception) { false }
    }

    fun getBrightness(): Int {
        val activity = this as Activity
        return try {
            Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (_: Exception) { -1 }
    }

    fun vibrate(durationMs: Int = 200): Boolean {
        val activity = this as Activity
        return try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "vibrate failed: ${e.message}")
            false
        }
    }

    fun vibratePattern(patternJson: CharSequence, repeat: Int = -1): Boolean {
        val activity = this as Activity
        return try {
            val arr = org.json.JSONArray(patternJson.toString())
            val pattern = LongArray(arr.length()) { arr.getLong(it) }
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat))
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "vibratePattern failed: ${e.message}")
            false
        }
    }
}
