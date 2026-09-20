package haven.mobile.core.crypto

import haven.mobile.core.domain.error.HavenError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.ByteArrayOutputStream

/**
 * Strips a CARv1 container from a byte stream, passing the block data through.
 *
 * Retrieval (`/piece/`) serves the stored CAR file, while [HavenCipher] expects the raw
 * chunked ciphertext the uploader encrypted. Without this step the CAR header is mistaken
 * for the base IV and every chunk fails its GCM tag check (`bad_decrypt`).
 *
 * Streaming and bounded: only the container framing (header + block headers, at most a few
 * hundred bytes) is ever buffered. Block payloads flow straight through, so a multi-GB
 * piece still stages in constant memory.
 *
 * Scope is deliberately narrow:
 * - Non-CAR input (legacy raw ciphertext) passes through byte-identical.
 * - A single data block passes through; anything after its end fails LOUD with a
 *   multi-block error instead of decrypting frame headers as ciphertext.
 * - Truncated/ambiguous prefixes fail OPEN (pass through) so the decrypt layer — which
 *   already fails closed with framing/tag errors — stays the single judge of validity.
 */
fun Flow<ByteArray>.stripCarContainer(): Flow<ByteArray> = flow {
    val pending = ByteArrayOutputStream()
    var phase: Phase = Phase.PREFIX
    var frameDataRemaining = 0L

    suspend fun emitAll(out: ByteArrayOutputStream) {
        val bytes = out.toByteArray()
        if (bytes.isNotEmpty()) emit(bytes)
    }

    collect { incoming ->
        if (incoming.isEmpty()) return@collect
        when (phase) {
            Phase.RAW -> emit(incoming)
            Phase.DATA -> {
                val take = minOf(incoming.size.toLong(), frameDataRemaining).toInt()
                if (take > 0) emit(incoming.copyOfRange(0, take))
                frameDataRemaining -= take
                if (take < incoming.size) {
                    // Frame ended mid-chunk: whatever follows is another frame.
                    throw HavenError.PlaybackDecryptFailed(
                        "Multi-block CAR container: only single-block pieces are supported",
                    )
                }
                if (frameDataRemaining == 0L) phase = Phase.TRAIL
            }
            Phase.TRAIL -> throw HavenError.PlaybackDecryptFailed(
                "Multi-block CAR container: only single-block pieces are supported",
            )
            Phase.PREFIX -> {
                pending.write(incoming)
                when (val decision = decide(pending.toByteArray())) {
                    is Decision.NotCar -> {
                        phase = Phase.RAW
                        emitAll(pending)
                        pending.reset()
                    }
                    is Decision.Car -> {
                        phase = Phase.DATA
                        frameDataRemaining = decision.dataLength
                        val rest = pending.toByteArray().copyOfRange(decision.dataOffset, pending.size())
                        pending.reset()
                        if (rest.isNotEmpty()) {
                            val take = minOf(rest.size.toLong(), frameDataRemaining).toInt()
                            if (take > 0) emit(rest.copyOfRange(0, take))
                            frameDataRemaining -= take
                            if (take < rest.size) {
                                throw HavenError.PlaybackDecryptFailed(
                                    "Multi-block CAR container: only single-block pieces are supported",
                                )
                            }
                            if (frameDataRemaining == 0L) phase = Phase.TRAIL
                        }
                    }
                    Decision.Undecided -> {
                        if (pending.size() > MAX_PREFIX_BYTES) {
                            phase = Phase.RAW
                            emitAll(pending)
                            pending.reset()
                        }
                    }
                }
            }
        }
    }

    // Stream ended with an undecided prefix: fail open so the decrypt layer judges it.
    if (phase == Phase.PREFIX) emitAll(pending)
}

private enum class Phase { PREFIX, RAW, DATA, TRAIL }

/** Cap on framing bytes ever buffered while deciding; block payloads never buffer. */
private const val MAX_PREFIX_BYTES = 4096

private sealed interface Decision {
    data object Undecided : Decision
    data object NotCar : Decision
    /** Container confirmed: block data starts at [dataOffset], spanning [dataLength] bytes. */
    data class Car(val dataOffset: Int, val dataLength: Long) : Decision
}

/** Unsigned LEB128; null when the bytes so far do not terminate one. */
private fun readVarint(data: ByteArray, offset: Int): Pair<Long, Int>? {
    var value = 0L
    var shift = 0
    var pos = offset
    while (pos < data.size && pos - offset < 10) {
        val b = data[pos].toInt() and 0xFF
        value = value or ((b and 0x7F).toLong() shl shift)
        pos++
        if (b and 0x80 == 0) return value to pos
        shift += 7
    }
    return null
}

private val VERSION_PATTERN = byteArrayOf(0x67) + "version".toByteArray(Charsets.US_ASCII) + byteArrayOf(0x01)

private fun containsVersionPattern(header: ByteArray): Boolean {
    if (header.size < VERSION_PATTERN.size) return false
    outer@ for (start in 0..header.size - VERSION_PATTERN.size) {
        for (i in VERSION_PATTERN.indices) {
            if (header[start + i] != VERSION_PATTERN[i]) continue@outer
        }
        return true
    }
    return false
}

/** Parse a CIDv1; returns its total length or null when incomplete/invalid. */
private fun parseCidV1Length(data: ByteArray, offset: Int): Int? {
    val (version, p1) = readVarint(data, offset) ?: return null
    if (version != 1L) return null
    val (_, p2) = readVarint(data, p1) ?: return null // codec
    val (_, p3) = readVarint(data, p2) ?: return null // multihash code
    val (digestLen, p4) = readVarint(data, p3) ?: return null
    if (digestLen <= 0 || digestLen > 128) return null
    if (data.size - p4 < digestLen) return null
    return (p4 - offset) + digestLen.toInt()
}

private fun decide(prefix: ByteArray): Decision {
    val (headerLen, headerStart) = readVarint(prefix, 0) ?: return Decision.Undecided
    if (headerLen <= 0 || headerLen > MAX_PREFIX_BYTES) return Decision.NotCar
    val headerLenInt = headerLen.toInt()
    if (prefix.size - headerStart < headerLenInt) return Decision.Undecided
    val header = prefix.copyOfRange(headerStart, headerStart + headerLenInt)
    if (header.isEmpty() || header[0].toInt() and 0xFF !in 0xA1..0xBF) return Decision.NotCar
    if (!containsVersionPattern(header)) return Decision.NotCar
    var pos = headerStart + headerLenInt
    val (frameLen, frameDataStart) = readVarint(prefix, pos) ?: return Decision.Undecided
    if (frameLen <= 0) return Decision.NotCar
    pos = frameDataStart
    val cidLen = parseCidV1Length(prefix, pos)
    if (cidLen == null) {
        // CID might be split across network chunks — wait for more unless unreasonable.
        return if (prefix.size - pos > MAX_PREFIX_BYTES) Decision.NotCar else Decision.Undecided
    }
    val dataLength = frameLen - cidLen
    if (dataLength <= 0) return Decision.NotCar
    return Decision.Car(dataOffset = pos + cidLen, dataLength = dataLength)
}
