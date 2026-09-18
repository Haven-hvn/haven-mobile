package haven.mobile.core.haven.aol

import haven.mobile.core.domain.MediaItem
import haven.mobile.core.wallet.WalletSession

interface HavenAol {
    suspend fun decrypt(item: MediaItem, session: WalletSession): Result<ByteArray>
    suspend fun verificationKey(): Result<ByteArray>
    /**
     * The canister's Ed25519 attestation key (`getAttestationPublicKey`, 32 bytes) for
     * offline attestation checks — not the BLS VetKD key [verificationKey] returns.
     */
    suspend fun attestationPublicKey(): Result<ByteArray>
    suspend fun decryptAll(items: List<MediaItem>, session: WalletSession): List<Result<ByteArray>>
    suspend fun clearFor(walletAddress: String)
}