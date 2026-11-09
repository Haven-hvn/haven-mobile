package haven.mobile.core.wallet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The switch contract: identities are per-wallet partitions, and switching
 * only moves the active pointer — it never removes or wipes an entry.
 */
class IdentityRegistryTest {

    private val a = "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    private val b = "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"

    @Test
    fun `codec round-trips identities`() {
        val identities = listOf(
            WalletIdentity(a, label = "Main", connector = "WalletConnect", addedAtMs = 1, lastActiveAtMs = 2),
            WalletIdentity(b, label = "", connector = "", addedAtMs = 3, lastActiveAtMs = 4),
        )
        assertEquals(identities, IdentitiesCodec.decode(IdentitiesCodec.encode(identities)))
    }

    @Test
    fun `codec degrades malformed input to empty`() {
        assertTrue(IdentitiesCodec.decode(null).isEmpty())
        assertTrue(IdentitiesCodec.decode("not json").isEmpty())
        assertTrue(IdentitiesCodec.decode("[{}]").isEmpty())
    }

    @Test
    fun `upsert dedupes case-insensitively and keeps the label`() {
        var identities = IdentityRegistry.upsert(emptyList(), a.lowercase(), "WalletConnect", nowMs = 10)
        identities = IdentityRegistry.rename(identities, a, "Cold")
        identities = IdentityRegistry.upsert(identities, a, "WalletConnect", nowMs = 20)
        assertEquals(1, identities.size)
        assertEquals("Cold", identities.single().label)
        assertEquals(20, identities.single().lastActiveAtMs)
    }

    @Test
    fun `switching changes only lastActive, never the set`() {
        var identities = IdentityRegistry.upsert(emptyList(), a, nowMs = 1)
        identities = IdentityRegistry.upsert(identities, b, nowMs = 2)
        val switched = IdentityRegistry.touch(identities, a, nowMs = 99)
        assertEquals(identities.map { it.id }.toSet(), switched.map { it.id }.toSet())
        assertEquals(99, switched.first { it.id == a.lowercase() }.lastActiveAtMs)
        assertEquals(2, switched.first { it.id == b.lowercase() }.lastActiveAtMs)
    }

    @Test
    fun `forget activates the most recently used remaining identity`() {
        var identities = IdentityRegistry.upsert(emptyList(), a, nowMs = 1)
        identities = IdentityRegistry.upsert(identities, b, nowMs = 5)
        val (remaining, nextActive) = IdentityRegistry.forget(identities, activeId = a.lowercase(), address = a)
        assertEquals(listOf(b.lowercase()), remaining.map { it.id })
        assertEquals(b.lowercase(), nextActive)

        val (empty, none) = IdentityRegistry.forget(remaining, activeId = b.lowercase(), address = b)
        assertTrue(empty.isEmpty())
        assertNull(none)
    }

    @Test
    fun `forgetting a stored identity keeps the active one`() {
        var identities = IdentityRegistry.upsert(emptyList(), a, nowMs = 1)
        identities = IdentityRegistry.upsert(identities, b, nowMs = 5)
        val (_, nextActive) = IdentityRegistry.forget(identities, activeId = a.lowercase(), address = b)
        assertEquals(a.lowercase(), nextActive)
    }

    @Test
    fun `legacy single wallet migrates to a one-entry registry`() {
        val migrated = IdentityRegistry.migrateLegacy(a, "WalletConnect", nowMs = 7)
        assertEquals(1, migrated.size)
        assertEquals(a, migrated.single().address)
        assertTrue(IdentityRegistry.migrateLegacy(null).isEmpty())
        assertTrue(IdentityRegistry.migrateLegacy("  ").isEmpty())
    }

    @Test
    fun `display label falls back to a short address and hue is stable`() {
        val identity = WalletIdentity(a)
        assertEquals(WalletIdentity.shortAddress(a), identity.displayLabel)
        assertEquals(WalletIdentity.hueFor(a), WalletIdentity.hueFor(a.lowercase()))
        assertTrue(WalletIdentity.hueFor(a) in 0..359)
    }
}
