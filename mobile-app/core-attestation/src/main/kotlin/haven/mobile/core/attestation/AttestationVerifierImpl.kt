package haven.mobile.core.attestation

import haven.mobile.core.domain.Attestation
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.haven.aol.HavenAol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.PublicKey
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
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
        // Raw 32-byte Ed25519 key — wrap in X.509 SubjectPublicKeyInfo for KeyFactory.
        // If bytes already look like X.509 (starts with 0x30), use directly.
        if (bytes.isNotEmpty() && bytes[0] == 0x30.toByte()) {
            return KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(bytes))
        }
        // Build minimal X.509 header for raw 32-byte key: OID 1.3.101.112
        val header = byteArrayOf(
            0x30.toByte(), 0x2A.toByte(), 0x30.toByte(), 0x05.toByte(), 0x06.toByte(), 0x03.toByte(), 0x2B.toByte(), 0x65.toByte(), 0x70.toByte(), 0x03.toByte(), 0x21.toByte(), 0x00.toByte()
        )
        val x509 = header + bytes
        return KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(x509))
    }

    private fun verifySignature(publicKey: PublicKey, signature: ByteArray, message: ByteArray) {
        val sig = Signature.getInstance("Ed25519")
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