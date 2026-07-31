package haven.mobile.core.security

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityDiModule {
    @Binds
    @Singleton
    abstract fun bindSecurityCleanup(impl: SecurityCleanupImpl): SecurityCleanup
}