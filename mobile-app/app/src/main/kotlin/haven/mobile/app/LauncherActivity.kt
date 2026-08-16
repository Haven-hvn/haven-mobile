package haven.mobile.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * No Hilt, no Compose, no Reown — just decides where to go.
 * If a recent haven_crash.log exists (previous launch died before MainActivity), show CrashActivity
 * immediately. Otherwise forward to MainActivity. This breaks the crash loop where Hilt/ContentProvider
 * crashes never reach MainActivity.maybeShowCrashDialog().
 */
class LauncherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crashFile = try {
            getExternalFilesDir(null)?.resolve("haven_crash.log")?.takeIf { it.exists() }
                ?: filesDir.resolve("haven_crash.log").takeIf { it.exists() }
        } catch (_: Exception) { null }
        val hasRecentCrash = try {
            crashFile != null && crashFile.length() > 0 && System.currentTimeMillis() - crashFile.lastModified() < 5 * 60 * 1000
        } catch (_: Exception) { false }

        if (hasRecentCrash) {
            try {
                val text = crashFile!!.readText().take(12000)
                val intent = Intent(this, CrashActivity::class.java).apply {
                    putExtra(CrashActivity.EXTRA_CRASH, text)
                }
                startActivity(intent)
                finish()
                return
            } catch (_: Exception) {}
        }
        // No recent crash — proceed to real app
        try {
            startActivity(Intent(this, MainActivity::class.java))
        } catch (e: Throwable) {
            // If MainActivity itself can't start (Hilt crash), show the throw directly
            try {
                val crashText = "Launcher -> MainActivity failed:\n${e.stackTraceToString()}\n"
                val crashLog = getExternalFilesDir(null)?.resolve("haven_crash.log")
                    ?: filesDir.resolve("haven_crash.log")
                crashLog.writeText(crashText)
                val intent = Intent(this, CrashActivity::class.java).apply {
                    putExtra(CrashActivity.EXTRA_CRASH, crashText.take(12000))
                }
                startActivity(intent)
            } catch (_: Exception) {}
        }
        finish()
    }
}
