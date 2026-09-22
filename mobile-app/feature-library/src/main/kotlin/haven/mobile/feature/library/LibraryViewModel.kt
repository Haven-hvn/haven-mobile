package haven.mobile.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import haven.mobile.core.cache.HavenCache
import haven.mobile.core.cache.mirror.MediaRepository
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.haven.aol.HavenAol
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.CancellationException
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
        /** Selection mode is on while true; ids are the checked rows. */
        val selecting: Boolean,
        val selectedIds: Set<String>,
        /** Batch unlock/download progress; null when idle or dismissed. */
        val batch: SelectionBatch?,
        /** Hidden items in this mirror (shown only while [showHidden]). */
        val hiddenCount: Int,
        val showHidden: Boolean,
        /** Ids hidden via the long-press menu — drives Hide vs Unhide per row. */
        val hiddenIds: Set<String>,
    ) : LibraryUiState

    data class Error(val message: String) : LibraryUiState
}

internal data class Filters(
    val query: String,
    val category: LibraryCategory,
    val layout: LibraryLayout,
    val offlineOnly: Boolean = false,
    val hiddenIds: Set<String> = emptySet(),
    val showHidden: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val walletSession: WalletSession,
    private val havenAol: HavenAol,
    private val havenCache: HavenCache,
    private val hiddenItems: HiddenItemsStore,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val category = MutableStateFlow(LibraryCategory.ALL)
    private val layout = MutableStateFlow(LibraryLayout.GRID)
    private val offlineOnly = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)
    private val refreshError = MutableStateFlow<String?>(null)
    private val fatalError = MutableStateFlow<String?>(null)
    private val selecting = MutableStateFlow(false)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val batch = MutableStateFlow<SelectionBatch?>(null)
    private val showHidden = MutableStateFlow(false)

    /** Ids the reader hid via the long-press menu; persisted in DataStore. */
    private val hiddenIds: StateFlow<Set<String>> = hiddenItems.hiddenIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

    /**
     * Connected wallet, for provenance language (`My contribution` vs `The pool`).
     * See `LibraryLabels`: "mine" is an on-chain comparison, not a text handle.
     */
    val walletAddress: StateFlow<String?> = walletSession.address

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
        combine(query, category, layout, offlineOnly, showHidden, hiddenIds) { args ->
            @Suppress("UNCHECKED_CAST")
            Filters(
                query = args[0] as String,
                category = args[1] as LibraryCategory,
                layout = args[2] as LibraryLayout,
                offlineOnly = args[3] as Boolean,
                showHidden = args[4] as Boolean,
                hiddenIds = args[5] as Set<String>,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            Filters("", LibraryCategory.ALL, LibraryLayout.GRID),
        )

    val uiState: StateFlow<LibraryUiState> =
        combine(
            mirror,
            filters,
            refreshing,
            refreshError,
            fatalError,
            selecting,
            selectedIds,
            batch,
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            val items = args[0] as List<MediaItem>?
            val f = args[1] as Filters
            val isRefreshing = args[2] as Boolean
            val softError = args[3] as String?
            val hardError = args[4] as String?
            val isSelecting = args[5] as Boolean
            val checked = args[6] as Set<String>
            val batchState = args[7] as SelectionBatch?
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
                    selecting = isSelecting,
                    selectedIds = checked,
                    batch = batchState,
                    hiddenCount = items.count { it.id in f.hiddenIds },
                    showHidden = f.showHidden,
                    hiddenIds = f.hiddenIds,
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

    /** Hide from the list (top-bar action on the checked set). Persisted; refresh never resurrects it. */
    fun hideItem(id: String) {
        if (batch.value is SelectionBatch.Working) return
        selectedIds.value = selectedIds.value - id
        viewModelScope.launch { hiddenItems.setHidden(id, true) }
    }

    /** Restore a hidden item to the list. */
    fun unhideItem(id: String) {
        viewModelScope.launch { hiddenItems.setHidden(id, false) }
    }

    fun toggleShowHidden() {
        showHidden.value = !showHidden.value
    }

    fun dismissRefreshError() {
        refreshError.value = null
    }

    fun retry() {
        fatalError.value = null
        refresh()
    }

    /** Enter/exit multi-select. Exiting drops the checked set and any finished summary. */
    fun setSelecting(value: Boolean) {
        if (batch.value is SelectionBatch.Working) return
        selecting.value = value
        if (!value) {
            selectedIds.value = emptySet()
            batch.value = null
        }
    }

    fun toggleSelection(id: String) {
        if (batch.value is SelectionBatch.Working) return
        if (!selecting.value) selecting.value = true
        selectedIds.value = if (id in selectedIds.value) {
            selectedIds.value - id
        } else {
            selectedIds.value + id
        }
    }

    /**
     * Conventional long-press: enters selection mode with this item checked.
     * Never unchecks — holding a checked row keeps it checked.
     */
    fun checkItem(id: String) {
        if (batch.value is SelectionBatch.Working) return
        selecting.value = true
        if (id !in selectedIds.value) selectedIds.value = selectedIds.value + id
    }

    /** Hide every checked item; hidden rows leave the list and the checked set. */
    fun hideChecked() {
        if (batch.value is SelectionBatch.Working) return
        selectedIds.value.toList().forEach { hideItem(it) }
    }

    /** Restore every checked item to the list. */
    fun unhideChecked() {
        selectedIds.value.toList().forEach { unhideItem(it) }
    }

    fun selectAllVisible(visible: List<MediaItem>) {
        if (batch.value is SelectionBatch.Working) return
        selecting.value = true
        selectedIds.value = visible.map { it.id }.toSet()
    }

    fun dismissBatch() {
        if (batch.value !is SelectionBatch.Working) batch.value = null
    }

    /**
     * Unlock every checked gated item in one batch.
     *
     * One tap replaces N open-wait-back navigations; items sharing a gate cost one
     * signature via `decryptAll`, and afterwards every unlocked row opens instantly
     * from the session key cache. Ungated rows need no key and are not counted.
     */
    fun unlockSelected(visible: List<MediaItem>) {
        val targets = selectionTargets(visible, selectedIds.value).filter { it.isEncrypted }
        if (targets.isEmpty() || batch.value is SelectionBatch.Working) return
        if (walletSession.address.value == null) return
        viewModelScope.launch {
            batch.value = SelectionBatch.Working(BatchOp.UNLOCK, phase = "Unlocking", done = 0, total = targets.size)
            val results = try {
                havenAol.decryptAll(targets, walletSession) { done, total ->
                    batch.value = SelectionBatch.Working(BatchOp.UNLOCK, phase = "Unlocking", done = done, total = total)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // decryptAll promises per-item Results, but a batch tap must never
                // take the app down: degrade to per-item failures with a screen.
                targets.map { Result.failure<ByteArray>(HavenError.Internal("Batch unlock hit an unexpected error.")) }
            }
            batch.value = SelectionBatch.Done(
                op = BatchOp.UNLOCK,
                succeeded = results.count { it.isSuccess },
                failed = results.count { !it.isSuccess },
                failedNames = failedTitles(targets, results),
            )
        }
    }

    /**
     * Save every checked item on this device so it plays with no signal.
     *
     * Keys first (one `decryptAll`, one signature per gate — the session cache keeps
     * them), then each piece fetches into the device cache. An item whose key fails
     * is counted failed without spending bandwidth on ciphertext it could not open.
     */
    fun downloadSelected(visible: List<MediaItem>) {
        val targets = selectionTargets(visible, selectedIds.value).filter { it.pieceRef != null }
        if (targets.isEmpty() || batch.value is SelectionBatch.Working) return
        if (walletSession.address.value == null) return
        viewModelScope.launch {
            val gated = targets.filter { it.isEncrypted }
            val keyOkById: Map<String, Boolean> = if (gated.isEmpty()) {
                emptyMap()
            } else {
                batch.value = SelectionBatch.Working(
                    BatchOp.DOWNLOAD, phase = "Unlocking keys", done = 0, total = gated.size,
                )
                val keyResults = try {
                    havenAol.decryptAll(gated, walletSession) { done, total ->
                        batch.value = SelectionBatch.Working(
                            BatchOp.DOWNLOAD, phase = "Unlocking keys", done = done, total = total,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    gated.map { Result.failure<ByteArray>(HavenError.Internal("Batch unlock hit an unexpected error.")) }
                }
                gated.map { it.id }.zip(keyResults.map { it.isSuccess }).toMap()
            }
            var succeeded = 0
            var failed = 0
            val failedNames = mutableListOf<String>()
            targets.forEachIndexed { index, item ->
                val ref = item.pieceRef
                val fetched = if (ref != null && (keyOkById[item.id] ?: true)) {
                    try {
                        havenCache.fetch(ref).isSuccess
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        false
                    }
                } else {
                    false
                }
                if (fetched) {
                    succeeded++
                } else {
                    failed++
                    if (failedNames.size < 3) failedNames += item.title
                }
                batch.value = SelectionBatch.Working(
                    BatchOp.DOWNLOAD, phase = "Downloading", done = index + 1, total = targets.size,
                )
            }
            batch.value = SelectionBatch.Done(
                BatchOp.DOWNLOAD,
                succeeded = succeeded,
                failed = failed,
                failedNames = failedNames.toList(),
            )
        }
    }

    private companion object {
        /** Keeps the Room subscription alive across a configuration change. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Which batch ran: keys only, or keys plus on-device files. */
enum class BatchOp { UNLOCK, DOWNLOAD }

/** Batch unlock/download progress for the selection action bar; null when idle or dismissed. */
sealed interface SelectionBatch {
    data class Working(val op: BatchOp, val phase: String, val done: Int, val total: Int) : SelectionBatch
    /**
     * Counts plus the failed titles (capped) — "1 unlocked, 1 failed" never
     * again leaves the reader guessing which row to retry alone.
     */
    data class Done(
        val op: BatchOp,
        val succeeded: Int,
        val failed: Int,
        val failedNames: List<String> = emptyList(),
    ) : SelectionBatch
}

/**
 * Pure: titles of the failed targets, in list order, capped for one-line display.
 * `results` must align with `targets` by index (both batch paths zip them so).
 */
internal fun failedTitles(
    targets: List<MediaItem>,
    results: List<Result<ByteArray>>,
    max: Int = 3,
): List<String> =
    targets.zip(results)
        .filter { !it.second.isSuccess }
        .map { it.first.title }
        .take(max)

/** Pure: "" when nothing failed, otherwise " (A, B)" for the summary line. */
internal fun failedNamesSuffix(names: List<String>): String =
    if (names.isEmpty()) "" else " (" + names.joinToString() + ")"

/**
 * Pure: the checked rows, in list order. Ids that left the list (a refresh narrowed it)
 * select nothing rather than unlocking something the reader can no longer see.
 */
internal fun selectionTargets(items: List<MediaItem>, ids: Set<String>): List<MediaItem> =
    items.filter { it.id in ids }

/** Pure so it can be tested without Room, a wallet, or a coroutine dispatcher. */
internal fun applyFilters(items: List<MediaItem>, filters: Filters): List<MediaItem> {
    val kind = filters.category.kind
    val needle = filters.query.trim().lowercase()
    return items.asSequence()
        .filter { kind == null || it.kind == kind }
        .filter { !filters.offlineOnly || it.isOnDevice() }
        .filter { filters.showHidden || it.id !in filters.hiddenIds }
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
