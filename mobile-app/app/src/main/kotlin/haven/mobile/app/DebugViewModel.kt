package haven.mobile.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import haven.mobile.core.haven.aol.HavenAolConfig
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
    private val havenAolConfig: HavenAolConfig,
) : ViewModel() {

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    fun pingCanister() {
        viewModelScope.launch {
            _log.update { it + "Ping canister: called" }
            try {
                _log.update { it + "Ping canister: canisterId=${havenAolConfig.canisterId} host=${havenAolConfig.icHost}" }
                if (havenAolConfig.canisterId.isBlank()) {
                    _log.update { it + "Ping canister: no canister id configured (offline mode)" }
                }
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
                    providerServiceUrls = emptyList(),
                )
                _log.update { it + "Fetch fixture PieceRef: created ${ref.pieceCid} (${ref.size} bytes, offline cache)" }
            } catch (e: Exception) {
                _log.update { it + "Fetch fixture: failed — ${e.message}" }
            }
        }
    }

    fun signFixtureEip712() {
        viewModelScope.launch {
            _log.update { it + "Sign fixture EIP-712: called" }
            // Same shape GateRequestBuilder.buildV1Request emits, with fixture values —
            // primaryType must have a matching definition or wallets reject the payload.
            val payload = """
                {
                  "types": {
                    "EIP712Domain": [
                      {"name": "name", "type": "string"},
                      {"name": "version", "type": "string"},
                      {"name": "chainId", "type": "uint256"},
                      {"name": "verifyingContract", "type": "address"}
                    ],
                    "GateRequest": [
                      {"name": "itemId", "type": "string"},
                      {"name": "gate", "type": "Gate"},
                      {"name": "nonce", "type": "uint256"}
                    ],
                    "Gate": [
                      {"name": "chain", "type": "string"},
                      {"name": "tokenAddress", "type": "address"},
                      {"name": "threshold", "type": "uint256"},
                      {"name": "tokenStandard", "type": "string"}
                    ]
                  },
                  "primaryType": "GateRequest",
                  "domain": {
                    "name": "Haven-AOL",
                    "version": "1",
                    "chainId": 1,
                    "verifyingContract": "0x0000000000000000000000000000000000000000"
                  },
                  "message": {
                    "itemId": "fixture",
                    "gate": {
                      "chain": "eip155:1",
                      "tokenAddress": "0x0000000000000000000000000000000000000000",
                      "threshold": 1,
                      "tokenStandard": "ERC20"
                    },
                    "nonce": 123
                  }
                }
            """.trimIndent()
             _log.update { it + "Sign fixture EIP-712: awaiting wallet response..." }
            val result = walletSession.signTypedDataV4(payload, 1L)
            _log.update { it + "Sign fixture EIP-712: received — ${if (result.isSuccess) "ok" else "error"}" }
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
