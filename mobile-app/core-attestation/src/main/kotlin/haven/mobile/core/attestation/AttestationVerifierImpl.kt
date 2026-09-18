package haven.mobile.core.attestation

import haven.mobile.core.domain.Attestation
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.haven.aol.HavenAol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttestationVerifierImpl @Inject constructor(
    private val havenAol: HavenAol,
) : AttestationVerifier {

    /** The canister attestation key is global (not wallet-bound), so one in-memory slot is enough. */
    private var cachedAttestationKey: ByteArray? = null

    override suspend fun verify(attestation: Attestation, item: MediaItem, nowSeconds: Long): Result<Unit> {
        return withContext(Dispatchers.Default) {
            try {
                val keyBytes = cachedAttestationKey ?: havenAol.attestationPublicKey().getOrElse {
                    throw HavenError.NoKeyAvailable("Failed to fetch attestation key")
                }.also { cachedAttestationKey = it }
                val publicKey = decodeEd25519PublicKey(keyBytes)
                val sigValid = when (attestation) {
                    is Attestation.Single -> verifySingleOffline(attestation, publicKey, nowSeconds)
                    is Attestation.Merkle -> verifyMerkleOffline(attestation, publicKey, nowSeconds)
                }
                if (!sigValid) throw HavenError.AttestationFailed("Attestation signature invalid or expired")
                if (!attestationMatchesEntity(attestation, item)) {
                    throw HavenError.AttestationFailed("Attestation does not match this item")
                }
                Result.success(Unit)
            } catch (e: HavenError) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(HavenError.AttestationFailed(e.message ?: "Unknown error"))
            }
        }
    }
}
