package haven.mobile.feature.identity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import haven.mobile.core.security.SecurityCleanup
import haven.mobile.core.wallet.WalletIdentity
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IdentityUiState(
    val identities: List<WalletIdentity> = emptyList(),
    val active: WalletIdentity? = null,
    val isLive: Boolean = false,
    val working: Boolean = false,
    val message: String? = null,
)

/**
 * Identity switching. Switching is instant and clears nothing — it only
 * re-points the active partition (see planning/IDENTITY_SWITCHING_PLAN.md).
 * Removal is the one destructive action and goes through [SecurityCleanup],
 * which wipes only that identity.
 */
@HiltViewModel
class IdentityViewModel @Inject constructor(
    private val walletSession: WalletSession,
    private val securityCleanup: SecurityCleanup,
) : ViewModel() {

    private val working = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<IdentityUiState> =
        combine(
            walletSession.identities,
            walletSession.activeIdentity,
            walletSession.isLive,
            working,
            message,
        ) { identities, active, live, isWorking, msg ->
            IdentityUiState(identities, active, live, isWorking, msg)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), IdentityUiState())

    /** Instant, no wipe. The previous identity's data stays for switch-back. */
    fun switchTo(address: String) {
        viewModelScope.launch {
            walletSession.switchTo(address)
                .onFailure { message.value = it.message ?: "Couldn't switch identity" }
        }
    }

    fun addIdentity() {
        viewModelScope.launch {
            working.value = true
            val result = walletSession.connectNewIdentity()
            working.value = false
            result.onFailure { message.value = it.message ?: "Couldn't connect that wallet" }
        }
    }

    /** Re-establishes the live WalletConnect session for the active identity. */
    fun reconnect() {
        viewModelScope.launch {
            working.value = true
            val result = walletSession.reconnect()
            working.value = false
            result.onFailure { message.value = it.message ?: "Couldn't reconnect" }
        }
    }

    fun rename(address: String, label: String) {
        viewModelScope.launch {
            walletSession.renameIdentity(address, label)
                .onFailure { message.value = it.message ?: "Couldn't rename identity" }
        }
    }

    /** Wipes only this identity (keys, cache, mirror, settings), then forgets it. */
    fun removeIdentity(address: String) {
        viewModelScope.launch {
            working.value = true
            val report = runCatching { securityCleanup.removeIdentity(address) }.getOrNull()
            working.value = false
            message.value = when {
                report == null -> "Couldn't remove that identity"
                report.overallOk -> "Identity removed"
                else -> "Identity removed, but some of its local data could not be deleted"
            }
        }
    }

    fun consumeMessage() {
        message.value = null
    }
}
