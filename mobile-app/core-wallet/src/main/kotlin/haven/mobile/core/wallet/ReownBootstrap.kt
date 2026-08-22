package haven.mobile.core.wallet

import android.app.Application
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.android.relay.ConnectionType
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import com.reown.appkit.presets.AppKitChainsPresets

/**
 * Single idempotent entry point for Reown (CoreClient + AppKit) initialization.
 *
 * Both HavenApplication (early, at Application.onCreate) and WalletSessionImpl (lazily, before
 * connect) call this. The second caller is a no-op thanks to the [attempted] flag, so Reown can
 * never be initialized twice in one process — calling AppKit.initialize twice throws
 * AlreadyInitializedException and breaks the relay connection.
 */
object ReownBootstrap {

    @Volatile
    private var attempted = false

    /**
     * Initializes CoreClient + AppKit exactly once per process.
     *
     * @return true when Reown is ready to use (freshly initialized here or already initialized
     * by an earlier caller in this process), false when initialization was skipped or failed.
     */
    fun initialize(
        projectId: String,
        application: Application,
        appName: String,
        appDescription: String,
        appIconUrl: String,
        redirectUrl: String,
        log: (String) -> Unit = {},
        logError: (stage: String, e: Throwable) -> Unit = { _, _ -> },
    ): Boolean {
        if (projectId.isBlank() || projectId.startsWith("dummy-")) {
            log("INIT: projectId blank/dummy — Reown not initialized, wallet connect will fail until wallet.projectId is set")
            return false
        }
        if (attempted) return true
        synchronized(this) {
            if (attempted) return true
            // Physical-device guard: Reown/CoreClient needs Play Services; on devices without it
            // CoreClient.initialize throws NoClassDefFoundError, which a catch of Exception misses.
            val playServicesOk = try {
                val clazz = Class.forName("com.google.android.gms.common.GoogleApiAvailability")
                val availability = clazz.getMethod("getInstance").invoke(null)
                val isAvailable = clazz.getMethod("isGooglePlayServicesAvailable", android.content.Context::class.java)
                isAvailable.invoke(availability, application) as Int == 0 // ConnectionResult.SUCCESS
            } catch (e: Throwable) {
                logError("INIT: Play Services check failed", e)
                false
            }
            if (!playServicesOk) {
                log("INIT: Play Services unavailable — Reown init skipped")
                return false
            }
            attempted = true
            return try {
                val appMetaData = Core.Model.AppMetaData(
                    name = appName.ifBlank { "Haven" },
                    description = appDescription.ifBlank { "Haven — gated media" },
                    url = "https://haven",
                    icons = listOfNotNull(appIconUrl.takeIf { it.isNotBlank() }),
                    redirect = redirectUrl.ifBlank { "haven://connect" },
                    appLink = redirectUrl.ifBlank { "https://haven" }
                )
                CoreClient.initialize(
                    projectId = projectId,
                    connectionType = ConnectionType.AUTOMATIC,
                    application = application,
                    metaData = appMetaData
                ) { error -> logError("INIT: CoreClient initialize error", error.throwable) }
                log("INIT: CoreClient.initialize returned, calling AppKit.initialize")
                AppKit.initialize(
                    init = Modal.Params.Init(core = CoreClient),
                    onSuccess = { log("INIT: AppKit initialized OK (async onSuccess)") },
                    onError = { error -> logError("INIT: AppKit initialize error", error.throwable) }
                )
                AppKit.setChains(AppKitChainsPresets.ethChains.values.toList())
                log("INIT: setChains applied (${AppKitChainsPresets.ethChains.values.size} chains); Reown ready")
                true
            } catch (e: Exception) {
                // Reown may already be initialized by an earlier caller in this process
                if (e.message?.contains("already", ignoreCase = true) == true || e is IllegalStateException) {
                    log("INIT: Reown already-initialized path")
                    true
                } else {
                    attempted = false // allow a retry on the next connect attempt
                    logError("INIT: Reown init failed", e)
                    false
                }
            }
        }
    }
}
