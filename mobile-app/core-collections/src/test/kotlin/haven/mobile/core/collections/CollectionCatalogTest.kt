package haven.mobile.core.collections

import haven.mobile.core.domain.HavenChain
import haven.mobile.core.domain.TokenGate
import haven.mobile.core.domain.TokenStandard
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The roster is dynamic: the bundled seed plus Arkiv's live gate index.
 * These pin the merge — seed wins collisions, unknown chains drop, and an
 * unreachable index degrades to the seed rather than failing.
 */
class CollectionCatalogTest {

    private fun seedEntry(
        address: String = "0xabcDEF1234567890abcdef1234567890ABCDEF12",
        chain: HavenChain = HavenChain.ETH_MAINNET,
    ) = CatalogEntry(
        collection = Collection(
            id = "seed-1",
            name = "Seed DAO",
            category = CollectionCategory.CULTURE,
            premise = "Curated.",
            requirement = "Hold 1 SEED",
            marketUrl = "https://example.com",
            marketName = "example",
        ),
        gate = GateSpec(id = "seed-1", address = address, kind = GateSpec.Kind.TOKEN, threshold = BigInteger.ONE),
        chain = chain,
    )

    private fun liveGate(
        address: String = "0x1111111111111111111111111111111111111111",
        chain: String = "eip155:8453",
        threshold: Double = 1.0,
    ) = TokenGate(chain = chain, tokenAddress = address, threshold = threshold)

    @Test
    fun `empty index returns the seed untouched`() {
        val seed = listOf(seedEntry())
        assertEquals(seed, mergeEntries(seed, emptyList()))
    }

    @Test
    fun `live gate not in seed is appended with derived display`() {
        val merged = mergeEntries(listOf(seedEntry()), listOf(liveGate()))
        assertEquals(2, merged.size)
        val live = merged.last()
        assertEquals("live:8453:0x1111111111111111111111111111111111111111", live.collection.id)
        assertEquals("0x111111…111111", live.collection.name)
        assertEquals("Hold 1 0x111111…111111", live.collection.requirement)
        assertEquals(
            "https://mint.club/token/base/0x1111111111111111111111111111111111111111",
            live.collection.marketUrl,
        )
        assertEquals(HavenChain.BASE_MAINNET, live.chain)
    }

    @Test
    fun `seed wins when the live index repeats it`() {
        val addr = "0xabcDEF1234567890abcdef1234567890ABCDEF12"
        val seed = listOf(seedEntry(address = addr))
        val merged = mergeEntries(seed, listOf(liveGate(address = addr, chain = "eip155:1")))
        assertEquals(1, merged.size)
        assertEquals("Seed DAO", merged.single().collection.name)
    }

    @Test
    fun `live gate on unknown chain is dropped`() {
        assertTrue(mergeEntries(emptyList(), listOf(liveGate(chain = "eip155:999999"))).isEmpty())
        assertNull(liveGate(chain = "eip155:999999").toCatalogEntry())
    }

    @Test
    fun `erc721 live gate maps to collection kind`() {
        val entry = liveGate().copy(tokenStandard = TokenStandard.ERC721).toCatalogEntry()!!
        assertEquals(GateSpec.Kind.COLLECTION, entry.gate.kind)
        assertTrue(entry.collection.requirement.contains("collectible"))
    }
}
