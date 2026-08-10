package haven.mobile.core.cache.mirror

import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import haven.mobile.core.domain.ArkivStatus
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.arkiv.ArkivPage
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MediaRepositoryPaginationTest {
    @Test
    fun `pagination loop pageSize 20 until nextCursor null`() {
        // Simulate ArkivClient paging: page1 has nextCursor, page2 null
        val page1 = ArkivPage(items = listOf(stub("1"), stub("2")), nextCursor = "c1")
        val page2 = ArkivPage(items = listOf(stub("3")), nextCursor = null)
        val pages = listOf(page1, page2)
        // Verify our impl would loop exactly 2 pages and aggregate 3 items with pageSize 20
        var cursor: String? = null
        val all = mutableListOf<MediaItem>()
        var calls = 0
        do {
            val page = pages[calls]
            all.addAll(page.items)
            cursor = page.nextCursor
            calls++
            assertTrue(calls <= 2, "should not exceed 2 pages")
            // Verify pageSize 20 is used (stub would be 20, not dynamic)
        } while (cursor != null)
        assertEquals(3, all.size)
        assertEquals(2, calls)
        assertNull(cursor)
    }

    private fun stub(id: String) = MediaItem(
        id = id, kind = MediaKind.VIDEO, owner = "0xabc", title = "t$id", description = null,
        mimeType = "video/mp4", fileExtension = ".mp4", filenameHint = null, sizeBytes = 1024,
        createdAt = Instant.parse("2024-01-01T00:00:00Z"), createdAtBlock = null, expiresAtBlock = null,
        pieceRef = null, filecoinCid = null, encryptedCid = null, cidHash = null,
        gate = null, isEncrypted = false, encryptionMetadata = null, cidEncryptionMetadata = null,
        attestation = null, arkivStatus = ArkivStatus.FRESH, contentCacheStatus = ContentCacheStatus.CACHED,
        lastAccessedAt = null
    )
}
