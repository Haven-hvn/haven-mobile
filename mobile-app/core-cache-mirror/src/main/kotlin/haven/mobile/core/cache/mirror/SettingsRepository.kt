package haven.mobile.core.cache.mirror

import cloud.filecoin.foc.cache.FocChain
import haven.mobile.core.domain.HavenChain
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    /** Bytes, or `AUTOMATIC_QUOTA` (0) for "size automatically" — the default. */
    val cacheQuotaBytes: Flow<Long>
    val cacheTtlDays: Flow<Int>
    val clearOnDisconnect: Flow<Boolean>

    /** Store gated content decrypted so it opens without a key or network. Off by default. */
    val keepUnlocked: Flow<Boolean>

    /**
     * Which of Haven-AOL's chains to check for access.
     *
     * A reader's assets are spread across chains, and every chain in scope is a balance query per gate —
     * so this is both a correctness setting and a cost one. Defaults to the mainnets; Sepolia is
     * available for anyone testing against it and off by default.
     *
     * An empty set is treated as "the defaults" rather than "check nothing", so a bad write cannot
     * silently empty someone's library.
     */
    val enabledChains: Flow<Set<HavenChain>>

    suspend fun setCacheQuotaBytes(bytes: Long)
    suspend fun setCacheTtlDays(days: Int)
    suspend fun setClearOnDisconnect(enabled: Boolean)
    suspend fun setKeepUnlocked(enabled: Boolean)
    suspend fun setEnabledChains(chains: Set<HavenChain>)

    /**
     * Which Filecoin Onchain Cloud network pieces are fetched from — application-wide, not
     * per wallet, because the network is a property of the deployment being read, not of the
     * identity reading it.
     *
     * `null` (the default) means automatic: per-record gate chain when present, otherwise
     * inferred from the Arkiv endpoint (testnet means calibration, anything else mainnet).
     * A non-null value forces that network for every record.
     *
     * Unlike the cache preferences this is stored under a plain (unscoped) DataStore key —
     * it is not per identity, and [clearFor] deliberately leaves it alone.
     */
    val focNetwork: Flow<FocChain?>

    suspend fun setFocNetwork(network: FocChain?)

    /** Deletes every preference stored under [walletAddress]'s identity namespace. */
    suspend fun clearFor(walletAddress: String)
}
