package moe.bemly.mrboto

import android.content.Intent
import android.net.Uri
import android.util.Log

fun MrbotoActivityBase.intentView(url: CharSequence): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()))
        startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w("Mrboto", "intentView failed: ${e.message}")
        false
    }
}

fun MrbotoActivityBase.intentSend(text: CharSequence, subject: CharSequence = ""): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text.toString())
            if (subject.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, subject.toString())
        }
        startActivity(Intent.createChooser(intent, "Share"))
        true
    } catch (e: Exception) {
        Log.w("Mrboto", "intentSend failed: ${e.message}")
        false
    }
}

fun MrbotoActivityBase.intentAction(action: CharSequence, data: CharSequence = "", type: CharSequence = ""): Boolean {
    return try {
        val intent = Intent(action.toString()).apply {
            if (data.isNotEmpty()) setData(Uri.parse(data.toString()))
            if (type.isNotEmpty()) setType(type.toString())
        }
        startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w("Mrboto", "intentAction failed: ${e.message}")
        false
    }
}
