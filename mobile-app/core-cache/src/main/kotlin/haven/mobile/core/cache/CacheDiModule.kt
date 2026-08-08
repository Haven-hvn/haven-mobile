package haven.mobile.core.cache

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
abstract class CacheDiModule {
    @Binds
    @Singleton
    abstract fun bindHavenCache(impl: HavenCacheImpl): HavenCache
}

@Module
@InstallIn(SingletonComponent::class)
object CacheConfigModule {
    @Provides
    @Singleton
    fun provideCacheConfig(): CacheConfig = CacheConfig()

    @Provides
    @Singleton
    fun providePieceCidVerifier(): PieceCidVerifier = NoOpPieceCidVerifier()
}