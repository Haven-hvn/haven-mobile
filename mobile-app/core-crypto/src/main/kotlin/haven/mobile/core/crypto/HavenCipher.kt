package haven.mobile.core.crypto

import haven.mobile.core.domain.error.HavenError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

class HavenCipherImpl @javax.inject.Inject constructor() : HavenCipher {

    companion object {
        private const val BASE_IV_SIZE = 12
        private const val CHUNK_INDEX_SIZE = 4
        private const val CHUNK_LENGTH_SIZE = 4
        private const val CHUNK_HEADER_SIZE = CHUNK_INDEX_SIZE + CHUNK_LENGTH_SIZE
        private const val MAX_CHUNK_SIZE = 64 * 1024 * 1024
        private const val GCM_TAG_SIZE = 16
    }

    override suspend fun decrypt(key: ByteArray, ciphertext: ByteArray, aad: ByteArray?): Result<ByteArray> {
        return try {
            // Try chunked format first (haven-cli streaming). Fallback to simple AES-GCM for legacy single-block.
            if (isChunkedFormat(ciphertext)) {
                Result.success(decryptChunked(key, ciphertext))
            } else {
                // Legacy: IV (12) + ciphertext+tag
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(128, ciphertext, 0, BASE_IV_SIZE)
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)
                if (aad != null) cipher.updateAAD(aad)
                val plaintext = cipher.doFinal(ciphertext, BASE_IV_SIZE, ciphertext.size - BASE_IV_SIZE)
                Result.success(plaintext)
            }
        } catch (e: HavenError) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(HavenError.PlaybackDecryptFailed(e.message ?: "Decrypt failed"))
        }
    }

    override fun decryptStream(
        key: ByteArray,
        ciphertext: Flow<ByteArray>,
        aad: ByteArray?
    ): Flow<ByteArray> {
        return flow {
            // Collect all incoming Flow chunks into a single buffer then do chunked decrypt emission.
            // This keeps the per-chunk IV derivation correct even when network chunks don't align with encryption chunks.
            val collected = mutableListOf<ByteArray>()
            var total = 0
            ciphertext.collect { chunk ->
                collected.add(chunk)
                total += chunk.size
            }
            if (total == 0) return@flow
            val combined = ByteArray(total)
            var off = 0
            for (c in collected) {
                System.arraycopy(c, 0, combined, off, c.size)
                off += c.size
            }
            if (isChunkedFormat(combined)) {
                var offset = BASE_IV_SIZE
                val baseIv = combined.copyOfRange(0, BASE_IV_SIZE)
                var expectedIndex = 0
                while (offset < combined.size) {
                    if (offset + CHUNK_HEADER_SIZE > combined.size) break
                    val bb = ByteBuffer.wrap(combined, offset, CHUNK_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
                    val idx = bb.int
                    val len = bb.int
                    if (len > MAX_CHUNK_SIZE) throw HavenError.PlaybackDecryptFailed("Chunk $idx exceeds max size")
                    val dataOff = offset + CHUNK_HEADER_SIZE
                    if (dataOff + len > combined.size) break
                    if (idx != expectedIndex) throw HavenError.PlaybackDecryptFailed("Chunk index mismatch expected $expectedIndex got $idx")
                    val encryptedChunk = combined.copyOfRange(dataOff, dataOff + len)
                    val perIv = deriveChunkIv(baseIv, idx)
                    val plain = aesGcmDecrypt(key, perIv, encryptedChunk, aad)
                    emit(plain)
                    offset = dataOff + len
                    expectedIndex++
                }
            } else {
                val plain = decrypt(key, combined, aad).getOrThrow()
                emit(plain)
            }
        }.flowOn(Dispatchers.Default)
    }

    private fun isChunkedFormat(data: ByteArray): Boolean {
        if (data.size < BASE_IV_SIZE + CHUNK_HEADER_SIZE) return false
        // Peek first chunk header: if length is plausible and offset aligns, treat as chunked.
        val bb = ByteBuffer.wrap(data, BASE_IV_SIZE, CHUNK_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        val len = bb.getInt(4)
        // Encrypted chunk length should be > GCM_TAG_SIZE and < MAX and not exceed remaining
        return len in (GCM_TAG_SIZE + 1)..MAX_CHUNK_SIZE && BASE_IV_SIZE + CHUNK_HEADER_SIZE + len <= data.size
    }

    private fun decryptChunked(key: ByteArray, data: ByteArray): ByteArray {
        val baseIv = data.copyOfRange(0, BASE_IV_SIZE)
        var offset = BASE_IV_SIZE
        var expectedIndex = 0
        val out = mutableListOf<ByteArray>()
        var totalPlain = 0
        while (offset < data.size) {
            if (offset + CHUNK_HEADER_SIZE > data.size) {
                throw HavenError.PlaybackDecryptFailed("Truncated chunk header at $offset")
            }
            val bb = ByteBuffer.wrap(data, offset, CHUNK_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            val idx = bb.int
            val len = bb.int
            if (len > MAX_CHUNK_SIZE) throw HavenError.PlaybackDecryptFailed("Chunk $idx exceeds max")
            val dataOff = offset + CHUNK_HEADER_SIZE
            if (dataOff + len > data.size) throw HavenError.PlaybackDecryptFailed("Truncated chunk $idx")
            if (idx != expectedIndex) throw HavenError.PlaybackDecryptFailed("Chunk order mismatch expected $expectedIndex got $idx")
            val encryptedChunk = data.copyOfRange(dataOff, dataOff + len)
            val perIv = deriveChunkIv(baseIv, idx)
            val plain = aesGcmDecrypt(key, perIv, encryptedChunk, null)
            out.add(plain)
            totalPlain += plain.size
            offset = dataOff + len
            expectedIndex++
        }
        if (expectedIndex == 0) throw HavenError.PlaybackDecryptFailed("No chunks found")
        val result = ByteArray(totalPlain)
        var pos = 0
        for (p in out) {
            System.arraycopy(p, 0, result, pos, p.size)
            pos += p.size
        }
        return result
    }

    private fun deriveChunkIv(baseIv: ByteArray, chunkIndex: Int): ByteArray {
        require(baseIv.size == BASE_IV_SIZE) { "Base IV must be 12 bytes" }
        val perIv = baseIv.copyOf()
        // big-endian u64 of chunkIndex into bytes [4..12]
        val idxBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(chunkIndex.toLong()).array()
        for (i in 0 until 8) {
            perIv[i + 4] = (perIv[i + 4].toInt() xor idxBytes[i].toInt()).toByte()
        }
        return perIv
    }

    private fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertextAndTag: ByteArray, aad: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(ciphertextAndTag)
    }
}
