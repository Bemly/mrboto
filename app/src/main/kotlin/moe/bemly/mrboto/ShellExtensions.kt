package moe.bemly.mrboto

import android.util.Log
import java.util.concurrent.TimeUnit

interface ShellMixin {
    val mruby: MRuby

    fun shellExec(command: CharSequence, timeout: Int = 10): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command.toString()))
            val finished = process.waitFor((timeout * 1000).toLong(), TimeUnit.MILLISECONDS)
            if (finished) {
                val stdout = process.inputStream.bufferedReader().readText()
                val stderr = process.errorStream.bufferedReader().readText()
                if (stderr.isNotEmpty()) "$stdout\n$stderr".trim() else stdout.trim()
            } else {
                process.destroyForcibly()
                "TIMEOUT"
            }
        } catch (e: Exception) {
            Log.w("Mrboto", "shellExec failed: ${e.message}")
            "ERROR: ${e.message}"
        }
    }
}
