package haven.mobile.core.domain

import cloud.filecoin.foc.cache.PieceRef
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

/**
 * The key gate must never spend (signature, canister call) on content that
 * cannot open, and must never probe when a cached key already answers.
 */
class KeyGateTest {

    private fun item(id: String, ref: PieceRef? = ref(id)) = MediaItem(
        id = id,
        kind = MediaKind.VIDEO,
        owner = "0xabc",
        title = "Title $id",
        description = null,
        mimeType = null,
        fileExtension = null,
        filenameHint = null,
        sizeBytes = 1_024,
        createdAt = kotlinx.datetime.Instant.parse("2026-01-01T00:00:00Z"),
        createdAtBlock = null,
        expiresAtBlock = null,
        pieceRef = ref,
        filecoinCid = null,
        encryptedCid = null,
        cidHash = null,
        gate = null,
        isEncrypted = true,
        encryptionMetadata = null,
        cidEncryptionMetadata = null,
        attestation = null,
        arkivStatus = ArkivStatus.FRESH,
        contentCacheStatus = ContentCacheStatus.CACHED,
        lastAccessedAt = null,
    )

    private fun ref(cid: String) = PieceRef(
        pieceCid = "bafkzcib-$cid",
        size = 0,
        providerServiceUrls = emptyList(),
    )

    @Test
    fun `cached keys pass without probing`() = runBlocking {
        var probes = 0
        val items = listOf(item("a"), item("b"))
        val verdicts = gateKeyAcquisition(
            items = items,
            isKeyCached = { true },
            isAvailable = { probes++; true },
        )
        assertEquals(
            listOf(items[0] to KeyGateVerdict.CACHED, items[1] to KeyGateVerdict.CACHED),
            verdicts,
        )
        assertEquals(0, probes)
    }

    @Test
    fun `items without a ref pass without probing`() = runBlocking {
        var probes = 0
        val items = listOf(item("a", ref = null))
        val verdicts = gateKeyAcquisition(
            items = items,
            isKeyCached = { false },
            isAvailable = { probes++; true },
        )
        assertEquals(listOf(items[0] to KeyGateVerdict.AVAILABLE), verdicts)
        assertEquals(0, probes)
    }

    @Test
    fun `unavailable pieces fail closed in input order`() = runBlocking {
        val items = listOf(item("a"), item("b"), item("c"))
        val verdicts = gateKeyAcquisition(
            items = items,
            isKeyCached = { it.id == "c" },
            isAvailable = { ref -> ref.pieceCid != "bafkzcib-a" },
        )
        assertEquals(
            listOf(
                items[0] to KeyGateVerdict.UNAVAILABLE,
                items[1] to KeyGateVerdict.AVAILABLE,
                items[2] to KeyGateVerdict.CACHED,
            ),
            verdicts,
        )
    }

    @Test
    fun `throwing probe fails closed`() = runBlocking {
        val items = listOf(item("a"))
        val verdicts = gateKeyAcquisition(
            items = items,
            isKeyCached = { false },
            isAvailable = { throw RuntimeException("network down") },
        )
        assertEquals(listOf(items[0] to KeyGateVerdict.UNAVAILABLE), verdicts)
    }

    @Test
    fun `probes run concurrently`() = runBlocking {
        val items = (1..4).map { item("id$it") }
        val elapsed = measureTimeMillis {
            gateKeyAcquisition(
                items = items,
                isKeyCached = { false },
                isAvailable = { delay(300); true },
            )
        }
        // Sequential probing would take at least 1200ms; concurrently it is one probe long.
        assertTrue(elapsed < 1000, "probes ran sequentially: ${elapsed}ms")
    }
}
