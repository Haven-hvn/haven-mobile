package haven.mobile.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import haven.mobile.core.cache.mirror.MediaRepository
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which kinds the grid is showing. `ALL` is a filter, not a special case. */
enum class LibraryCategory(val label: String, val kind: MediaKind?) {
    ALL("All", null),
    VIDEO("Video", MediaKind.VIDEO),
    AUDIO("Audio", MediaKind.AUDIO),
    IMAGE("Image", MediaKind.IMAGE),
    DOCUMENT("Docs", MediaKind.DOCUMENT),
    FILE("Files", MediaKind.FILE),
}

enum class LibraryLayout { GRID, LIST }

/**
 * Everything the screen draws, in one object.
 *
 * Previously `filteredItems`, `categoryCounts` and `selectedCategory` were plain getters reading
 * `MutableStateFlow.value`, and `selectCategory` "notified" by re-emitting the same state
 * instance — which `StateFlow` drops as a duplicate. Compose therefore never recomposed and the
 * category chips did nothing. Derived values belong in the emitted state, not beside it.
 */
sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object Disconnected : LibraryUiState

    data class Ready(
        /** Already filtered by query + category + residency, sorted newest first. */
        val items: List<MediaItem>,
        /** Everything accessible, before filters — the denominator for "3 of 27". */
        val totalCount: Int,
        /** How many of those will play with no signal. */
        val offlineCount: Int,
        val counts: Map<LibraryCategory, Int>,
        val query: String,
        val category: LibraryCategory,
        val layout: LibraryLayout,
        val offlineOnly: Boolean,
        val isRefreshing: Boolean,
        /** Non-fatal: the mirror still has content, but the last refresh failed. */
        val refreshError: String? = null,
    ) : LibraryUiState

    data class Error(val message: String) : LibraryUiState
}

internal data class Filters(
    val query: String,
    val category: LibraryCategory,
    val layout: LibraryLayout,
    val offlineOnly: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val walletSession: WalletSession,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val category = MutableStateFlow(LibraryCategory.ALL)
    private val layout = MutableStateFlow(LibraryLayout.GRID)
    private val offlineOnly = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)
    private val refreshError = MutableStateFlow<String?>(null)
    private val fatalError = MutableStateFlow<String?>(null)

    /**
     * Everything this wallet can read.
     *
     * You join a community and that is how you read, so the library is the union of every archive the
     * wallet has access to — not a list of its own uploads, and not only what happens to be
     * downloaded. Parity with `haven-dapp`, which shows Arkiv's answer merged with the cache
     * (including entities that have since expired on Arkiv) and reports residency per item with a
     * badge rather than by hiding rows.
     *
     * The "Offline" filter narrows it to what plays with no signal, for the times that is the actual
     * question — a filter the reader chooses, not a rule the app imposes.
     */
    private val mirror: StateFlow<List<MediaItem>?> = walletSession.address
        .flatMapLatest { address ->
            if (address == null) {
                flowOf(null)
            } else {
                mediaRepository.observeAccessible()
                    .catch { throwable ->
                        fatalError.value = throwable.message ?: "Could not read the local library"
                        emit(emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val filters: StateFlow<Filters> =
        combine(query, category, layout, offlineOnly) { q, c, l, offline ->
            Filters(query = q, category = c, layout = l, offlineOnly = offline)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            Filters("", LibraryCategory.ALL, LibraryLayout.GRID),
        )

    val uiState: StateFlow<LibraryUiState> =
        combine(mirror, filters, refreshing, refreshError, fatalError) { items, f, isRefreshing, softError, hardError ->
            when {
                hardError != null -> LibraryUiState.Error(hardError)
                items == null -> LibraryUiState.Disconnected
                else -> LibraryUiState.Ready(
                    items = applyFilters(items, f),
                    totalCount = items.size,
                    offlineCount = items.count { it.isOnDevice() },
                    counts = countByCategory(items),
                    query = f.query,
                    category = f.category,
                    layout = f.layout,
                    offlineOnly = f.offlineOnly,
                    isRefreshing = isRefreshing,
                    refreshError = softError,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState.Loading)

    init {
        // Land on fresh data without making the user pull: the mirror renders instantly from
        // Room, then the Arkiv page walk updates it in place.
        viewModelScope.launch {
            walletSession.address.collect { address ->
                if (address != null) refresh()
            }
        }
    }

    fun refresh() {
        if (walletSession.address.value == null) return
        viewModelScope.launch {
            refreshing.value = true
            refreshError.value = null
            // Resolves the wallet's communities and pages each archive into the mirror.
            val result = mediaRepository.refreshAccessible()
            refreshing.value = false
            result.exceptionOrNull()?.let { throwable ->
                // Soft failure: whatever is already mirrored stays on screen.
                refreshError.value = throwable.message ?: "Refresh failed"
            }
        }
    }

    fun toggleOfflineOnly() {
        offlineOnly.value = !offlineOnly.value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun selectCategory(value: LibraryCategory) {
        category.value = value
    }

    fun toggleLayout() {
        layout.value = if (layout.value == LibraryLayout.GRID) LibraryLayout.LIST else LibraryLayout.GRID
    }

    fun dismissRefreshError() {
        refreshError.value = null
    }

    fun retry() {
        fatalError.value = null
        refresh()
    }

    private companion object {
        /** Keeps the Room subscription alive across a configuration change. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Pure so it can be tested without Room, a wallet, or a coroutine dispatcher. */
internal fun applyFilters(items: List<MediaItem>, filters: Filters): List<MediaItem> {
    val kind = filters.category.kind
    val needle = filters.query.trim().lowercase()
    return items.asSequence()
        .filter { kind == null || it.kind == kind }
        .filter { !filters.offlineOnly || it.isOnDevice() }
        .filter { item ->
            needle.isEmpty() ||
                item.title.lowercase().contains(needle) ||
                item.description?.lowercase()?.contains(needle) == true ||
                item.pieceRef?.pieceCid?.lowercase()?.contains(needle) == true
        }
        .sortedByDescending { it.createdAt }
        .toList()
}

internal fun countByCategory(items: List<MediaItem>): Map<LibraryCategory, Int> =
    LibraryCategory.entries.associateWith { category ->
        val kind = category.kind
        if (kind == null) items.size else items.count { it.kind == kind }
    }

/**
 * Will this play without a connection?
 *
 * Drives the "Offline" filter and its count. `PARTIAL` counts: a partly-resident piece still opens, it
 * just finishes fetching. `EXPIRED` does not — it is on disk but past its TTL, so opening it needs the
 * network, and counting it as offline-ready would be a promise the app cannot keep.
 */
internal fun MediaItem.isOnDevice(): Boolean =
    contentCacheStatus == ContentCacheStatus.CACHED || contentCacheStatus == ContentCacheStatus.PARTIAL
