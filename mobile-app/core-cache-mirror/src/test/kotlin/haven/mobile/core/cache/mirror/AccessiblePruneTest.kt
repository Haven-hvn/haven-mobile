package haven.mobile.core.cache.mirror

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Holder-not-uploader mirror pruning: a consumer's library is other
 * people's uploads unlocked by held tokens. Selling the token must
 * remove those rows on the next clean refresh, while still-held and
 * own items stay.
 */
class AccessiblePruneTest {

    @Test
    fun `sold token prunes the community items it unlocked`() {
        val current = listOf("own-1", "community-a", "community-b")
        val fetchedAfterSale = listOf("own-1")
        assertEquals(
            listOf("community-a", "community-b"),
            staleMirrorIds(current, fetchedAfterSale),
        )
    }

    @Test
    fun `still-held items are kept`() {
        val current = listOf("own-1", "community-a")
        assertTrue(staleMirrorIds(current, current).isEmpty())
    }

    @Test
    fun `holder with no uploads keeps a pure community library`() {
        // Fetched set is entirely other creators' items; nothing is stale
        // while the token is held.
        val fetched = listOf("community-a", "community-b")
        assertTrue(staleMirrorIds(fetched, fetched).isEmpty())
        // And after selling, the whole library prunes.
        assertEquals(fetched, staleMirrorIds(fetched, emptyList()))
    }
}
