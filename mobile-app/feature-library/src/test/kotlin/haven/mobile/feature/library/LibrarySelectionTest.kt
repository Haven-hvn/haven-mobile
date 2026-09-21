package haven.mobile.feature.library

import haven.mobile.core.domain.ArkivStatus
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The checked set resolves against the visible list, in list order. A stale id — checked
 * before a refresh narrowed the list — must select nothing rather than batch-operating on
 * something the reader can no longer see.
 */
class LibrarySelectionTest {

    private val a = item("a", "First")
    private val b = item("b", "Second")
    private val c = item("c", "Third")
    private val all = listOf(a, b, c)

    @Test
    fun `empty set selects nothing`() {
        assertEquals(emptyList<MediaItem>(), selectionTargets(all, emptySet()))
    }

    @Test
    fun `checked ids resolve in list order`() {
        assertEquals(listOf(a, c), selectionTargets(all, setOf("c", "a")))
    }

    @Test
    fun `stale ids are ignored`() {
        assertEquals(listOf(b), selectionTargets(all, setOf("b", "gone")))
    }

    /** `pieceRef` stays null so this fixture needs nothing from the foc composite build. */
    private fun item(id: String, title: String) = MediaItem(
        id = id,
        kind = MediaKind.VIDEO,
        owner = "0xabc",
        title = title,
        description = null,
        mimeType = null,
        fileExtension = null,
        filenameHint = null,
        sizeBytes = 1_024,
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
        contentCacheStatus = ContentCacheStatus.CACHED,
        lastAccessedAt = null,
    )

    @Test
    fun `failed titles follow list order`() {
        val results = listOf(
            Result.success(ByteArray(0)),
            Result.failure<ByteArray>(RuntimeException("no")),
            Result.failure<ByteArray>(RuntimeException("no")),
        )
        assertEquals(listOf("Second", "Third"), failedTitles(all, results))
    }

    @Test
    fun `failed titles cap at three`() {
        val targets = (1..5).map { item("$it", "Title $it") }
        val results = targets.map { Result.failure<ByteArray>(RuntimeException("no")) }
        assertEquals(listOf("Title 1", "Title 2", "Title 3"), failedTitles(targets, results))
    }

    @Test
    fun `failed suffix is empty when nothing failed`() {
        assertEquals("", failedNamesSuffix(emptyList()))
    }

    @Test
    fun `failed suffix wraps names in parens`() {
        assertEquals(" (Second)", failedNamesSuffix(listOf("Second")))
    }
}
