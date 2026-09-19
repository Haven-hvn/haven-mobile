package haven.mobile.core.haven.aol

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import haven.mobile.core.haven.aol.vetkeys.JniVetKdUnwrap
import haven.mobile.core.haven.aol.vetkeys.VetKdUnwrap
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HavenAolDiModule {
    @Binds
    @Singleton
    abstract fun bindHavenAol(impl: HavenAolImpl): HavenAol

    @Binds
    @Singleton
    abstract fun bindVetKdUnwrap(impl: JniVetKdUnwrap): VetKdUnwrap
}

@Module
@InstallIn(SingletonComponent::class)
object HavenAolConfigModule {
    @Provides
    @Singleton
    fun provideHavenAolConfig(): HavenAolConfig {
        // CI/release builds have no local.properties, where BuildConfig would otherwise be blank.
        // Mainnet backend from haven-hvn/haven-aol; local.properties may still override.
        return HavenAolConfig(
            canisterId = try { haven.mobile.core.haven.aol.BuildConfig.HAVEN_AOL_CANISTER_ID } catch (_: Exception) { "" }
                .ifBlank { "gny6k-fqaaa-aaaab-ag3ra-cai" },
            icHost = try { haven.mobile.core.haven.aol.BuildConfig.HAVEN_AOL_IC_HOST } catch (_: Exception) { "" }
                .ifBlank { "https://ic0.app" },
        )
    }

    @Provides
    @Singleton
    fun provideNonceManager(): NonceManager = NonceManager()

    @Provides
    @Singleton
    fun provideGateRequestBuilder(): GateRequestBuilder = GateRequestBuilder()
}