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
        // Hardcode Haven Cloud projectId for now — ensures v0.1.5 never shows
        // "Wallet connections aren't available" regardless of BuildConfig reflection.
        // app/build.gradle.kts still sets BuildConfig.WALLET_PROJECT_ID from local.properties,
        // but we don't rely on it at runtime.
        return WalletConfig(
            projectId = "02760a75be7577c92e7d39f1de04db31",
            appName = "Haven",
            appDescription = "Haven — gated media",
            appIconUrl = "",
            redirectUrl = "haven://connect",
        )
    }
}