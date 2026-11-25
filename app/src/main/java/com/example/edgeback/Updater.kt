package com.example.edgeback

import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

object Updater {
    data class Info(val version: String, val apkUrl: String, val notes: String)

    /** يرجع معلومات التحديث إذا اكو إصدار أحدث، وإلا null */
    fun check(): Info? {
        val c = URL("https://api.github.com/repos/${BuildConfig.REPO}/releases/latest")
            .openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 10000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        if (c.responseCode != 200) return null
        val j = JSONObject(c.inputStream.bufferedReader().readText())
        val tag = j.getString("tag_name").removePrefix("v")
        val assets = j.getJSONArray("assets")
        var apk: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) { apk = a.getString("browser_download_url"); break }
        }
        if (apk == null || !isNewer(tag, BuildConfig.VERSION_NAME)) return null
        return Info(tag, apk, j.optString("body"))
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val l = local.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}

/** تحميل يدعم الإيقاف المؤقت والاستئناف (عن طريق Range) */
class Downloader(
    private val url: String,
    private val file: File,
    private val onProgress: (Long, Long) -> Unit,
    private val onDone: () -> Unit,
    private val onError: (String) -> Unit
) {
    @Volatile private var stop = false

    fun start() {
        stop = false
        Thread {
            try {
                val existing = if (file.exists()) file.length() else 0L
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 15000; c.readTimeout = 15000
                if (existing > 0) c.setRequestProperty("Range", "bytes=$existing-")
                val code = c.responseCode
                if (code == 416) { onDone(); return@Thread }   // الملف مكتمل
                val resumed = code == 206
                if (code != 200 && code != 206) { onError("HTTP $code"); return@Thread }
                val start = if (resumed) existing else 0L
                val total = start + c.contentLengthLong
                RandomAccessFile(file, "rw").use { raf ->
                    if (!resumed) raf.setLength(0)
                    raf.seek(start)
                    val buf = ByteArray(32 * 1024)
                    var done = start
                    c.inputStream.use { ins ->
                        while (!stop) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            raf.write(buf, 0, n)
                            done += n
                            onProgress(done, total)
                        }
                    }
                    if (!stop && done >= total) onDone()
                }
            } catch (e: Exception) {
                if (!stop) onError(e.message ?: "خطأ")
            }
        }.start()
    }

    fun pause() { stop = true }
    fun cancel() { stop = true; file.delete() }
}
