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
                val result = havenAol.decrypt(item, walletSession)
                if (result.isFailure) {
                    val msg = result.exceptionOrNull()?.message ?: "Decryption failed"
                    _uiState.update { current ->
                        if (current is WatchUiState.Ready) {
                            current.copy(isDecrypting = false, decryptError = msg)
                        } else {
                            current
                        }
                    }
                    return@launch
                }
                result.getOrNull()!!
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

    fun exportFile(item: haven.mobile.core.domain.MediaItem, uri: android.net.Uri, contentResolver: android.content.ContentResolver, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            _uiState.update { current ->
                if (current is WatchUiState.Ready) current.copy(isDecrypting = true, decryptError = null) else current
            }
            val piece = item.pieceRef
            if (piece == null) {
                _uiState.update { if (it is WatchUiState.Ready) it.copy(isDecrypting = false, decryptError = "No piece ref") else it }
                onResult(Result.failure(haven.mobile.core.domain.error.HavenError.CacheMiss("No piece ref for ${item.id}")))
                return@launch
            }
            // FR-FILE-4: block export if attestation failed (handled by caller canExport) — double-check here
            if (item.contentCacheStatus == haven.mobile.core.domain.ContentCacheStatus.EXPIRED) {
                _uiState.update { if (it is WatchUiState.Ready) it.copy(isDecrypting = false, decryptError = "Verification failed") else it }
                onResult(Result.failure(haven.mobile.core.domain.error.HavenError.AttestationFailed("Export blocked: verification failed")))
                return@launch
            }
            // 1. Get AES key (cached or via HavenAol decrypt)
            val cachedKey = aesKeyCache.get(piece.pieceCid)
            val keyResult = if (cachedKey != null) Result.success(cachedKey) else havenAol.decrypt(item, walletSession)
            if (keyResult.isFailure) {
                val msg = keyResult.exceptionOrNull()?.message ?: "Decryption failed"
                _uiState.update { if (it is WatchUiState.Ready) it.copy(isDecrypting = false, decryptError = msg) else it }
                onResult(Result.failure(keyResult.exceptionOrNull()!!))
                return@launch
            }
            val key = keyResult.getOrNull()!!
            aesKeyCache.put(piece.pieceCid, key)
            // 2. Fetch ciphertext via HavenCache (hedged, offline-first) — FR-CACHE-1
            val bytesResult = try {
                // Prefer HavenCache.get for FILE (full bytes, not stream)
                havenCache.get(piece)
            } catch (e: Exception) {
                Result.failure(haven.mobile.core.domain.error.HavenError.CacheReadFailed(e.message ?: "Fetch failed"))
            }
            if (bytesResult.isFailure) {
                _uiState.update { if (it is WatchUiState.Ready) it.copy(isDecrypting = false, decryptError = bytesResult.exceptionOrNull()?.message) else it }
                onResult(Result.failure(bytesResult.exceptionOrNull()!!))
                return@launch
            }
            val ciphertext = bytesResult.getOrNull()!!
            // 3. Decrypt in memory only — FR-UI-5 no plaintext on disk
            val plainResult = havenCipher.decrypt(key, ciphertext, null)
            if (plainResult.isFailure) {
                _uiState.update { if (it is WatchUiState.Ready) it.copy(isDecrypting = false, decryptError = plainResult.exceptionOrNull()?.message) else it }
                onResult(Result.failure(plainResult.exceptionOrNull()!!))
                return@launch
            }
            val plain = plainResult.getOrNull()!!
            // 4. Write to SAF uri via ContentResolver
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(plain)
                    out.flush()
                } ?: throw IllegalStateException("Could not open output stream for $uri")
                _uiState.update { if (it is WatchUiState.Ready) it.copy(isDecrypting = false) else it }
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                _uiState.update { if (it is WatchUiState.Ready) it.copy(isDecrypting = false, decryptError = e.message) else it }
                onResult(Result.failure(haven.mobile.core.domain.error.HavenError.PlaybackDecryptFailed(e.message ?: "Write failed")))
            }
        }
    }
}
