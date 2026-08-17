package haven.mobile.app

import android.app.Application
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.android.relay.ConnectionType
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import com.reown.appkit.presets.AppKitChainsPresets
import dagger.hilt.android.HiltAndroidApp
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
        // If previous launch crashed, show the crash immediately and skip risky init to break the crash loop
        try {
            val crashFile = getExternalFilesDir(null)?.resolve("haven_crash.log")?.takeIf { it.exists() }
                ?: filesDir.resolve("haven_crash.log").takeIf { it.exists() }
            if (crashFile != null && crashFile.length() > 0) {
                val ageMs = System.currentTimeMillis() - crashFile.lastModified()
                if (ageMs < 5 * 60 * 1000) {
                    try {
                        val text = crashFile.readText().take(12000)
                        try { StartupTracer.log(this, "Application.onCreate showing CrashActivity from previous crash", text.take(400)) } catch (_: Exception) {}
                        val intent = android.content.Intent(this, CrashActivity::class.java).apply {
                            putExtra(CrashActivity.EXTRA_CRASH, text)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(intent)
                        // Don't re-init Reown until user dismisses crash screen
                        return
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
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
        if (projectId.isBlank() || projectId.startsWith("dummy-")) {
            Timber.w("WALLET_PROJECT_ID blank/dummy — Reown init skipped (projectId=$projectId)")
            return
        }
        // Physical-device guard: Reown/CoreClient needs Play Services; on devices without it it throws NoClassDefFoundError which Exception does not catch
        try {
            val clazz = Class.forName("com.google.android.gms.common.GoogleApiAvailability")
            val getInstance = clazz.getMethod("getInstance")
            val availability = getInstance.invoke(null)
            val isAvailable = clazz.getMethod("isGooglePlayServicesAvailable", android.content.Context::class.java)
            val psStatus = isAvailable.invoke(availability, this) as Int
            if (psStatus != 0) { // ConnectionResult.SUCCESS = 0
                Timber.w("Play Services not available ($psStatus) — Reown init skipped, onboarding will show fallback")
                return
            }
        } catch (e: Throwable) {
            Timber.w(e, "Play Services check failed — Reown init skipped")
            return
        }
        try {
            val appMetaData = Core.Model.AppMetaData(
                name = "Haven",
                description = "Haven — gated media",
                url = "https://haven",
                icons = emptyList(),
                redirect = "haven://connect",
                appLink = "https://haven"
            )
            CoreClient.initialize(
                projectId = projectId,
                connectionType = ConnectionType.AUTOMATIC,
                application = this,
                metaData = appMetaData
            ) { error -> Timber.e(error.throwable, "CoreClient init error") }
            AppKit.initialize(
                init = Modal.Params.Init(core = CoreClient),
                onSuccess = { Timber.i("AppKit initialized in HavenApplication") },
                onError = { error -> Timber.e(error.throwable, "AppKit init error") }
            )
            AppKit.setChains(AppKitChainsPresets.ethChains.values.toList())
        } catch (e: Throwable) {
            if (e.message?.contains("already", ignoreCase = true) != true) {
                Timber.e(e, "Reown init failed (Throwable)")
            }
        }
    }
}