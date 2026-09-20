package haven.mobile.feature.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import haven.mobile.core.attestation.AttestationVerifier
import haven.mobile.core.cache.mirror.MediaRepository
import haven.mobile.core.design.component.AttestationState
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.haven.aol.HavenAol
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

sealed interface CommunityUiState {
    data object Loading : CommunityUiState
    data object Disconnected : CommunityUiState

    data class Ready(
        val items: List<MediaItem>,
        val totalCount: Int,
        /** Per-item verdict. Absent means "not checked yet", which is not the same as unverified. */
        val attestations: Map<String, AttestationState>,
        val query: String,
        val isRefreshing: Boolean,
        val refreshError: String? = null,
    ) : CommunityUiState

    data class Error(val message: String) : CommunityUiState
}

/**
 * One-shot batch unlock state. Null means no batch has run (or it was dismissed).
 *
 * Counts items, not signatures: each gated item still needs its own wallet
 * signature until the canister offers a batch method, and the UI says so up front.
 */
sealed interface UnlockBatch {
    data class Working(val done: Int, val total: Int) : UnlockBatch
    data class Done(val succeeded: Int, val failed: Int) : UnlockBatch
}

/**
 * The feed.
 *
 * Verification runs per item and its verdict is kept explicitly rather than being inferred from
 * "id is in the failed set". Three states have to stay distinguishable: an item with a valid
 * attestation, an item that never had one, and an item whose attestation failed to verify —
 * collapsing the last two would let a forged item pass as merely unsigned.
 *
 * Results are memoised for the session because verification is an Ed25519 check plus a Merkle
 * walk per item, and the feed re-emits on every mirror write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val attestationVerifier: AttestationVerifier,
    private val walletSession: WalletSession,
    private val havenAol: HavenAol,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val refreshing = MutableStateFlow(false)
    private val refreshError = MutableStateFlow<String?>(null)
    private val fatalError = MutableStateFlow<String?>(null)
    private val attestations = MutableStateFlow<Map<String, AttestationState>>(emptyMap())
    private val unlockBatch = MutableStateFlow<UnlockBatch?>(null)

    /** Batch unlock progress for the "Unlock all" header; null when idle or dismissed. */
    val unlockBatchState: StateFlow<UnlockBatch?> = unlockBatch

    private val mirror: StateFlow<List<MediaItem>?> = walletSession.address
        .flatMapLatest { address ->
            if (address == null) {
                flowOf(null)
            } else {
                // `haven-dapp`'s community page is "entities from all creators who gated content with
                // this token" — other people's work, verified. The Library already covers everything
                // accessible, so this narrows to items this wallet did not publish; otherwise the two
                // screens are one screen twice.
                mediaRepository.observeAccessible()
                    .map { items -> items.filter { !it.owner.equals(address, ignoreCase = true) } }
                    .catch { throwable ->
                        fatalError.value = throwable.message ?: "Could not read the local feed"
                        emit(emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Soft (refresh) and hard (mirror unreadable) failures, paired so both drive recomposition. */
    private val errors: StateFlow<Pair<String?, String?>> =
        combine(refreshError, fatalError) { soft, hard -> soft to hard }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null to null)

    val uiState: StateFlow<CommunityUiState> =
        combine(mirror, query, attestations, refreshing, errors) { items, q, verdicts, isRefreshing, errorPair ->
            val (softError, hardError) = errorPair
            when {
                hardError != null -> CommunityUiState.Error(hardError)
                items == null -> CommunityUiState.Disconnected
                else -> CommunityUiState.Ready(
                    items = filterFeed(items, q),
                    totalCount = items.size,
                    attestations = verdicts,
                    query = q,
                    isRefreshing = isRefreshing,
                    refreshError = softError,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CommunityUiState.Loading)

    init {
        viewModelScope.launch {
            walletSession.address.collect { address ->
                if (address != null) refresh()
            }
        }
        viewModelScope.launch {
            // Verify as items arrive rather than inside the state combine, so a slow canister
            // round-trip for the verification key never blocks the list from rendering.
            mirror.collect { items -> if (items != null) verify(items) }
        }
    }

    private suspend fun verify(items: List<MediaItem>) {
        val known = attestations.value
        val pending = items.filter { it.id !in known }
        if (pending.isEmpty()) return
        val verdicts = pending.associate { item ->
            val attestation = item.attestation
            val state = when {
                attestation == null -> AttestationState.UNVERIFIED
                else -> attestationVerifier.verify(attestation, item).toState()
            }
            item.id to state
        }
        attestations.value = known + verdicts
    }

    fun refresh() {
        if (walletSession.address.value == null) return
        viewModelScope.launch {
            refreshing.value = true
            refreshError.value = null
            // Resolves the wallet's communities and pages each archive into the mirror.
            val result = mediaRepository.refreshAccessible()
            refreshing.value = false
            result.exceptionOrNull()?.let { refreshError.value = it.message ?: "Refresh failed" }
        }
    }

    /**
     * Unlock every gated item currently shown, in one batch.
     *
     * One tap replaces N open-wait-back navigations; after this, tapping any unlocked
     * row opens instantly (session key cache, no re-sign). Each item still costs its
     * own wallet signature — the header says the count up front. Ungated items need
     * no key and are skipped, not failed.
     */
    fun unlockAll(visible: List<MediaItem>) {
        val targets = visible.filter { it.isEncrypted }
        if (targets.isEmpty() || unlockBatch.value is UnlockBatch.Working) return
        if (walletSession.address.value == null) return
        viewModelScope.launch {
            unlockBatch.value = UnlockBatch.Working(done = 0, total = targets.size)
            val results = havenAol.decryptAll(targets, walletSession) { done, total ->
                unlockBatch.value = UnlockBatch.Working(done = done, total = total)
            }
            unlockBatch.value = UnlockBatch.Done(
                succeeded = results.count { it.isSuccess },
                failed = results.count { !it.isSuccess },
            )
        }
    }

    fun dismissUnlockBatch() {
        if (unlockBatch.value !is UnlockBatch.Working) unlockBatch.value = null
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

private fun Result<Unit>.toState(): AttestationState =
    if (isSuccess) AttestationState.VERIFIED else AttestationState.FAILED

/** Pure: title, description or publisher address. */
internal fun filterFeed(items: List<MediaItem>, query: String): List<MediaItem> {
    val needle = query.trim().lowercase()
    return items.asSequence()
        .filter { item ->
            needle.isEmpty() ||
                item.title.lowercase().contains(needle) ||
                item.description?.lowercase()?.contains(needle) == true ||
                item.owner.lowercase().contains(needle)
        }
        .sortedByDescending { it.createdAt }
        .toList()
}
