package haven.mobile.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import haven.mobile.core.wallet.WalletConfig
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface OnboardingUiState {
    data object Idle : OnboardingUiState
    data object Connecting : OnboardingUiState
    data class Connected(val address: String) : OnboardingUiState
    data class Error(val message: String) : OnboardingUiState
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val walletSession: WalletSession,
    private val walletConfig: WalletConfig,
) : ViewModel() {
    val isWalletConfigured: Boolean = walletConfig.projectId.isNotBlank() && !walletConfig.projectId.startsWith("dummy-")

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Idle)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        observeWallet()
    }

    private fun observeWallet() {
        viewModelScope.launch {
            walletSession.address.collect { address ->
                _uiState.update {
                    if (address != null) {
                        OnboardingUiState.Connected(address)
                    } else {
                        OnboardingUiState.Idle
                    }
                }
            }
        }
    }

    fun connect() {
        viewModelScope.launch {
            _uiState.value = OnboardingUiState.Connecting
            val result = walletSession.connect()
            if (result.isSuccess) {
                _uiState.value = OnboardingUiState.Connected(result.getOrNull()!!)
            } else {
                _uiState.value = OnboardingUiState.Error(
                    result.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            walletSession.disconnect()
            _uiState.value = OnboardingUiState.Idle
        }
    }
}