package moe.bemly.mrboto

import android.app.Activity
import android.content.Context
import android.util.Log
import java.io.File
import java.nio.charset.Charset

interface FileEncodingMixin {
    val mruby: MRuby

    fun fileReadEncoding(name: CharSequence, encoding: CharSequence): String {
        val activity = this as Activity
        return try {
            val charset = Charset.forName(encoding.toString())
            activity.openFileInput(name.toString()).readBytes().toString(charset)
        } catch (e: Exception) {
            Log.w("Mrboto", "fileReadEncoding failed: ${e.message}")
            ""
        }
    }

    fun fileWriteEncoding(name: CharSequence, content: CharSequence, encoding: CharSequence): Boolean {
        val activity = this as Activity
        return try {
            val charset = Charset.forName(encoding.toString())
            activity.openFileOutput(name.toString(), Context.MODE_PRIVATE).use {
                it.write(content.toString().toByteArray(charset))
            }
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "fileWriteEncoding failed: ${e.message}")
            false
        }
    }

    fun fileListDir(path: CharSequence): String {
        return try {
            val dir = if (path.isEmpty()) (this as Activity).filesDir else File(path.toString())
            val arr = org.json.JSONArray()
            (dir.listFiles() ?: emptyArray()).forEach { arr.put(it.name) }
            arr.toString()
        } catch (e: Exception) {
            "[]"
        }
    }

    fun fileMkdir(path: CharSequence): Boolean {
        return try {
            val dir = File(path.toString())
            if (dir.isDirectory) true else dir.mkdirs()
        } catch (_: Exception) { false }
    }

    fun fileDeleteDir(path: CharSequence): Boolean {
        return try {
            val dir = File(path.toString())
            dir.deleteRecursively()
        } catch (_: Exception) { false }
    }

    fun fileExistsPath(path: CharSequence): Boolean {
        return File(path.toString()).exists()
    }

    fun fileIsDir(path: CharSequence): Boolean {
        return File(path.toString()).isDirectory
    }
}
