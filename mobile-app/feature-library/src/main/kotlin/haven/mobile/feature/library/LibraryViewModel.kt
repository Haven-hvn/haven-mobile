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

    enum class LibraryCategory(val label: String) {
        ALL("All"),
        VIDEO("Video"),
        AUDIO("Audio"),
        DOCUMENT("Document"),
        IMAGE("Image"),
        FILE("File"),
    }

    private val _selectedCategory = MutableStateFlow(LibraryCategory.ALL)
    val selectedCategory: LibraryCategory get() = _selectedCategory.value

    fun selectCategory(category: LibraryCategory) {
        _selectedCategory.value = category
        _uiState.update { it } // trigger recomposition via state flow read in composable
    }

    val categoryCounts: Map<LibraryCategory, Int>
        get() {
            val items = (_uiState.value as? LibraryUiState.Ready)?.items ?: emptyList()
            return mapOf(
                LibraryCategory.ALL to items.size,
                LibraryCategory.VIDEO to items.count { it.kind == haven.mobile.core.domain.MediaKind.VIDEO },
                LibraryCategory.AUDIO to items.count { it.kind == haven.mobile.core.domain.MediaKind.AUDIO },
                LibraryCategory.DOCUMENT to items.count { it.kind == haven.mobile.core.domain.MediaKind.DOCUMENT },
                LibraryCategory.IMAGE to items.count { it.kind == haven.mobile.core.domain.MediaKind.IMAGE },
                LibraryCategory.FILE to items.count { it.kind == haven.mobile.core.domain.MediaKind.FILE },
            )
        }

    val filteredItems: List<haven.mobile.core.domain.MediaItem>
        get() {
            val current = _uiState.value
            if (current !is LibraryUiState.Ready) return emptyList()
            val bySearch = if (current.searchQuery.isBlank()) {
                current.items
            } else {
                val query = current.searchQuery.lowercase()
                current.items.filter {
                    it.title.lowercase().contains(query) ||
                        (it.description?.lowercase()?.contains(query) ?: false)
                }
            }
            val cat = _selectedCategory.value
            if (cat == LibraryCategory.ALL) return bySearch
            return bySearch.filter {
                when (cat) {
                    LibraryCategory.VIDEO -> it.kind == haven.mobile.core.domain.MediaKind.VIDEO
                    LibraryCategory.AUDIO -> it.kind == haven.mobile.core.domain.MediaKind.AUDIO
                    LibraryCategory.DOCUMENT -> it.kind == haven.mobile.core.domain.MediaKind.DOCUMENT
                    LibraryCategory.IMAGE -> it.kind == haven.mobile.core.domain.MediaKind.IMAGE
                    LibraryCategory.FILE -> it.kind == haven.mobile.core.domain.MediaKind.FILE
                    LibraryCategory.ALL -> true
                }
            }
        }
}