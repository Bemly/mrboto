package moe.bemly.mrboto

import android.util.Log
import org.json.JSONArray
import java.io.File
import java.nio.charset.Charset

fun MrbotoActivityBase.fileReadEncoding(name: CharSequence, encoding: CharSequence): String {
    return try {
        val charset = Charset.forName(encoding.toString())
        openFileInput(name.toString()).readBytes().toString(charset)
    } catch (e: Exception) {
        Log.w("Mrboto", "fileReadEncoding failed: ${e.message}")
        ""
    }
}

fun MrbotoActivityBase.fileWriteEncoding(name: CharSequence, content: CharSequence, encoding: CharSequence): Boolean {
    return try {
        val charset = Charset.forName(encoding.toString())
        openFileOutput(name.toString(), android.content.Context.MODE_PRIVATE).use {
            it.write(content.toString().toByteArray(charset))
        }
        true
    } catch (e: Exception) {
        Log.w("Mrboto", "fileWriteEncoding failed: ${e.message}")
        false
    }
}

fun MrbotoActivityBase.fileListDir(path: CharSequence): String {
    return try {
        val dir = if (path.isEmpty()) filesDir else File(path.toString())
        val arr = JSONArray()
        (dir.listFiles() ?: emptyArray()).forEach { arr.put(it.name) }
        arr.toString()
    } catch (e: Exception) {
        "[]"
    }
}

fun MrbotoActivityBase.fileMkdir(path: CharSequence): Boolean {
    return try {
        File(path.toString()).mkdirs()
    } catch (_: Exception) { false }
}

fun MrbotoActivityBase.fileDeleteDir(path: CharSequence): Boolean {
    return try {
        val dir = File(path.toString())
        dir.deleteRecursively()
    } catch (_: Exception) { false }
}

fun MrbotoActivityBase.fileExistsPath(path: CharSequence): Boolean {
    return File(path.toString()).exists()
}

fun MrbotoActivityBase.fileIsDir(path: CharSequence): Boolean {
    return File(path.toString()).isDirectory
}
