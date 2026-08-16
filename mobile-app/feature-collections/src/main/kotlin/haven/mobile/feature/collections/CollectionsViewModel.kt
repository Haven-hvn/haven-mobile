package haven.mobile.feature.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import haven.mobile.core.collections.Access
import haven.mobile.core.collections.CollectionAccess
import haven.mobile.core.collections.CollectionCategory
import haven.mobile.core.collections.CollectionRepository
import haven.mobile.core.cache.mirror.SettingsRepository
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CollectionsUiState {
    data object Loading : CollectionsUiState

    data class Ready(
        /** Communities this wallet is already in. Shown first — it is the answer to "what can I open". */
        val joined: List<CollectionAccess>,
        /** Everything else, grouped so a long roster is scannable. */
        val available: Map<CollectionCategory, List<CollectionAccess>>,
        val isRefreshing: Boolean,
        /** False when no verdict came back at all, so the UI can say so once instead of per row. */
        val accessKnown: Boolean,
        val isConnected: Boolean,
    ) : CollectionsUiState

    data class Error(val message: String) : CollectionsUiState
}

/**
 * Collections.
 *
 * Ordering is the whole design: what you can open now, then what you could open. A reader who has
 * just connected an empty wallet gets a browsable directory instead of a dead end, and a reader who
 * holds three of these sees those three at the top without scrolling.
 */
@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val repository: CollectionRepository,
    private val settingsRepository: SettingsRepository,
    private val walletSession: WalletSession,
) : ViewModel() {

    private val entries = MutableStateFlow<List<CollectionAccess>?>(null)
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CollectionsUiState> =
        combine(entries, refreshing, error, walletSession.address) { list, isRefreshing, failure, address ->
            when {
                failure != null -> CollectionsUiState.Error(failure)
                list == null -> CollectionsUiState.Loading
                else -> CollectionsUiState.Ready(
                    joined = list.filter { it.access == Access.GRANTED },
                    available = list
                        .filter { it.access != Access.GRANTED }
                        .groupBy { it.collection.category },
                    isRefreshing = isRefreshing,
                    // Not "is a provider configured" any more — every chain has a working default, so
                    // the honest question is whether any verdict actually came back. All-unknown means
                    // the reads failed, and saying nothing would leave a holder wondering why they are
                    // not marked as in.
                    accessKnown = address != null && list.any { it.access != Access.UNKNOWN },
                    isConnected = address != null,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CollectionsUiState.Loading)

    init {
        refresh()
        // Re-check when the wallet changes: the same roster has different answers per address.
        viewModelScope.launch {
            walletSession.address.collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            error.value = null
            // Honours the reader's opt-outs: a chain switched off in Settings is not queried here
            // either, so the two screens never disagree about what was checked.
            val chains = settingsRepository.enabledChains.first()
            val result = runCatching { repository.collections(chains) }
            refreshing.value = false
            result
                .onSuccess { entries.value = it }
                .onFailure {
                    // Only a catalog failure lands here; access failures degrade to UNKNOWN per row.
                    error.value = it.message ?: "Could not load communities"
                }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
