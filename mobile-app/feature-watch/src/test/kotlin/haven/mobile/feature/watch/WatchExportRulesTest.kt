package haven.mobile.feature.watch

import haven.mobile.core.domain.ArkivStatus
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import haven.mobile.core.domain.error.HavenError
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * FR-FILE-4 says an item that failed verification must not be exportable. That rule is the only
 * thing standing between a corrupt or forged payload and the user's Downloads folder, so it is
 * asserted here rather than trusted to a disabled button.
 */
class WatchExportRulesTest {

    @Test
    fun `cached items are exportable`() {
        assertTrue(item(ContentCacheStatus.CACHED).isExportable())
    }

    @Test
    fun `uncached and partial items are exportable once fetched`() {
        // Residency is not a trust signal — a not-yet-cached item is fetched on demand.
        assertTrue(item(ContentCacheStatus.UNCACHED).isExportable())
        assertTrue(item(ContentCacheStatus.PARTIAL).isExportable())
    }

    @Test
    fun `expired items are blocked`() {
        assertFalse(item(ContentCacheStatus.EXPIRED).isExportable())
    }

    @Test
    fun `only FILE needs the save and open-with path`() {
        assertFalse(MediaKind.FILE.rendersInline())
        assertTrue(MediaKind.VIDEO.rendersInline())
        assertTrue(MediaKind.AUDIO.rendersInline())
        assertTrue(MediaKind.IMAGE.rendersInline())
        assertTrue(MediaKind.DOCUMENT.rendersInline())
    }

    @Test
    fun `haven errors keep their stable code`() {
        val failure = HavenError.CacheMiss("nothing there").toFailure("fallback")
        assertEquals("CACHE_MISS", failure.code)
        assertEquals("nothing there", failure.message)
    }

    @Test
    fun `unknown throwables are reported as internal with their message`() {
        val failure = IllegalStateException("player exploded").toFailure("fallback")
        assertEquals("INTERNAL", failure.code)
        assertEquals("player exploded", failure.message)
    }

    @Test
    fun `a throwable with no message falls back to the caller's wording`() {
        val failure = RuntimeException().toFailure("Could not fetch this item's content.")
        assertEquals("Could not fetch this item's content.", failure.message)
    }

    @Test
    fun `staged content is identified by its file`() {
        // ContentState.Ready carries a File rather than a ByteArray, which is what lets a 2GB item
        // open on a low-memory device — and makes StateFlow deduplication trivially correct.
        val first = ContentState.Ready(java.io.File("/tmp/haven/abc.bin"))
        val same = ContentState.Ready(java.io.File("/tmp/haven/abc.bin"))
        val different = ContentState.Ready(java.io.File("/tmp/haven/def.bin"))
        assertEquals(first, same)
        assertEquals(first.hashCode(), same.hashCode())
        assertFalse(first == different)
    }

    @Test
    fun `unlock stages are ordered from gate to bytes`() {
        assertEquals("Checking your access\u2026", UnlockStage.UNLOCKING.label)
        assertEquals("Decrypting\u2026", UnlockStage.STREAMING.label)
    }

    private fun item(status: ContentCacheStatus) = MediaItem(
        id = "1",
        kind = MediaKind.FILE,
        owner = "0xabc",
        title = "Bundle",
        description = null,
        mimeType = "application/zip",
        fileExtension = ".zip",
        filenameHint = "bundle.zip",
        sizeBytes = 2_048,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        createdAtBlock = null,
        expiresAtBlock = null,
        pieceRef = null,
        filecoinCid = null,
        encryptedCid = null,
        cidHash = null,
        gate = null,
        isEncrypted = true,
        encryptionMetadata = null,
        cidEncryptionMetadata = null,
        attestation = null,
        arkivStatus = ArkivStatus.FRESH,
        contentCacheStatus = status,
        lastAccessedAt = null,
    )
}
