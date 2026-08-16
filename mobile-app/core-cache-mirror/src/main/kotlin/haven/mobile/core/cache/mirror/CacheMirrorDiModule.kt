package haven.mobile.core.cache.mirror

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import haven.mobile.core.cache.CacheSettingsSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
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

    /**
     * Supplies `:core-cache` with the user's preferences.
     *
     * The direction matters: `:core-cache-mirror` already depends on `:core-cache`, so the cache
     * declares the interface it needs and this module fulfils it. Without this binding the quota and
     * TTL sliders wrote values nothing ever read.
     */
    @Binds
    @Singleton
    abstract fun bindCacheSettingsSource(impl: SettingsBackedCacheSettings): CacheSettingsSource
}

/** Adapter: the preferences store, seen through the cache's much smaller interface. */
@Singleton
class SettingsBackedCacheSettings @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : CacheSettingsSource {
    override val quotaBytes: Flow<Long> get() = settingsRepository.cacheQuotaBytes
    override val ttlDays: Flow<Int> get() = settingsRepository.cacheTtlDays
}

@Module
@InstallIn(SingletonComponent::class)
object CacheMirrorDatabaseModule {
    @Provides
    @Singleton
    fun provideMediaDao(database: HavenMirrorDatabase): MediaDao = database.mediaDao()

    /**
     * Satisfies Hilt for anything asking for the database directly. The real, per-wallet database is
     * opened lazily by `MediaRepositoryImpl`, because its filename contains the wallet address and so
     * cannot be known at graph-construction time.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HavenMirrorDatabase =
        Room.inMemoryDatabaseBuilder(context, HavenMirrorDatabase::class.java).build()
}
