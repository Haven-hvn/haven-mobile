package haven.mobile.core.collections

import haven.mobile.core.domain.TokenGate
import haven.mobile.core.domain.TokenStandard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The roster is live-only: Arkiv's gate index, no bundled seed.
 * A static list advertises communities with no Haven DataDAO behind them.
 */
class CollectionCatalogTest {

    private fun liveGate(
        address: String = "0x1111111111111111111111111111111111111111",
        chain: String = "eip155:8453",
        threshold: Double = 1.0,
    ) = TokenGate(chain = chain, tokenAddress = address, threshold = threshold)

    @Test
    fun `empty index returns empty`() {
        assertTrue(liveEntries(emptyList()).isEmpty())
    }

    @Test
    fun `live gate becomes an entry with derived display`() {
        val entries = liveEntries(listOf(liveGate()))
        assertEquals(1, entries.size)
        val live = entries.single()
        assertEquals("live:8453:0x1111111111111111111111111111111111111111", live.collection.id)
        assertEquals("0x111111…111111", live.collection.name)
        assertEquals("Hold 1 0x111111…111111", live.collection.requirement)
        assertEquals(
            "https://mint.club/token/base/0x1111111111111111111111111111111111111111",
            live.collection.marketUrl,
        )
    }

    @Test
    fun `duplicate live gates dedupe to one entry`() {
        val addr = "0x1111111111111111111111111111111111111111"
        val entries = liveEntries(listOf(liveGate(address = addr), liveGate(address = addr)))
        assertEquals(1, entries.size)
    }

    @Test
    fun `live gate on unknown chain is dropped`() {
        assertTrue(liveEntries(listOf(liveGate(chain = "eip155:999999"))).isEmpty())
        assertNull(liveGate(chain = "eip155:999999").toCatalogEntry())
    }

    @Test
    fun `erc721 live gate maps to collection kind`() {
        val entry = liveGate().copy(tokenStandard = TokenStandard.ERC721).toCatalogEntry()!!
        assertEquals(GateSpec.Kind.COLLECTION, entry.gate.kind)
        assertTrue(entry.collection.requirement.contains("collectible"))
    }
}
