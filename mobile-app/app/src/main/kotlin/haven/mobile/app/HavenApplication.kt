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
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        initReownIfNeeded()
    }

    private fun initReownIfNeeded() {
        val projectId = BuildConfig.WALLET_PROJECT_ID
        if (projectId.isBlank()) {
            Timber.w("WALLET_PROJECT_ID blank — Reown init skipped")
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
        } catch (e: Exception) {
            if (e.message?.contains("already", ignoreCase = true) != true) {
                Timber.e(e, "Reown init failed")
            }
        }
    }
}