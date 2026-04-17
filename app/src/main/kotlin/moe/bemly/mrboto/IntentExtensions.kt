package moe.bemly.mrboto

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log

interface IntentMixin {
    val mruby: MRuby

    fun intentView(url: CharSequence): Boolean {
        val activity = this as Activity
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()))
            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "intentView failed: ${e.message}")
            false
        }
    }

    fun intentSend(text: CharSequence, subject: CharSequence = ""): Boolean {
        val activity = this as Activity
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text.toString())
                if (subject.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, subject.toString())
            }
            activity.startActivity(Intent.createChooser(intent, "Share"))
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "intentSend failed: ${e.message}")
            false
        }
    }

    fun intentAction(action: CharSequence, data: CharSequence = "", type: CharSequence = ""): Boolean {
        val activity = this as Activity
        return try {
            val intent = Intent(action.toString()).apply {
                if (data.isNotEmpty()) setData(Uri.parse(data.toString()))
                if (type.isNotEmpty()) setType(type.toString())
            }
            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "intentAction failed: ${e.message}")
            false
        }
    }
}
