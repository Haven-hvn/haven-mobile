package haven.mobile.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import haven.mobile.core.wallet.ReownBootstrap
import timber.log.Timber

@HiltAndroidApp
class HavenApplication : Application() {
    override fun attachBaseContext(base: android.content.Context?) {
        super.attachBaseContext(base)
        try { StartupTracer.log(base ?: this, "attachBaseContext") } catch (_: Exception) {}
        try { if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree()) } catch (_: Exception) {}
        try { EarlyCrashHandler.install(base ?: this) } catch (_: Exception) {}
    }

    override fun onCreate() {
        super.onCreate()
        try { StartupTracer.log(this, "Application.onCreate start") } catch (_: Exception) {}
        try { EarlyCrashHandler.install(this) } catch (_: Exception) {}
        // Wrap init so a Reown failure never kills the app before MainActivity can show onboarding
        try { StartupTracer.log(this, "Application before initReown") } catch (_: Exception) {}
        try { initReownIfNeeded() } catch (e: Throwable) {
            try { StartupTracer.log(this, "initReownIfNeeded Throwable", e.stackTraceToString().take(800)) } catch (_: Exception) {}
            try { Timber.e(e, "initReownIfNeeded crashed") } catch (_: Exception) {}
            try {
                val crashText = "initReown: ${e.stackTraceToString()}\n"
                val crashLog = getExternalFilesDir(null)?.resolve("haven_crash.log")
                    ?: filesDir.resolve("haven_crash.log")
                crashLog.writeText(crashText)
            } catch (_: Exception) {}
        }
        try { StartupTracer.log(this, "Application.onCreate end") } catch (_: Exception) {}
    }

    private fun initReownIfNeeded() {
        val projectId = BuildConfig.WALLET_PROJECT_ID
        try { StartupTracer.log(this, "initReown check projectId=$projectId") } catch (_: Exception) {}
        val ok = ReownBootstrap.initialize(
            projectId = projectId,
            application = this,
            appName = "Haven",
            appDescription = "Haven — gated media",
            appIconUrl = "",
            redirectUrl = "haven://connect"
        )
        if (!ok) Timber.w("Reown init skipped (blank/dummy projectId or no Play Services)")
    }
}