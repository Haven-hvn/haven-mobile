package haven.mobile.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import haven.mobile.core.cache.HavenCache
import haven.mobile.core.cache.mirror.SettingsRepository
import haven.mobile.core.security.SecurityCleanup
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Ready(
        val cacheQuotaBytes: Long,
        val cacheTtlDays: Int,
        val clearOnDisconnect: Boolean,
        val walletAddress: String?,
        val cacheSpaceBytes: Long? = null,
    ) : SettingsUiState
    data class Error(val message: String) : SettingsUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val havenCache: HavenCache,
    private val securityCleanup: SecurityCleanup,
    private val walletSession: WalletSession,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val quota = kotlinx.coroutines.flow.first(settingsRepository.cacheQuotaBytes)
                val ttl = kotlinx.coroutines.flow.first(settingsRepository.cacheTtlDays)
                val clearOnDisconnect = kotlinx.coroutines.flow.first(settingsRepository.clearOnDisconnect)
                val address = walletSession.address.value
                val space = havenCache.space().let { it.usedBytes }

                _uiState.value = SettingsUiState.Ready(
                    cacheQuotaBytes = quota,
                    cacheTtlDays = ttl,
                    clearOnDisconnect = clearOnDisconnect,
                    walletAddress = address,
                    cacheSpaceBytes = space,
                )
            } catch (e: Exception) {
                _uiState.value = SettingsUiState.Error(
                    e.message ?: "Failed to load settings"
                )
            }
        }
    }

    fun setCacheQuotaBytes(bytes: Long) {
        viewModelScope.launch {
            settingsRepository.setCacheQuotaBytes(bytes)
            loadSettings()
        }
    }

    fun setCacheTtlDays(days: Int) {
        viewModelScope.launch {
            settingsRepository.setCacheTtlDays(days)
            loadSettings()
        }
    }

    fun setClearOnDisconnect(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setClearOnDisconnect(enabled)
            loadSettings()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            val address = walletSession.address.value
            if (address != null) {
                havenCache.clearFor(address)
            }
            loadSettings()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            val address = walletSession.address.value
            if (address != null) {
                securityCleanup.runDisconnect(address)
            }
            walletSession.disconnect()
            _uiState.value = SettingsUiState.Loading
        }
    }
}