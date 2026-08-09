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
    fun provideHavenAolConfig(): HavenAolConfig = HavenAolConfig(
        canisterId = try { haven.mobile.core.haven.aol.BuildConfig.HAVEN_AOL_CANISTER_ID } catch (_: Exception) { "" },
        icHost = try { haven.mobile.core.haven.aol.BuildConfig.HAVEN_AOL_IC_HOST } catch (_: Exception) { "https://ic0.app" },
    )

    @Provides
    @Singleton
    fun provideNonceManager(): NonceManager = NonceManager()

    @Provides
    @Singleton
    fun provideGateRequestBuilder(): GateRequestBuilder = GateRequestBuilder()
}