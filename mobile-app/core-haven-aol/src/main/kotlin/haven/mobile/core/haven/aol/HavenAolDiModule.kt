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
}

@Module
@InstallIn(SingletonComponent::class)
object HavenAolConfigModule {
    @Provides
    @Singleton
    fun provideHavenAolConfig(): HavenAolConfig = HavenAolConfig(canisterId = "", icHost = "")

    @Provides
    @Singleton
    fun provideNonceManager(): NonceManager = NonceManager()

    @Provides
    @Singleton
    fun provideGateRequestBuilder(): GateRequestBuilder = GateRequestBuilder()
}