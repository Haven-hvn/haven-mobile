package haven.mobile.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import timber.log.Timber

/**
 * Installs the uncaught handler before Hilt's ContentProvider (initOrder) so a Hilt init crash is captured.
 * Hilt's generated provider uses default initOrder 0; we use 100 to run first.
 */
class EarlyCrashProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        try {
            EarlyCrashHandler.install(context!!)
        } catch (_: Exception) {}
        return true
    }
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}

object EarlyCrashHandler {
    @Volatile private var installed = false
    fun install(appContext: android.content.Context) {
        if (installed) return
        installed = true
        val app = appContext.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try { Timber.e(throwable, "Uncaught in ${thread.name}") } catch (_: Exception) {}
            val startup = try { StartupTracer.read(app) } catch (_: Exception) { null }
            val crashText = buildString {
                append("thread=${thread.name}\n")
                append(throwable.stackTraceToString())
                append("\n\n--- Startup trace ---\n")
                append(startup?.take(6000) ?: "no trace")
            }
            try {
                val crashLog = app.getExternalFilesDir(null)?.resolve("haven_crash.log")
                    ?: app.filesDir.resolve("haven_crash.log")
                crashLog.writeText(crashText)
            } catch (_: Exception) {}
            try { StartupTracer.log(app, "Uncaught ${thread.name} ${throwable::class.simpleName}", throwable.message?.take(300)) } catch (_: Exception) {}
            // Try to show CrashActivity directly — may be blocked on Android 10+ if in background, so also rely on file for next launch
            try {
                val intent = android.content.Intent(app, CrashActivity::class.java).apply {
                    putExtra(CrashActivity.EXTRA_CRASH, crashText.take(12000))
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                app.startActivity(intent)
            } catch (_: Exception) {}
            try { previous?.uncaughtException(thread, throwable) } catch (_: Exception) {
                try { android.os.Process.killProcess(android.os.Process.myPid()) } catch (_: Exception) {}
                try { kotlin.system.exitProcess(10) } catch (_: Exception) {}
            }
        }
    }
}
