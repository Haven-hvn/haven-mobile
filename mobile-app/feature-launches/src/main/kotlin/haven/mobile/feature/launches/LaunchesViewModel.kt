package haven.mobile.feature.launches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import haven.mobile.core.arkiv.ArkivClient
import haven.mobile.core.domain.LaunchStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LaunchesUiState {
    data object Loading : LaunchesUiState

    data class Ready(
        val items: List<LaunchStage>,
        val totalCount: Int,
        val query: String,
        val isRefreshing: Boolean,
        val refreshError: String? = null,
    ) : LaunchesUiState

    data class Error(val message: String) : LaunchesUiState
}

/**
 * Global launches: every live drip stage on Arkiv, whoever published it.
 *
 * Unlike the library and the feed this asks nothing of the wallet — no address, no gate
 * membership — because discovery is the point: a reader who holds nothing can still find launches
 * to pump. The query goes straight to [ArkivClient] rather than through the mirror, which only
 * holds what the wallet can open.
 */
@HiltViewModel
class LaunchesViewModel @Inject constructor(
    private val arkivClient: ArkivClient,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val refreshing = MutableStateFlow(false)
    private val refreshError = MutableStateFlow<String?>(null)
    private val fatalError = MutableStateFlow<String?>(null)
    private val stages = MutableStateFlow<List<LaunchStage>?>(null)

    val uiState: StateFlow<LaunchesUiState> =
        combine(stages, query, refreshing, refreshError, fatalError) { found, q, isRefreshing, softError, hardError ->
            when {
                hardError != null -> LaunchesUiState.Error(hardError)
                found == null -> LaunchesUiState.Loading
                else -> LaunchesUiState.Ready(
                    items = filterLaunches(found, q),
                    totalCount = found.size,
                    query = q,
                    isRefreshing = isRefreshing,
                    refreshError = softError,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LaunchesUiState.Loading)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            refreshError.value = null
            val result = arkivClient.listLaunches()
            refreshing.value = false
            result.fold(
                onSuccess = { stages.value = it },
                onFailure = { throwable ->
                    val message = throwable.message ?: "Could not load launches"
                    if (stages.value == null) fatalError.value = message
                    else refreshError.value = message
                },
            )
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun dismissRefreshError() {
        refreshError.value = null
    }

    fun retry() {
        fatalError.value = null
        refresh()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * Pure: title, token, creator handle or launch id — then newest launch first, stages in order.
 *
 * A launch's recency is its newest part's block height, so a fresh premiere surfaces even when its
 * earlier stages are old. Launches with no recorded height sort last rather than first: "unknown"
 * is not "new".
 */
internal fun filterLaunches(items: List<LaunchStage>, query: String): List<LaunchStage> {
    val needle = query.trim().lowercase()
    val filtered = items.filter { item ->
        needle.isEmpty() ||
            item.title.lowercase().contains(needle) ||
            item.gateToken.lowercase().contains(needle) ||
            item.creatorHandle?.lowercase()?.contains(needle) == true ||
            item.dripId.lowercase().contains(needle)
    }
    val newestBlock = filtered.groupBy { it.dripId }
        .mapValues { (_, group) -> group.maxOfOrNull { it.createdAtBlock ?: -1 } ?: -1 }
    return filtered.sortedWith(
        compareByDescending<LaunchStage> { newestBlock[it.dripId] }
            .thenBy { it.dripId }
            .thenBy { it.dripIndex },
    )
}
