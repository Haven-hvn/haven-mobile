package haven.mobile.core.cache.mirror

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Settings live under the active identity's namespace: two wallets must not
 * see each other's quota/TTL/chains, and a pre-multi-identity install's
 * global values remain the fallback for its first identity.
 */
class ScopedSettingsTest {

    @Test
    fun `scoped key namespaces by wallet`() {
        assertEquals("id/0xaaa/cache.quota_bytes", scopedSettingsKey("0xaaa", "cache.quota_bytes"))
        assertEquals("id/0xbbb/cache.quota_bytes", scopedSettingsKey("0xbbb", "cache.quota_bytes"))
    }

    @Test
    fun `no wallet falls back to the plain key`() {
        assertEquals("cache.quota_bytes", scopedSettingsKey(null, "cache.quota_bytes"))
        assertEquals("cache.quota_bytes", scopedSettingsKey("", "cache.quota_bytes"))
    }

    @Test
    fun `scoped value wins, then legacy, then default`() {
        assertEquals(5L, resolveScoped(5L, 9L, 0L))
        assertEquals(9L, resolveScoped(null, 9L, 0L))
        assertEquals(0L, resolveScoped(null, null, 0L))
    }
}
