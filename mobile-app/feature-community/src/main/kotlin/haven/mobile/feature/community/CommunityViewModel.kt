package haven.mobile.feature.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import haven.mobile.core.attestation.AttestationVerifier
import haven.mobile.core.cache.mirror.MediaRepository
import haven.mobile.core.haven.aol.HavenAol
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CommunityUiState {
    data object Loading : CommunityUiState
    data class Ready(
        val items: List<haven.mobile.core.domain.MediaItem>,
        val searchQuery: String,
        val isRefreshing: Boolean,
    ) : CommunityUiState
    data class Error(val message: String) : CommunityUiState
    data object Empty : CommunityUiState
}

@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val attestationVerifier: AttestationVerifier,
    private val havenAol: HavenAol,
    private val walletSession: WalletSession,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CommunityUiState>(CommunityUiState.Loading)
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    init {
        loadCommunity()
    }

    private fun loadCommunity() {
        viewModelScope.launch {
            val address = walletSession.address.value
            if (address == null) {
                _uiState.value = CommunityUiState.Empty
                return@launch
            }

            _uiState.value = CommunityUiState.Loading

            mediaRepository.observeLibrary(address).collect { items ->
                val verifiedItems = items.map { item ->
                    val attestation = item.attestation
                    if (attestation != null) {
                        val result = attestationVerifier.verifySingle(attestation, item.id)
                        item.copy(attestation = if (result.isSuccess) attestation else null)
                    } else {
                        item
                    }
                }

                val current = _uiState.value
                when (current) {
                    CommunityUiState.Loading -> {
                        if (verifiedItems.isEmpty()) {
                            _uiState.value = CommunityUiState.Empty
                        } else {
                            _uiState.value = CommunityUiState.Ready(
                                items = verifiedItems,
                                searchQuery = "",
                                isRefreshing = false,
                            )
                        }
                    }
                    is CommunityUiState.Ready -> {
                        _uiState.value = current.copy(items = verifiedItems, isRefreshing = false)
                    }
                    is CommunityUiState.Error -> {
                        if (verifiedItems.isNotEmpty()) {
                            _uiState.value = CommunityUiState.Ready(
                                items = verifiedItems,
                                searchQuery = "",
                                isRefreshing = false,
                            )
                        }
                    }
                    CommunityUiState.Empty -> {
                        if (verifiedItems.isNotEmpty()) {
                            _uiState.value = CommunityUiState.Ready(
                                items = verifiedItems,
                                searchQuery = "",
                                isRefreshing = false,
                            )
                        }
                    }
                }
            }
        }
    }

    fun refreshCommunity() {
        viewModelScope.launch {
            val address = walletSession.address.value ?: return@launch

            _uiState.update { current ->
                if (current is CommunityUiState.Ready) {
                    current.copy(isRefreshing = true)
                } else {
                    CommunityUiState.Loading
                }
            }

            mediaRepository.refreshLibrary(address)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { current ->
            if (current is CommunityUiState.Ready) {
                current.copy(searchQuery = query)
            } else {
                current
            }
        }
    }

    val filteredItems: List<haven.mobile.core.domain.MediaItem>
        get() {
            val current = _uiState.value
            if (current !is CommunityUiState.Ready) return emptyList()
            if (current.searchQuery.isBlank()) return current.items
            val query = current.searchQuery.lowercase()
            return current.items.filter {
                it.title.lowercase().contains(query) ||
                    (it.description?.lowercase()?.contains(query) ?: false)
            }
        }
}