package haven.mobile.core.crypto

import haven.mobile.core.domain.error.HavenError
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Retrieval serves the stored CAR file; [HavenCipher] expects the raw chunked ciphertext
 * inside it. These pin the strip step with synthetic containers (no fixtures needed):
 * single-block CARs pass through byte-identical under any network chunking, anything else
 * either passes through untouched (legacy) or fails loud (multi-block).
 */
class CarContainerTest {

    private val cipher = HavenCipherImpl()
    private val key = ByteArray(32) { 0x37 }

    private fun varint(value: Long): ByteArray {
        var v = value
        val out = mutableListOf<Byte>()
        do {
            var b = (v and 0x7F).toByte()
            v = v ushr 7
            if (v != 0L) b = (b.toInt() or 0x80).toByte()
            out += b
        } while (v != 0L)
        return out.toByteArray()
    }

    private fun carHeader(): ByteArray {
        val roots = byteArrayOf(0x01, 0x55, 0x12, 0x20) + ByteArray(32) { 0x11 }
        val header = byteArrayOf(0xA2.toByte(), 0x67.toByte()) +
            "version".toByteArray(Charsets.US_ASCII) + byteArrayOf(0x01) +
            byteArrayOf(0x65.toByte()) + "roots".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0x81.toByte()) + roots
        return varint(header.size.toLong()) + header
    }

    private fun carBlock(cid: ByteArray, data: ByteArray): ByteArray =
        varint((cid.size + data.size).toLong()) + cid + data

    private fun rawCid(seed: Byte = 0x11): ByteArray =
        byteArrayOf(0x01, 0x55, 0x12, 0x20) + ByteArray(32) { (seed + it).toByte() }

    private fun chunkedCiphertext(plaintext: ByteArray): ByteArray {
        val baseIv = ByteArray(12) { it.toByte() }
        val out = mutableListOf<Byte>()
        out.addAll(baseIv.toList())
        var index = 0
        plaintext.toList().chunked(64).forEach { piece ->
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = baseIv.copyOf()
            val counter = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(index.toLong()).array()
            for (i in 0 until 8) iv[i + 4] = (iv[i + 4].toInt() xor counter[i].toInt()).toByte()
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            val ct = cipher.doFinal(piece.toByteArray())
            val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(index).putInt(ct.size).array()
            out.addAll(header.toList())
            out.addAll(ct.toList())
            index++
        }
        return out.toByteArray()
    }

    private fun split(bytes: ByteArray, sizes: List<Int>): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        var pos = 0
        for (size in sizes) {
            if (pos >= bytes.size) break
            val end = minOf(pos + size, bytes.size)
            out += bytes.copyOfRange(pos, end)
            pos = end
        }
        if (pos < bytes.size) out += bytes.copyOfRange(pos, bytes.size)
        return out
    }

    @Test
    fun `single block car strips under hostile chunking`() = runBlocking {
        val data = ByteArray(300) { it.toByte() }
        val car = carHeader() + carBlock(rawCid(), data)
        // Chop through the varints, the CID, and mid-payload.
        val chunks = split(car, listOf(1, 2, 5, 13, 37, 64))
        val stripped = flow { chunks.forEach { emit(it) } }.stripCarContainer().toList()
        assertArrayEquals(data, stripped.fold(ByteArray(0)) { acc, b -> acc + b })
    }

    @Test
    fun `non-car bytes pass through identical`() = runBlocking {
        val raw = chunkedCiphertext(ByteArray(200) { (it * 3).toByte() })
        val chunks = split(raw, listOf(7, 100))
        val out = flow { chunks.forEach { emit(it) } }.stripCarContainer().toList()
            .fold(ByteArray(0)) { acc, b -> acc + b }
        assertArrayEquals(raw, out)
    }

    @Test
    fun `stripped car decrypts through the cipher`() = runBlocking {
        val plaintext = ByteArray(200) { (it * 3).toByte() }
        val car = carHeader() + carBlock(rawCid(0x22), chunkedCiphertext(plaintext))
        val stripped = flowOf(car).stripCarContainer()
        val decrypted = cipher.decryptStream(key, stripped, null).toList()
            .fold(ByteArray(0)) { acc, b -> acc + b }
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `multi-block car fails loud`() {
        val car = carHeader() + carBlock(rawCid(0x22), byteArrayOf(1, 2, 3)) +
            carBlock(rawCid(0x33), byteArrayOf(4, 5, 6))
        val error = assertThrows(HavenError.PlaybackDecryptFailed::class.java) {
            runBlocking { flowOf(car).stripCarContainer().toList() }
        }
        assertTrue(error.message!!.contains("Multi-block"))
    }

    @Test
    fun `truncated prefix fails open`() = runBlocking {
        // Dies mid-header: not provably a container, so pass through for the decrypt
        // layer to judge rather than failing here.
        val partial = carHeader().copyOfRange(0, 3)
        val out = flowOf(partial).stripCarContainer().toList()
            .fold(ByteArray(0)) { acc, b -> acc + b }
        assertArrayEquals(partial, out)
    }

    @Test
    fun `empty stream stays empty`() = runBlocking {
        assertEquals(0, flowOf<ByteArray>().stripCarContainer().toList().size)
    }
}
