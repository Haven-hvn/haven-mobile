package haven.mobile.core.crypto

import cloud.filecoin.foc.cache.PieceRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * The foc transform behind "Keep unlocked content on this device". A fake cipher (XOR with the key's
 * first byte) keeps this about the plumbing: key resolved lazily and once, output streamed, failures
 * abort. Real decryption is covered by [HavenCipherStreamTest].
 */
class DecryptOnStoreTest {

    private object XorCipher : HavenCipher {
        override suspend fun decrypt(key: ByteArray, ciphertext: ByteArray, aad: ByteArray?) =
            Result.success(ciphertext.map { (it.toInt() xor key[0].toInt()).toByte() }.toByteArray())

        override fun decryptStream(key: ByteArray, ciphertext: Flow<ByteArray>, aad: ByteArray?): Flow<ByteArray> =
            ciphertext.map { chunk -> ByteArray(chunk.size) { (chunk[it].toInt() xor key[0].toInt()).toByte() } }
    }

    private val ref = PieceRef(pieceCid = "baga-test", size = 0, providerServiceUrls = emptyList())

    @Test
    fun `decrypts the stored stream and reports progress`() = runBlocking {
        val plain = ByteArray(600_000) { (it % 251).toByte() }
        val sealed = ByteArray(plain.size) { (plain[it].toInt() xor 0x42).toByte() }
        var keyCalls = 0
        var reported = 0L
        val out = ByteArrayOutputStream()

        XorCipher.decryptOnStore(key = { keyCalls++; byteArrayOf(0x42) }, onBytesWritten = { reported = it })
            .transform(ref, ByteArrayInputStream(sealed), out)

        assertArrayEquals(plain, out.toByteArray())
        assertEquals(1, keyCalls)
        assertEquals(plain.size.toLong(), reported)
    }

    @Test
    fun `a refused unlock aborts before anything is written`() {
        val out = ByteArrayOutputStream()
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                XorCipher.decryptOnStore(key = { error("below threshold") })
                    .transform(ref, ByteArrayInputStream(ByteArray(10)), out)
            }
        }
        assertEquals(0, out.size())
    }
}
