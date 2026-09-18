package haven.mobile.feature.launches

import haven.mobile.core.domain.HavenChain
import haven.mobile.core.domain.LaunchStage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The launches tab is global discovery: rows carry a stage line, the pump itself happens in-app
 * on the watch screen, and the list surfaces the newest launch first. These pin the row facts
 * and the filter/sort the list is drawn from.
 */
class LaunchDisplayTest {

    private fun stage(
        dripId: String = "drip-1",
        index: Int = 0,
        title: String = "Genesis Drop",
        token: String = "0x1111111111111111111111111111111111111111",
        chain: HavenChain? = HavenChain.BASE_MAINNET,
        target: Long = 5_000_000,
        total: Int = 3,
        creator: String? = "polycat",
        block: Long? = 100,
    ) = LaunchStage(
        id = "$dripId:$index",
        title = title,
        gateToken = token,
        gateChain = chain,
        marketCapTargetUsd = target,
        dripIndex = index,
        dripTotal = total,
        dripId = dripId,
        creatorHandle = creator,
        createdAtBlock = block,
    )

    @Test
    fun `row line mirrors the dapp drop row`() {
        assertEquals("Stage 2/3 · unlocks at \$5M", stageLabel(stage(index = 1)))
    }

    @Test
    fun `usd compacts like the dapp`() {
        assertEquals("\$950", formatUsdCompact(950))
        assertEquals("\$800K", formatUsdCompact(800_000))
        assertEquals("\$1.5M", formatUsdCompact(1_500_000))
        assertEquals("\$1B", formatUsdCompact(1_000_000_000))
    }

    @Test
    fun `filter matches title, token, creator and launch id`() {
        val items = listOf(
            stage(title = "Neon Nights"),
            stage(dripId = "drip-2", title = "Midnight", token = "0x2222222222222222222222222222222222222222"),
        )
        assertEquals(listOf(items[0]), filterLaunches(items, "neon"))
        assertEquals(listOf(items[0]), filterLaunches(items, "0x1111"))
        assertEquals(items, filterLaunches(items, "polycat"))
        assertEquals(listOf(items[1]), filterLaunches(items, "drip-2"))
    }

    @Test
    fun `newest launch first, stages in index order`() {
        val old = listOf(stage(dripId = "old", index = 1, block = 10), stage(dripId = "old", index = 0, block = 9))
        val new = listOf(stage(dripId = "new", index = 0, block = 50))
        val unknown = listOf(stage(dripId = "unknown", index = 0, block = null))
        val items = old + unknown + new
        assertEquals(
            listOf(new[0], old[1], old[0], unknown[0]),
            filterLaunches(items, ""),
        )
    }
}
