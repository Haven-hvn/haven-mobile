package haven.mobile.core.design.component

import haven.mobile.core.domain.ContentCacheStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The library row/card rule: the default state gets no badge.
 *
 * A chip on every row for "nothing is stored here" teaches the reader that
 * their library is broken. Only states a reader can act on — or be glad
 * about — earn a chip in a list. The detail sheet still names every state.
 */
class CacheChipTest {

    @Test
    fun `uncached is silent in lists`() {
        assertFalse(cacheChipVisibleInList(ContentCacheStatus.UNCACHED))
    }

    @Test
    fun `actionable and positive states stay visible in lists`() {
        assertTrue(cacheChipVisibleInList(ContentCacheStatus.CACHED))
        assertTrue(cacheChipVisibleInList(ContentCacheStatus.PARTIAL))
        assertTrue(cacheChipVisibleInList(ContentCacheStatus.EXPIRED))
    }

    @Test
    fun `labels name the reader's situation, not the cache`() {
        assertEquals("Offline", cacheChipLabel(ContentCacheStatus.CACHED))
        assertEquals("Not on this device", cacheChipLabel(ContentCacheStatus.UNCACHED))
    }
}
