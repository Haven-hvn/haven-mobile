package haven.mobile.core.collections

/**
 * A community whose archive a reader can get into.
 *
 * Note what is **not** on this type: no contract address, no chain, no token standard, no threshold
 * arithmetic, no decimals. Those exist — they are how access is actually decided — but they live in
 * `internal` types inside this module, so a screen physically cannot render them. That is deliberate
 * enforcement rather than discipline: the web surface is written for ecosystem developers and shows
 * all of it, and the phone app must not turn into that by accretion.
 *
 * What a reader gets instead is the four things they can act on: what the community is, what it
 * takes to get in, whether they are already in, and where to go if they are not.
 */
data class Collection(
    /** Stable key. Used for list identity and deep links, never displayed. */
    val id: String,
    val name: String,
    val category: CollectionCategory,
    /** One sentence: what a community like this keeps in an archive. */
    val premise: String,
    /** Plain-language entry condition, e.g. "Hold any 1 Noun" or "Hold 75 FWB". */
    val requirement: String,
    /** Where to acquire it, and the name of that place, for the button label. */
    val marketUrl: String,
    val marketName: String,
)

enum class CollectionCategory(val label: String) {
    LORE("Lore"),
    ART("Art"),
    CULTURE("Culture"),
    GOVERNANCE("Governance"),
}

/** Whether the connected wallet can open this community's archive. */
enum class Access {
    /** Holds what the community asks for. */
    GRANTED,

    /** Does not hold it — the "get access" path applies. */
    MISSING,

    /**
     * Not determined. Offline, or no balance source is configured in this build.
     *
     * A separate state from [MISSING] on purpose: telling a holder they do not have access because
     * the network was unreachable is worse than saying nothing.
     */
    UNKNOWN,
}

data class CollectionAccess(
    val collection: Collection,
    val access: Access,
)
