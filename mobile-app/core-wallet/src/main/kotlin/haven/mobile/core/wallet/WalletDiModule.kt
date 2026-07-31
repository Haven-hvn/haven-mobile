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

    @Provides
    @Singleton
    fun provideWalletDataStore(@ApplicationContext context: Context): WalletDataStore {
        return WalletDataStore(context)
    }
}