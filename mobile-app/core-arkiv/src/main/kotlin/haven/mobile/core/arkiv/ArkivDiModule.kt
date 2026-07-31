package haven.mobile.core.arkiv

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ArkivDiModule {
    @Binds
    @Singleton
    abstract fun bindArkivClient(impl: ArkivClientImpl): ArkivClient

    @Provides
    @Singleton
    fun provideArkivConfig(): ArkivConfig {
        return ArkivConfig(
            endpointUrl = "",
        )
    }
}