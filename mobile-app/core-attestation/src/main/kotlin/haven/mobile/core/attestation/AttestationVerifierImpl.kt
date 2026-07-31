package haven.mobile.core.attestation

import haven.mobile.core.domain.Attestation
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.haven.aol.HavenAol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.PublicKey
import java.security.spec.Ed25519PublicKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttestationVerifierImpl @Inject constructor(
    private val havenAol: HavenAol,
) : AttestationVerifier {

    private var cachedVerificationKey: ByteArray? = null

    override suspend fun verifySingle(attestation: Attestation, subjectCid: String): Result<Unit> {
        return withContext(Dispatchers.Default) {
            try {
                val pubKey = getVerificationKey()
                val subjectBytes = subjectCid.toByteArray(Charsets.UTF_8)
                verifySignature(pubKey, attestation.signature, subjectBytes)
                Result.success(Unit)
            } catch (e: HavenError) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(HavenError.AttestationFailed(e.message ?: "Unknown error"))
            }
        }
    }

    override suspend fun verifyBatch(attestation: Attestation, subjectCid: String): Result<Unit> {
        return withContext(Dispatchers.Default) {
            try {
                val pubKey = getVerificationKey()
                val subjectBytes = subjectCid.toByteArray(Charsets.UTF_8)
                verifySignature(pubKey, attestation.signature, subjectBytes)
                val merkleProof = attestation.merkleProof
                if (merkleProof != null) {
                    verifyMerkleProof(merkleProof, subjectBytes)
                }
                Result.success(Unit)
            } catch (e: HavenError) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(HavenError.AttestationFailed(e.message ?: "Unknown error"))
            }
        }
    }

    private suspend fun getVerificationKey(): PublicKey {
        val cached = cachedVerificationKey
        if (cached != null) {
            return decodeEd25519PublicKey(cached)
        }
        val keyBytes = havenAol.verificationKey().getOrElse {
            throw HavenError.NoKeyAvailable("Failed to fetch verification key")
        }
        cachedVerificationKey = keyBytes
        return decodeEd25519PublicKey(keyBytes)
    }

    private fun decodeEd25519PublicKey(bytes: ByteArray): PublicKey {
        val keySpec = Ed25519PublicKeySpec(bytes)
        return java.security.KeyFactory.getInstance("EdDSA").generatePublic(keySpec)
    }

    private fun verifySignature(publicKey: PublicKey, signature: ByteArray, message: ByteArray) {
        val sig = java.security.Signature.getInstance("Ed25519")
        sig.initVerify(publicKey)
        sig.update(message)
        if (!sig.verify(signature)) {
            throw HavenError.AttestationFailed("Signature verification failed")
        }
    }

    private fun verifyMerkleProof(merkleProof: List<ByteArray>, subjectBytes: ByteArray) {
        var computedHash = sha256(subjectBytes)
        for (proof in merkleProof) {
            computedHash = sha256(computedHash + proof)
        }
    }

    private fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }
}