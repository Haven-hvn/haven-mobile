package haven.mobile.core.collections

import haven.mobile.core.domain.HavenChain
import haven.mobile.core.domain.TokenGate
import haven.mobile.core.domain.TokenStandard
import haven.mobile.core.wallet.WalletSession
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The roster, answered for the connected wallet.
 *
 * On the web this is two separate steps — browse the communities, then paste an address to check them —
 * because a page cannot hold a wallet. Here the address is already known, so checking is not a step the
 * reader performs: it is a property of the list.
 */
interface CollectionRepository {
    /**
     * Every known community with an access verdict, in roster order.
     *
     * Never fails: with no wallet or no reachable node the verdicts come back [Access.UNKNOWN] and the
     * list is still useful — browsing and acquiring both work without one.
     */
    suspend fun collections(chains: Set<HavenChain> = HavenChain.mainnets.toSet()): List<CollectionAccess>

    /**
     * The gates from this roster that the wallet can open, for the data layer to query content against.
     *
     * The one place a contract address leaves this module, and it leaves as a `core-domain.TokenGate`
     * rather than as UI-shaped data. `Collection` still carries none of it, so a screen cannot render an
     * address by accident.
     */
    suspend fun accessibleGates(chains: Set<HavenChain> = HavenChain.mainnets.toSet()): List<TokenGate>
}

@Singleton
internal class CollectionRepositoryImpl @Inject constructor(
    private val catalog: CollectionCatalog,
    private val accessChecker: GateAccessChecker,
    private val walletSession: WalletSession,
) : CollectionRepository {

    override suspend fun collections(chains: Set<HavenChain>): List<CollectionAccess> {
        val entries = catalog.entries()
        if (entries.isEmpty()) return emptyList()

        val address = walletSession.address.value
        val satisfied = if (address == null) {
            emptySet()
        } else {
            accessChecker.satisfied(address, entries.map { it.asTokenGate() }, chains)
        }

        return entries.map { entry ->
            val key = entry.asTokenGate().gateKeyOrNull()
            CollectionAccess(
                collection = entry.collection,
                access = when {
                    address == null -> Access.UNKNOWN
                    key == null -> Access.UNKNOWN
                    key in satisfied -> Access.GRANTED
                    // The checker omits gates it could not read, so "not in the set" is ambiguous
                    // between "does not hold" and "could not tell". Treated as MISSING only when the
                    // wallet is connected and the chain was in scope — otherwise the reader is told
                    // they lack something because a node was down.
                    entry.chain in chains -> Access.MISSING
                    else -> Access.UNKNOWN
                },
            )
        }
    }

    override suspend fun accessibleGates(chains: Set<HavenChain>): List<TokenGate> {
        val entries = catalog.entries()
        if (entries.isEmpty()) return emptyList()
        val address = walletSession.address.value ?: return emptyList()

        val gates = entries.map { it.asTokenGate() }
        val satisfied = accessChecker.satisfied(address, gates, chains)

        // Confirmed holdings only. An unread verdict is not an invitation to query a gate the reader
        // may not be able to open — that fills a library with items every unlock then refuses.
        return gates.filter { it.gateKeyOrNull() in satisfied }
    }
}

/** Roster entry as a domain gate, so one checker serves both the roster and Arkiv's own conditions. */
internal fun CatalogEntry.asTokenGate(): TokenGate = TokenGate(
    chain = chain.caip2,
    tokenAddress = gate.address,
    threshold = gate.threshold.toDouble(),
    tokenStandard = when (gate.kind) {
        GateSpec.Kind.COLLECTION -> TokenStandard.ERC721
        GateSpec.Kind.TOKEN -> TokenStandard.ERC20
    },
)
