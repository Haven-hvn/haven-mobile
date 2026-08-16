package haven.mobile.feature.library

import haven.mobile.core.domain.ArkivStatus
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Filtering was the bug that motivated rewriting this ViewModel: category selection re-emitted an
 * identical state object, `StateFlow` dropped it as a duplicate, and the chips did nothing. Now the
 * filter is a pure function over the emitted state, which is exactly what a test can pin down.
 */
class LibraryFilterTest {

    private val video = item(id = "1", title = "Founding interview", kind = MediaKind.VIDEO, day = 3)
    private val audio = item(id = "2", title = "Board call", kind = MediaKind.AUDIO, day = 5)
    private val doc = item(
        id = "3",
        title = "Charter",
        kind = MediaKind.DOCUMENT,
        day = 1,
        description = "Founding document of the archive",
    )
    private val file = item(id = "4", title = "Bundle", kind = MediaKind.FILE, day = 2)
    private val all = listOf(video, audio, doc, file)

    @Test
    fun `all category returns everything`() {
        val result = applyFilters(all, filters(category = LibraryCategory.ALL))
        assertEquals(4, result.size)
    }

    @Test
    fun `category narrows to one kind`() {
        val result = applyFilters(all, filters(category = LibraryCategory.AUDIO))
        assertEquals(listOf("2"), result.map { it.id })
    }

    @Test
    fun `newest first regardless of input order`() {
        val result = applyFilters(listOf(doc, video, file, audio), filters())
        assertEquals(listOf("2", "1", "4", "3"), result.map { it.id })
    }

    @Test
    fun `query matches the title case-insensitively`() {
        val result = applyFilters(all, filters(query = "FOUNDING"))
        assertEquals(listOf("1", "3"), result.map { it.id })
    }

    @Test
    fun `query matches the description`() {
        val result = applyFilters(all, filters(query = "archive"))
        assertEquals(listOf("3"), result.map { it.id })
    }

    @Test
    fun `whitespace-only query is treated as no query`() {
        assertEquals(4, applyFilters(all, filters(query = "   ")).size)
    }

    @Test
    fun `query and category compose`() {
        // "call" matches the audio item's title, but the DOCUMENT filter excludes it.
        assertEquals(
            emptyList<String>(),
            applyFilters(all, filters(query = "call", category = LibraryCategory.DOCUMENT)).map { it.id },
        )
    }

    @Test
    fun `no match returns empty rather than everything`() {
        assertEquals(emptyList<MediaItem>(), applyFilters(all, filters(query = "zzz")))
    }

    @Test
    fun `counts cover every category and ALL totals the list`() {
        val counts = countByCategory(all)
        assertEquals(LibraryCategory.entries.size, counts.size)
        assertEquals(4, counts[LibraryCategory.ALL])
        assertEquals(1, counts[LibraryCategory.VIDEO])
        assertEquals(1, counts[LibraryCategory.AUDIO])
        assertEquals(1, counts[LibraryCategory.DOCUMENT])
        assertEquals(1, counts[LibraryCategory.FILE])
        assertEquals(0, counts[LibraryCategory.IMAGE])
    }

    @Test
    fun `counts of an empty library are all zero`() {
        val counts = countByCategory(emptyList())
        assertEquals(0, counts[LibraryCategory.ALL])
        assertEquals(0, counts[LibraryCategory.VIDEO])
    }

    @Test
    fun `the offline filter is a choice, not the default`() {
        // Parity with haven-dapp: the library shows everything accessible and badges residency per
        // item. Hiding rows that are not downloaded would misrepresent what the wallet can read.
        val resident = item(id = "r", title = "Resident", kind = MediaKind.VIDEO, day = 4, cache = ContentCacheStatus.CACHED)
        val remote = item(id = "n", title = "Remote", kind = MediaKind.VIDEO, day = 3, cache = ContentCacheStatus.UNCACHED)
        val both = listOf(resident, remote)

        assertEquals(2, applyFilters(both, filters()).size)
        assertEquals(
            listOf("r"),
            applyFilters(both, filters(offlineOnly = true)).map { it.id },
        )
    }

    @Test
    fun `the shelf holds what plays without a connection`() {
        // PARTIAL counts because a partly-resident piece still opens; EXPIRED does not, because
        // opening it needs the network and offering it offline would be a promise the app cannot keep.
        assertTrue(item(id = "a", title = "A", kind = MediaKind.VIDEO, day = 1, cache = ContentCacheStatus.CACHED).isOnDevice())
        assertTrue(item(id = "b", title = "B", kind = MediaKind.VIDEO, day = 1, cache = ContentCacheStatus.PARTIAL).isOnDevice())
        assertFalse(item(id = "c", title = "C", kind = MediaKind.VIDEO, day = 1, cache = ContentCacheStatus.UNCACHED).isOnDevice())
        assertFalse(item(id = "d", title = "D", kind = MediaKind.VIDEO, day = 1, cache = ContentCacheStatus.EXPIRED).isOnDevice())
    }

    private fun filters(
        query: String = "",
        category: LibraryCategory = LibraryCategory.ALL,
        offlineOnly: Boolean = false,
    ) = Filters(
        query = query,
        category = category,
        layout = LibraryLayout.GRID,
        offlineOnly = offlineOnly,
    )

    /** `pieceRef` stays null so this fixture needs nothing from the foc composite build. */
    private fun item(
        id: String,
        title: String,
        kind: MediaKind,
        day: Int,
        description: String? = null,
        cache: ContentCacheStatus = ContentCacheStatus.CACHED,
    ) = MediaItem(
        id = id,
        kind = kind,
        owner = "0xabc",
        title = title,
        description = description,
        mimeType = null,
        fileExtension = null,
        filenameHint = null,
        sizeBytes = 1_024,
        createdAt = Instant.parse("2026-01-0${day}T00:00:00Z"),
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
        contentCacheStatus = cache,
        lastAccessedAt = null,
    )
}
