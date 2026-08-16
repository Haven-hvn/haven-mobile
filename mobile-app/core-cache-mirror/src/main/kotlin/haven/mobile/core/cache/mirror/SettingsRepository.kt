package haven.mobile.core.cache.mirror

import haven.mobile.core.domain.HavenChain
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val cacheQuotaBytes: Flow<Long>
    val cacheTtlDays: Flow<Int>
    val clearOnDisconnect: Flow<Boolean>

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
    suspend fun setEnabledChains(chains: Set<HavenChain>)
}
