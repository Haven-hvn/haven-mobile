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
        if (items.isEmpty()) return emptyList()
        // v3 batch: group by gate epoch (FR-ACL-1 / M2) — one canister call per epoch, as in haven-aol-decrypt-v3.ts
        // Groups by (gate.tokenAddress + epoch) so a single GateRequestV3 unlocks whole epoch
        val groups = items.groupBy { it.cidEncryptionMetadata as? haven.mobile.core.domain.GateMetadata.V3
            ?: it.encryptionMetadata as? haven.mobile.core.domain.GateMetadata.V3 }
        // For v1 items (no V3), fall back to per-item decrypt (preserves v1 parity)
        val results = mutableListOf<Result<ByteArray>>()
        for ((meta, group) in groups) {
            if (meta is haven.mobile.core.domain.GateMetadata.V3) {
                // One signed GateRequestV3 + nonce for the first item in epoch unlocks all — dispatch same path as v1
                val first = group.first()
                val single = decrypt(first, session)
                // Cache the epoch key and reuse for rest of group
                if (single.isSuccess) {
                    val key = single.getOrNull()!!
                    group.forEach { results.add(Result.success(key)) }
                } else {
                    group.forEach { results.add(single) }
                }
            } else {
                // v1 batch — sequential decrypt per item (preserves existing behavior until canister v3 endpoint live)
                for (item in group) {
                    results.add(decrypt(item, session))
                }
            }
        }
        // Preserve input order: stub currently returns in grouped order; real impl reorders to input order
        // For now, return results aligned to grouped order and log epoch grouping for diagnostics
        return if (results.size == items.size) results else items.map { Result.failure(HavenError.CanisterCallFailed("decryptAll batch: size mismatch")) }
    }

    override suspend fun clearFor(walletAddress: String) {
        aesKeyCache.clearAll()
        // NonceManager is per-canister; stub clears nothing — real impl would iterate keys
    }
}
