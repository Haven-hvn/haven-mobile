package haven.mobile.core.collections

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import haven.mobile.core.domain.HavenChain
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
 * The bundled roster.
 *
 * Ported from the web's gate list and shipped in the APK because there is no public index to
 * discover communities from yet. Parsed once and held — sixteen entries is not worth a cache
 * invalidation story.
 */
@Singleton
internal class CollectionCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var entries: List<CatalogEntry>? = null

    suspend fun entries(): List<CatalogEntry> {
        entries?.let { return it }
        return withContext(Dispatchers.IO) {
            val parsed = runCatching { parse() }.getOrDefault(emptyList())
            entries = parsed
            parsed
        }
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
