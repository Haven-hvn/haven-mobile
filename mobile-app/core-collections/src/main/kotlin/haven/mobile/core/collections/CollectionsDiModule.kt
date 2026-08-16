package haven.mobile.core.collections

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CollectionsDiModule {
    @Binds
    @Singleton
    abstract fun bindCollectionRepository(impl: CollectionRepositoryImpl): CollectionRepository

    /**
     * The gate checker is bound here but consumed well outside this module — `:core-cache-mirror` uses
     * it to intersect Arkiv's stored gate conditions with what the wallet holds, which is how a
     * reader's library is assembled at all.
     */
    @Binds
    @Singleton
    abstract fun bindGateAccessChecker(impl: EvmGateAccessChecker): GateAccessChecker
}
