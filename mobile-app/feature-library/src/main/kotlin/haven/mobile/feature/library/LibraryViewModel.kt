package haven.mobile.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import haven.mobile.core.cache.mirror.MediaRepository
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Ready(
        val items: List<haven.mobile.core.domain.MediaItem>,
        val searchQuery: String,
        val isGridLayout: Boolean,
        val isRefreshing: Boolean,
    ) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
    data object Empty : LibraryUiState
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val walletSession: WalletSession,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var refreshJob: kotlinx.coroutines.Job? = null

    init {
        observeWallet()
    }

    private fun observeWallet() {
        viewModelScope.launch {
            walletSession.address.collect { address ->
                if (address == null) {
                    _uiState.value = LibraryUiState.Empty
                } else {
                    observeLibrary(address)
                }
            }
        }
    }

    private fun observeLibrary(address: String) {
        viewModelScope.launch {
            mediaRepository.observeLibrary(address).collect { items ->
                val current = _uiState.value
                when (current) {
                    is LibraryUiState.Loading -> {
                        if (items.isEmpty()) {
                            _uiState.value = LibraryUiState.Empty
                        } else {
                            _uiState.value = LibraryUiState.Ready(
                                items = items,
                                searchQuery = "",
                                isGridLayout = true,
                                isRefreshing = false,
                            )
                        }
                    }
                    is LibraryUiState.Ready -> {
                        _uiState.value = current.copy(items = items, isRefreshing = false)
                    }
                    is LibraryUiState.Error -> {
                        if (items.isNotEmpty()) {
                            _uiState.value = LibraryUiState.Ready(
                                items = items,
                                searchQuery = "",
                                isGridLayout = true,
                                isRefreshing = false,
                            )
                        }
                    }
                    LibraryUiState.Empty -> {
                        if (items.isNotEmpty()) {
                            _uiState.value = LibraryUiState.Ready(
                                items = items,
                                searchQuery = "",
                                isGridLayout = true,
                                isRefreshing = false,
                            )
                        }
                    }
                }
            }
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            val address = walletSession.address.value ?: return@launch

            refreshJob?.cancel()

            _uiState.update { current ->
                when (current) {
                    is LibraryUiState.Ready -> current.copy(isRefreshing = true)
                    else -> LibraryUiState.Loading
                }
            }

            mediaRepository.refreshLibrary(address)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { current ->
            if (current is LibraryUiState.Ready) {
                current.copy(searchQuery = query)
            } else {
                current
            }
        }
    }

    fun toggleLayout() {
        _uiState.update { current ->
            if (current is LibraryUiState.Ready) {
                current.copy(isGridLayout = !current.isGridLayout)
            } else {
                current
            }
        }
    }

    val filteredItems: List<haven.mobile.core.domain.MediaItem>
        get() {
            val current = _uiState.value
            if (current !is LibraryUiState.Ready) return emptyList()
            if (current.searchQuery.isBlank()) return current.items
            val query = current.searchQuery.lowercase()
            return current.items.filter {
                it.title.lowercase().contains(query) ||
                    (it.description?.lowercase()?.contains(query) ?: false)
            }
        }
}