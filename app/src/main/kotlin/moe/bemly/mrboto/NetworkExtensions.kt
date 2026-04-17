package moe.bemly.mrboto

import android.util.Log
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

interface NetworkMixin {
    val mruby: MRuby

    fun httpGetEx(url: CharSequence, headersJson: CharSequence? = null): String {
        return try {
            val conn = URL(url.toString()).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (headersJson != null) {
                val json = org.json.JSONObject(headersJson.toString())
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    conn.setRequestProperty(key, json.getString(key))
                }
            }
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            Log.w("Mrboto", "httpGetEx failed: ${e.message}")
            ""
        }
    }

    fun httpPostEx(url: CharSequence, body: CharSequence, headersJson: CharSequence? = null): String {
        return try {
            val conn = URL(url.toString()).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (headersJson != null) {
                val json = org.json.JSONObject(headersJson.toString())
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    conn.setRequestProperty(key, json.getString(key))
                }
            }
            DataOutputStream(conn.outputStream).use { it.write(body.toString().toByteArray()) }
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            Log.w("Mrboto", "httpPostEx failed: ${e.message}")
            ""
        }
    }

    fun httpUpload(url: CharSequence, filePath: CharSequence, fieldName: CharSequence = "file"): String {
        return try {
            val boundary = "----MrbotoBoundary${System.currentTimeMillis()}"
            val conn = URL(url.toString()).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            val file = File(filePath.toString())
            val output = DataOutputStream(conn.outputStream)
            output.writeBytes("--$boundary\r\n")
            output.writeBytes("Content-Disposition: form-data; name=\"${fieldName}\"; filename=\"${file.name}\"\r\n\r\n")
            file.inputStream().use { it.copyTo(output) }
            output.writeBytes("\r\n--$boundary--\r\n")
            output.flush()
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            Log.w("Mrboto", "httpUpload failed: ${e.message}")
            ""
        }
    }

    fun httpStream(url: CharSequence, headersJson: CharSequence? = null): String {
        return httpGetEx(url, headersJson)
    }
}
