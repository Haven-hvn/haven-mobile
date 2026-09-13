package haven.mobile.core.collections

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import haven.mobile.core.arkiv.ArkivClient
import haven.mobile.core.domain.HavenChain
import haven.mobile.core.domain.TokenGate
import haven.mobile.core.domain.TokenStandard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
 * The community roster: a bundled seed plus Arkiv's live gate index.
 *
 * The bundled file seeds curated entries (names, market links) and is the offline fallback.
 * The live part comes from Arkiv's entity attributes via [ArkivClient.discoverGates], so a
 * community published after the APK was built still appears — with derived display text — as
 * soon as the chain is reachable. A failed live fetch never fails this call; it just
 * returns the seed.
 */
@Singleton
internal class CollectionCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val arkivClient: ArkivClient,
) {
    @Volatile
    private var seed: List<CatalogEntry>? = null

    suspend fun entries(): List<CatalogEntry> {
        val bundled = seed ?: withContext(Dispatchers.IO) {
            runCatching { parse() }.getOrDefault(emptyList())
        }.also { seed = it }
        val live = withContext(Dispatchers.IO) {
            arkivClient.discoverGates().getOrDefault(emptyList())
        }
        return mergeEntries(bundled, live)
    }

    private fun parse(): List<CatalogEntry> {
        val json = context.resources.openRawResource(R.raw.collections)
            .bufferedReader()
            .use { it.readText() }

        val array = JSONObject(json).getJSONArray("collections")
        return (0 until array.length()).mapNotNull { index ->
            runCatching { array.getJSONObject(index).toEntry() }.getOrNull()
        }
    }

    private fun JSONObject.toEntry(): CatalogEntry {
        val market = getJSONObject("market")
        val gate = getJSONObject("gate")
        val id = getString("id")
        return CatalogEntry(
            collection = Collection(
                id = id,
                name = getString("name"),
                // An unrecognised category is a roster typo, not a reason to drop a community.
                category = runCatching { CollectionCategory.valueOf(getString("category")) }
                    .getOrDefault(CollectionCategory.CULTURE),
                premise = getString("premise"),
                requirement = getString("requirement"),
                marketUrl = market.getString("url"),
                marketName = market.getString("name"),
            ),
            gate = GateSpec(
                id = id,
                address = gate.getString("address"),
                kind = if (gate.getString("kind") == "token") {
                    GateSpec.Kind.TOKEN
                } else {
                    GateSpec.Kind.COLLECTION
                },
                threshold = BigInteger.valueOf(gate.getLong("threshold")),
            ),
            // Optional in the roster; every current entry is Ethereum mainnet. An unrecognised value
            // falls back rather than dropping the community, but it is logged by its absence from the
            // access check for that chain.
            chain = HavenChain.parse(gate.optString("chain", null)) ?: HavenChain.ETH_MAINNET,
        )
    }
}

/**
 * Merges the bundled seed with live gates from the Arkiv index.
 *
 * Seed entries win on key collisions so curated names and market links survive; live gates not
 * in the seed are appended with derived display text. Pure for testability.
 */
internal fun mergeEntries(bundled: List<CatalogEntry>, live: List<TokenGate>): List<CatalogEntry> {
    if (live.isEmpty()) return bundled
    val known = bundled.mapNotNull { it.asTokenGate().gateKeyOrNull()?.lowercase() }.toSet()
    val fresh = live
        .distinctBy { it.gateKeyOrNull()?.lowercase() }
        .filter { it.gateKeyOrNull()?.lowercase() !in known }
        .mapNotNull { it.toCatalogEntry() }
    return bundled + fresh
}

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
