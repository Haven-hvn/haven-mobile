package haven.mobile.core.attestation

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AttestationDiModule {
    @Binds
    @Singleton
    abstract fun bindAttestationVerifier(impl: AttestationVerifierImpl): AttestationVerifier
}