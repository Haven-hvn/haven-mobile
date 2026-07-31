package haven.mobile.feature.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import haven.mobile.core.cache.HavenCache
import haven.mobile.core.cache.mirror.MediaRepository
import haven.mobile.core.crypto.AesKeyCache
import haven.mobile.core.crypto.HavenCipher
import haven.mobile.core.haven.aol.HavenAol
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface WatchUiState {
    data object Loading : WatchUiState
    data class Ready(
        val item: haven.mobile.core.domain.MediaItem,
        val isDecrypting: Boolean = false,
        val decryptError: String? = null,
    ) : WatchUiState
    data class Error(val message: String) : WatchUiState
}

@HiltViewModel
class WatchViewModel @Inject constructor(
    private val havenAol: HavenAol,
    private val havenCache: HavenCache,
    private val havenCipher: HavenCipher,
    private val aesKeyCache: AesKeyCache,
    private val mediaRepository: MediaRepository,
    private val walletSession: WalletSession,
) : ViewModel() {

    private val _uiState = MutableStateFlow<WatchUiState>(WatchUiState.Loading)
    val uiState: StateFlow<WatchUiState> = _uiState.asStateFlow()

    fun loadItem(itemId: String) {
        viewModelScope.launch {
            _uiState.value = WatchUiState.Loading

            val address = walletSession.address.value
            if (address == null) {
                _uiState.value = WatchUiState.Error("No wallet connected")
                return@launch
            }

            mediaRepository.observeItem(itemId).collect { item ->
                if (item != null) {
                    _uiState.value = WatchUiState.Ready(item = item)
                }
            }
        }
    }

    fun decryptItem(item: haven.mobile.core.domain.MediaItem) {
        viewModelScope.launch {
            _uiState.update { current ->
                if (current is WatchUiState.Ready) {
                    current.copy(isDecrypting = true, decryptError = null)
                } else {
                    current
                }
            }

            val pieceCid = item.pieceRef?.pieceCid
            val cachedKey = if (pieceCid != null) aesKeyCache.get(pieceCid) else null

            val key = if (cachedKey != null) {
                cachedKey
            } else {
                when (val result = havenAol.decrypt(item, walletSession)) {
                    is Result.Success -> result.getOrNull()!!
                    is Result.Failure -> {
                        _uiState.update { current ->
                            if (current is WatchUiState.Ready) {
                                current.copy(isDecrypting = false, decryptError = result.exception.message ?: "Decryption failed")
                            } else {
                                current
                            }
                        }
                        return@launch
                    }
                }
            }

            if (pieceCid != null) {
                aesKeyCache.put(pieceCid, key)
            }

            _uiState.update { current ->
                if (current is WatchUiState.Ready) {
                    current.copy(isDecrypting = false)
                } else {
                    current
                }
            }
        }
    }
}
