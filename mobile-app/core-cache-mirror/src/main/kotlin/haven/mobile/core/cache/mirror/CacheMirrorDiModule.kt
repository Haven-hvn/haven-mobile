package haven.mobile.core.cache.mirror

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CacheMirrorDiModule {
    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}

@Module
@InstallIn(SingletonComponent::class)
object CacheMirrorDatabaseModule {
    @Provides
    @Singleton
    fun provideMediaDao(database: HavenMirrorDatabase): MediaDao = database.mediaDao()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context, walletSession: haven.mobile.core.wallet.WalletSession): HavenMirrorDatabase {
        // Lazy per-wallet DB — create a fallback; MediaRepositoryImpl manages per-wallet lifecycle.
        // This provider satisfies Hilt; actual DB is created lazily in MediaRepositoryImpl.
        return Room.inMemoryDatabaseBuilder(context, HavenMirrorDatabase::class.java).build()
    }
}