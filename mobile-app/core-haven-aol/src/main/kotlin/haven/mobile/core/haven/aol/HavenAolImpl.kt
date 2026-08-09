package haven.mobile.core.haven.aol

import haven.mobile.core.crypto.AesKeyCache
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.wallet.WalletSession
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HavenAolImpl @Inject constructor(
    private val config: HavenAolConfig,
    private val walletSession: WalletSession,
    private val aesKeyCache: AesKeyCache,
    private val nonceManager: NonceManager,
    private val gateRequestBuilder: GateRequestBuilder
) : HavenAol {

    override suspend fun decrypt(item: MediaItem, session: WalletSession): Result<ByteArray> {
        return Result.failure(HavenError.CanisterCallFailed("HavenAol decrypt stub — configure canisterId/icHost"))
    }

    override suspend fun verificationKey(): Result<ByteArray> {
        return Result.failure(HavenError.CanisterCallFailed("verificationKey stub"))
    }

    override suspend fun decryptAll(items: List<MediaItem>, session: WalletSession): List<Result<ByteArray>> {
        return items.map { Result.failure(HavenError.CanisterCallFailed("decryptAll stub")) }
    }

    override suspend fun clearFor(walletAddress: String) {
        aesKeyCache.clearAll()
        // NonceManager is per-canister; stub clears nothing — real impl would iterate keys
    }
}
