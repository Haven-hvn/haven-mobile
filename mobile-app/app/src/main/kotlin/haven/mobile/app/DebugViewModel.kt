package haven.mobile.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DebugLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
)

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val walletSession: WalletSession,
) : ViewModel() {

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    fun pingCanister() {
        viewModelScope.launch {
            _log.update { current ->
                current + "Ping canister: called"
            }
            // TODO: Implement actual canister ping
            _log.update { current ->
                current + "Ping canister: not yet implemented"
            }
        }
    }

    fun fetchFixturePieceRef() {
        viewModelScope.launch {
            _log.update { current ->
                current + "Fetch fixture PieceRef: called"
            }
            // TODO: Implement actual fixture PieceRef fetch
            _log.update { current ->
                current + "Fetch fixture PieceRef: not yet implemented"
            }
        }
    }

    fun signFixtureEip712() {
        viewModelScope.launch {
            _log.update { current ->
                current + "Sign fixture EIP-712: called"
            }
            // TODO: Implement actual EIP-712 signing
            _log.update { current ->
                current + "Sign fixture EIP-712: not yet implemented"
            }
        }
    }

    fun disconnectWallet() {
        viewModelScope.launch {
            _log.update { current ->
                current + "Disconnect wallet: called"
            }
            walletSession.disconnect()
            _log.update { current ->
                current + "Disconnect wallet: done"
            }
        }
    }
}
