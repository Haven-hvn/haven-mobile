package haven.mobile.core.cache

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CacheDiModule {
    @Binds
    @Singleton
    abstract fun bindHavenCache(impl: HavenCacheImpl): HavenCache

    @Binds
    @Singleton
    abstract fun bindPlaintextSpool(impl: PlaintextSpoolImpl): PlaintextSpool
}

@Module
@InstallIn(SingletonComponent::class)
object CacheConfigModule {
    /**
     * Defaults per requirements FR-CACHE-2. The user-adjustable quota and TTL live in
     * `SettingsRepository`; wiring those through to a live foc `Config` is tracked follow-up work
     * (foc reads `Config` at construction, so the facade has to rebuild on change).
     */
    @Provides
    @Singleton
    fun provideCacheConfig(): CacheConfig = CacheConfig()
}
