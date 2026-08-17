package haven.mobile.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StartupTracer {
    private const val FILE = "haven_startup.log"
    private const val MAX = 40000

    fun log(ctx: Context?, step: String, extra: String? = null) {
        val msg = buildString {
            append(SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date()))
            append(" ")
            append(step)
            if (extra != null) { append(" | "); append(extra.take(800)) }
        }
        try { android.util.Log.i("HavenStartup", msg) } catch (_: Exception) {}
        try {
            val c = ctx?.applicationContext ?: return
            val f = c.getExternalFilesDir(null)?.let { File(it, FILE) } ?: File(c.filesDir, FILE)
            val prev = if (f.exists()) f.readText().take(MAX - 2000) else ""
            val next = (msg + "\n" + prev).take(MAX)
            f.writeText(next)
        } catch (_: Exception) {}
    }

    fun read(ctx: Context?): String? = try {
        val c = ctx?.applicationContext ?: return null
        val f = c.getExternalFilesDir(null)?.let { File(it, FILE) }?.takeIf { it.exists() }
            ?: File(c.filesDir, FILE).takeIf { it.exists() } ?: return null
        f.readText().take(12000)
    } catch (_: Exception) { null }

    fun clear(ctx: Context?) {
        try {
            val c = ctx?.applicationContext ?: return
            c.getExternalFilesDir(null)?.let { File(it, FILE) }?.delete()
            File(c.filesDir, FILE).delete()
        } catch (_: Exception) {}
    }
}
