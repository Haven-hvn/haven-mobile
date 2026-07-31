package haven.mobile.core.crypto

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CryptoDiModule {
    @Binds
    @Singleton
    abstract fun bindHavenCipher(impl: HavenCipherImpl): HavenCipher

    @Binds
    @Singleton
    abstract fun bindAesKeyCache(impl: AesKeyCache): AesKeyCache

    @Binds
    @Singleton
    abstract fun bindGateKeyCache(impl: GateKeyCache): GateKeyCache
}