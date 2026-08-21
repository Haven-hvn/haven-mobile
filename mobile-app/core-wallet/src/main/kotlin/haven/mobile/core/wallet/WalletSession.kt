package haven.mobile.core.wallet

import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.flow.StateFlow

interface WalletSession {
    val address: StateFlow<String?>

    /**
     * Recent wallet-stack diagnostic events (init/restore/delegate/connect), newest last.
     * Surfaced in the onboarding UI so connection failures are visible without adb logcat.
     */
    val diagnostics: StateFlow<List<String>>

    suspend fun connect(): Result<String>
    suspend fun disconnect()
    suspend fun signTypedDataV4(json: String): Result<String>
}