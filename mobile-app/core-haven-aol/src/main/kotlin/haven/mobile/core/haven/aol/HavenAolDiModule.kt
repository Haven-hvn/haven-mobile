package haven.mobile.core.haven.aol

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HavenAolDiModule {
    @Binds
    @Singleton
    abstract fun bindHavenAol(impl: HavenAolImpl): HavenAol

    @Provides
    @Singleton
    fun provideHavenAolConfig(): HavenAolConfig {
        return HavenAolConfig(
            canisterId = "",
            icHost = ""
        )
    }

    @Provides
    @Singleton
    fun provideNonceManager(): NonceManager {
        return NonceManager()
    }

    @Provides
    @Singleton
    fun provideGateRequestBuilder(): GateRequestBuilder {
        return GateRequestBuilder()
    }
}