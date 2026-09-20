package haven.mobile.core.collections

import haven.mobile.core.arkiv.ArkivClient
import haven.mobile.core.domain.HavenChain
import haven.mobile.core.domain.TokenGate
import haven.mobile.core.domain.TokenStandard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How access is actually decided — and why this type is `internal`.
 *
 * A contract address, a token standard and a threshold in base units are the mechanics of the gate.
 * They are necessary here and meaningless to a reader, so they stop at this module boundary: the UI
 * layer cannot import this type, so it cannot leak it into a screen by accident.
 */
internal data class GateSpec(
    val id: String,
    val address: String,
    val kind: Kind,
    /** Whole units required. For a token, scaled by the contract's decimals at check time. */
    val threshold: BigInteger,
) {
    enum class Kind { COLLECTION, TOKEN }
}

internal data class CatalogEntry(
    val collection: Collection,
    val gate: GateSpec,
    /**
     * Which chain this community's asset lives on.
     *
     * Held on the entry rather than assumed: the roster is all Ethereum today, but Haven-AOL evaluates
     * gates on five chains and a Base-native community is a matter of one JSON line, not a code change.
     */
    val chain: HavenChain,
)

/**
 * The community roster: Arkiv's live gate index, nothing else.
 *
 * Every entry is a distinct gate condition actually recorded on Arkiv via
 * [ArkivClient.discoverGates]. There is no bundled seed — a static list would advertise
 * communities with no Haven DataDAO behind them. An unreachable index or an empty archive
 * honestly returns empty, same as Launches.
 */
@Singleton
internal class CollectionCatalog @Inject constructor(
    private val arkivClient: ArkivClient,
) {
    suspend fun entries(chains: Set<HavenChain> = HavenChain.mainnets.toSet()): List<CatalogEntry> {
        val live = withContext(Dispatchers.IO) {
            arkivClient.discoverGates(chains).getOrDefault(emptyList())
        }
        return live
            .distinctBy { it.gateKeyOrNull()?.lowercase() }
            .mapNotNull { runCatching { it.toCatalogEntry() }.getOrNull() }
    }
}

/**
 * Live Arkiv gates as roster entries. Pure for testability.
 *
 * Distinct by gate key so one community per (chain, contract); unknown chains drop via
 * [toCatalogEntry]'s null.
 */
internal fun liveEntries(live: List<TokenGate>): List<CatalogEntry> =
    live
        .distinctBy { it.gateKeyOrNull()?.lowercase() }
        .mapNotNull { runCatching { it.toCatalogEntry() }.getOrNull() }

/** A live Arkiv gate as a roster entry, with display text derived from the gate itself. */
internal fun TokenGate.toCatalogEntry(): CatalogEntry? {
    val chain = HavenChain.parse(chain) ?: return null
    val short = tokenAddress.shortAddress()
    val amount = if (threshold == kotlin.math.floor(threshold)) {
        threshold.toLong().toString()
    } else {
        threshold.toString()
    }
    val what = if (tokenStandard == TokenStandard.ERC721) "a $short collectible" else "$amount $short"
    return CatalogEntry(
        collection = Collection(
            id = "live:${chain.chainId}:${tokenAddress.lowercase()}",
            name = short,
            category = CollectionCategory.CULTURE,
            premise = "A community on Arkiv gated by holding $what.",
            requirement = "Hold $what",
            marketUrl = "https://mint.club/token/${chain.mintClubKey}/${tokenAddress}",
            marketName = "mint.club",
        ),
        gate = GateSpec(
            id = "live:${chain.chainId}:${tokenAddress.lowercase()}",
            address = tokenAddress,
            kind = if (tokenStandard == TokenStandard.ERC721) GateSpec.Kind.COLLECTION else GateSpec.Kind.TOKEN,
            // Live thresholds arrive as whole units (see ArkivClient.toTokenGate); the checker
            // scales ERC-20 thresholds by decimals at check time, same as for seed entries.
            threshold = BigInteger.valueOf(threshold.toLong()),
        ),
        chain = chain,
    )
}

internal fun String.shortAddress(): String {
    val t = trim()
    if (t.length > 14 && t.startsWith("0x")) return "${t.take(8)}…${t.takeLast(6)}"
    return t
}
