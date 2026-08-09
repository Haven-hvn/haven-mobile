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
            _log.update { it + "Ping canister: called" }
            try {
                val havenAol = try {
                    // Resolve via Hilt if available; fallback to direct check
                    haven.mobile.core.haven.aol.HavenAol::class.java
                } catch (_: Exception) { null }
                _log.update { it + "Ping canister: havenAol=${havenAol?.simpleName ?: "unavailable"} — no canisterId configured (stub mode)" }
            } catch (e: Exception) {
                _log.update { it + "Ping canister: failed — ${e.message}" }
            }
        }
    }

    fun fetchFixturePieceRef() {
        viewModelScope.launch {
            _log.update { it + "Fetch fixture PieceRef: called" }
            try {
                val ref = cloud.filecoin.foc.cache.PieceRef(
                    pieceCid = "baga6ea4seaqfixture",
                    size = 1024,
                )
                _log.update { it + "Fetch fixture PieceRef: created ${ref.pieceCid} (${ref.size} bytes, stub cache)" }
            } catch (e: Exception) {
                _log.update { it + "Fetch fixture: failed — ${e.message}" }
            }
        }
    }

    fun signFixtureEip712() {
        viewModelScope.launch {
            _log.update { it + "Sign fixture EIP-712: called" }
            val payload = """{"types":{"EIP712Domain":[]},"primaryType":"GateRequest","domain":{"name":"Haven-AOL"},"message":{"itemId":"fixture"}}"""
            val result = walletSession.signTypedDataV4(payload)
            if (result.isSuccess) {
                _log.update { it + "Sign fixture EIP-712: success — ${result.getOrNull()?.take(20)}..." }
            } else {
                _log.update { it + "Sign fixture EIP-712: failed — ${result.exceptionOrNull()?.message}" }
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
