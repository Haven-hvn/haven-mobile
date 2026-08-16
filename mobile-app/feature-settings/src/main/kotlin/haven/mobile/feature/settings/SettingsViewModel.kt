package haven.mobile.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import haven.mobile.core.cache.HavenCache
import haven.mobile.core.cache.mirror.SettingsRepository
import haven.mobile.core.domain.HavenChain
import haven.mobile.core.security.SecurityCleanup
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CacheUsage(
    val usedBytes: Long,
    val quotaBytes: Long,
    val itemCount: Int,
)

data class SettingsUiState(
    val walletAddress: String?,
    val quotaBytes: Long,
    val ttlDays: Int,
    val clearOnDisconnect: Boolean,
    /** Which of Haven-AOL's chains are checked for access. */
    val enabledChains: Set<HavenChain>,
    val usage: CacheUsage?,
    val recentEvents: List<String>,
    val isWorking: Boolean = false,
    val message: String? = null,
)

/**
 * Settings.
 *
 * Preferences are read as flows rather than sampled once with `first()`, so a value written here
 * (or by the eviction job) is reflected without a manual reload — the previous implementation
 * called `loadSettings()` after every setter, which meant three DataStore reads and a `space()`
 * disk walk per slider frame.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val havenCache: HavenCache,
    private val securityCleanup: SecurityCleanup,
    private val walletSession: WalletSession,
) : ViewModel() {

    private val usage = MutableStateFlow<CacheUsage?>(null)
    private val working = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    /**
     * FR-OBS-2: last 100 events, in memory only. Deliberately not persisted — the log can name
     * gates and CIDs, and none of that should outlive the process, let alone a disconnect.
     */
    private val events = MutableStateFlow<List<String>>(emptyList())

    private val preferences = combine(
        settingsRepository.cacheQuotaBytes,
        settingsRepository.cacheTtlDays,
        settingsRepository.clearOnDisconnect,
        settingsRepository.enabledChains,
    ) { quota, ttl, clearOnDisconnect, chains ->
        Preferences(quota, ttl, clearOnDisconnect, chains)
    }

    private val transient = combine(usage, working, message, events) { u, isWorking, msg, log ->
        TransientState(u, isWorking, msg, log)
    }

    val uiState: StateFlow<SettingsUiState> =
        combine(preferences, walletSession.address, transient) { prefs, address, t ->
            SettingsUiState(
                walletAddress = address,
                quotaBytes = prefs.quotaBytes,
                ttlDays = prefs.ttlDays,
                clearOnDisconnect = prefs.clearOnDisconnect,
                enabledChains = prefs.chains,
                usage = t.usage,
                recentEvents = t.events,
                isWorking = t.working,
                message = t.message,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            SettingsUiState(
                walletAddress = null,
                quotaBytes = DEFAULT_QUOTA_BYTES,
                ttlDays = DEFAULT_TTL_DAYS,
                clearOnDisconnect = true,
                enabledChains = HavenChain.mainnets.toSet(),
                usage = null,
                recentEvents = emptyList(),
            ),
        )

    init {
        refreshUsage()
    }

    fun refreshUsage() {
        viewModelScope.launch {
            val space = runCatching { havenCache.space() }.getOrNull() ?: return@launch
            usage.value = CacheUsage(
                usedBytes = space.usedBytes,
                quotaBytes = space.quotaBytes,
                itemCount = space.itemCount,
            )
        }
    }

    fun setQuotaBytes(bytes: Long) {
        viewModelScope.launch {
            settingsRepository.setCacheQuotaBytes(bytes)
            record("cache.quota_bytes = $bytes")
        }
    }

    fun setTtlDays(days: Int) {
        viewModelScope.launch {
            settingsRepository.setCacheTtlDays(days)
            record("cache.ttl_days = $days")
        }
    }

    fun setClearOnDisconnect(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setClearOnDisconnect(enabled)
            record("cache.clear_on_disconnect = $enabled")
        }
    }

    /**
     * Turn a chain on or off for access checks.
     *
     * Refuses to leave the set empty: with nothing checked, every gate reads as unavailable and the
     * library empties out with no explanation the reader could act on. Turning the last one off is
     * almost certainly a mis-tap.
     */
    fun toggleChain(chain: HavenChain) {
        viewModelScope.launch {
            val current = uiState.value.enabledChains
            val next = if (chain in current) current - chain else current + chain
            if (next.isEmpty()) {
                message.value = "At least one network has to stay on"
                return@launch
            }
            settingsRepository.setEnabledChains(next)
            record("access.enabled_chains = ${next.joinToString(",") { it.aolVariant }}")
        }
    }

    private data class Preferences(
        val quotaBytes: Long,
        val ttlDays: Int,
        val clearOnDisconnect: Boolean,
        val chains: Set<HavenChain>,
    )

    fun clearCache() {
        val address = walletSession.address.value ?: return
        viewModelScope.launch {
            working.value = true
            runCatching { havenCache.clearFor(address) }
                .onFailure { record("CLEAR_CACHE_FAILED ${it.message}") }
            working.value = false
            refreshUsage()
            message.value = "Cached content cleared"
            record("cache cleared for wallet")
        }
    }

    /**
     * Disconnect runs the security cleanup fan-out first and reports which steps failed. A
     * partially failed wipe is exactly the case a user must be told about, so the per-step
     * outcome is written to the event log instead of being swallowed.
     */
    fun disconnect() {
        val address = walletSession.address.value
        viewModelScope.launch {
            working.value = true
            if (address != null) {
                val report = runCatching { securityCleanup.runDisconnect(address) }.getOrNull()
                if (report == null) {
                    record("DISCONNECT_CLEANUP_THREW")
                } else {
                    report.steps.filter { !it.ok }.forEach { step ->
                        record("DISCONNECT_STEP_FAILED ${step.name} ${step.errorCode ?: ""}")
                    }
                    message.value = if (report.overallOk) {
                        "Disconnected and wiped local data"
                    } else {
                        "Disconnected, but some local data could not be removed"
                    }
                }
            }
            walletSession.disconnect()
            working.value = false
            usage.value = null
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    fun clearEvents() {
        events.value = emptyList()
    }

    private fun record(entry: String) {
        events.value = (listOf(entry) + events.value).take(MAX_EVENTS)
    }

    private data class TransientState(
        val usage: CacheUsage?,
        val working: Boolean,
        val message: String?,
        val events: List<String>,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MAX_EVENTS = 100
        const val DEFAULT_QUOTA_BYTES = 2L * 1024 * 1024 * 1024
        const val DEFAULT_TTL_DAYS = 30
    }
}
