package haven.mobile.core.arkiv

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ArkivDiModule {
    @Binds
    @Singleton
    abstract fun bindArkivClient(impl: ArkivClientImpl): ArkivClient
}

@Module
@InstallIn(SingletonComponent::class)
object ArkivConfigModule {
    /**
     * Endpoint comes from `local.properties` via `BuildConfig` (see this module's build script),
     * the same pattern as `wallet.projectId` and `haven.aol.*`. It used to be hard-coded to `""`,
     * which meant every Arkiv call failed while looking like a network error.
     */
    @Provides
    @Singleton
    fun provideArkivConfig(): ArkivConfig = ArkivConfig(
        endpointUrl = BuildConfig.ARKIV_ENDPOINT_URL,
    )
}
