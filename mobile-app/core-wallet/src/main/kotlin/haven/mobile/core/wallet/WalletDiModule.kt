package haven.mobile.core.wallet

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WalletDiModule {
    @Binds
    @Singleton
    abstract fun bindWalletSession(impl: WalletSessionImpl): WalletSession
}

@Module
@InstallIn(SingletonComponent::class)
object WalletDataStoreModule {
    @Provides
    @Singleton
    fun provideWalletDataStore(@ApplicationContext context: Context): WalletDataStore = WalletDataStore(context)

    @Provides
    @Singleton
    fun provideWalletConfig(): WalletConfig {
        val fromCore = try { haven.mobile.core.wallet.BuildConfig.WALLET_PROJECT_ID } catch (_: Exception) { "" }
        val fromApp = try { Class.forName("haven.mobile.app.BuildConfig").getField("WALLET_PROJECT_ID").get(null) as? String ?: "" } catch (_: Exception) { "" }
        // Hard fallback to the Haven Cloud projectId so debug builds never show "Wallet connections aren't available"
        // (app/build.gradle.kts also sets this from local.properties wallet.projectId, but core-wallet's BuildConfig is separate)
        val pid = when {
            fromCore.isNotBlank() && !fromCore.startsWith("dummy-") -> fromCore
            fromApp.isNotBlank() && !fromApp.startsWith("dummy-") -> fromApp
            else -> "02760a75be7577c92e7d39f1de04db31"
        }
        return WalletConfig(
            projectId = pid,
            appName = "Haven",
            appDescription = "Haven — gated media",
            appIconUrl = "",
            redirectUrl = "haven://connect",
        )
    }
}