package haven.mobile.core.crypto

import cloud.filecoin.foc.cache.PieceTransform
import kotlinx.coroutines.flow.flow

/**
 * foc-cache transform for "Keep unlocked content on this device": the retrieved piece (a CAR file
 * wrapping Haven's chunked ciphertext) is decrypted once as it's stored, so later opens read a local
 * file with no key and no canister call.
 *
 * [key] is resolved lazily, only when foc actually has to fetch the piece, so a piece already on the
 * device costs nothing. Throwing from [key] (a refused unlock, a drip below target) aborts the fetch
 * and nothing is stored. [onBytesWritten] reports cumulative plaintext bytes for a progress bar.
 */
fun HavenCipher.decryptOnStore(
    key: suspend () -> ByteArray,
    onBytesWritten: (Long) -> Unit = {},
): PieceTransform = PieceTransform { _, input, output ->
    val contentKey = key()
    val ciphertext = flow {
        val buf = ByteArray(CHUNK_BYTES)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            emit(buf.copyOf(n))
        }
    }
    var written = 0L
    decryptStream(contentKey, ciphertext.stripCarContainer(), null).collect { chunk ->
        output.write(chunk)
        written += chunk.size
        onBytesWritten(written)
    }
}

private const val CHUNK_BYTES = 256 * 1024
