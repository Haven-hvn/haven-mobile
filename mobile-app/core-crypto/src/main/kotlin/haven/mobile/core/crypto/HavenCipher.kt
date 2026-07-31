package haven.mobile.core.crypto

import haven.mobile.core.domain.error.HavenError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface HavenCipher {
    suspend fun decrypt(key: ByteArray, ciphertext: ByteArray, aad: ByteArray?): Result<ByteArray>

    fun decryptStream(
        key: ByteArray,
        ciphertext: Flow<ByteArray>,
        aad: ByteArray?
    ): Flow<ByteArray>
}

class HavenCipherImpl : HavenCipher {

    override suspend fun decrypt(key: ByteArray, ciphertext: ByteArray, aad: ByteArray?): Result<ByteArray> {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, extractIv(ciphertext))
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)
            val plaintext = cipher.doFinal(extractCiphertext(ciphertext))
            Result.success(plaintext)
        } catch (e: Exception) {
            Result.failure(HavenError.DecryptFailed(e.message ?: "Unknown error"))
        }
    }

    override fun decryptStream(
        key: ByteArray,
        ciphertext: Flow<ByteArray>,
        aad: ByteArray?
    ): Flow<ByteArray> {
        return flow {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12)
            var cipherInitialized = false
            val buffer = ByteArrayOutputStream()

            ciphertext.collect { chunk ->
                if (!cipherInitialized) {
                    if (chunk.size < 12) {
                        System.arraycopy(chunk, 0, iv, 0, chunk.size)
                        return@collect
                    }
                    System.arraycopy(chunk, 0, iv, 0, 12)
                    val spec = GCMParameterSpec(128, iv)
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)
                    cipherInitialized = true

                    if (chunk.size > 12) {
                        buffer.write(chunk, 12, chunk.size - 12)
                    }
                } else {
                    buffer.write(chunk)
                }

                val buffered = buffer.toByteArray()
                if (buffered.isNotEmpty()) {
                    val plaintext = cipher.update(buffered)
                    if (plaintext != null && plaintext.isNotEmpty()) {
                        emit(plaintext)
                    }
                    buffer.reset()
                }
            }

            if (cipherInitialized) {
                val finalPlaintext = cipher.doFinal()
                if (finalPlaintext != null && finalPlaintext.isNotEmpty()) {
                    emit(finalPlaintext)
                }
            }
        }.flowOn(Dispatchers.Default)
    }

    private fun extractIv(ciphertext: ByteArray): ByteArray {
        return ciphertext.copyOfRange(0, 12)
    }

    private fun extractCiphertext(ciphertext: ByteArray): ByteArray {
        return ciphertext.copyOfRange(12, ciphertext.size)
    }
}