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

    /**
     * Latest WalletConnect pairing URI (wc:…) awaiting wallet approval, if any. Surfaced so the
     * UI can offer a QR / copy fallback when no installed wallet picks up the deep link.
     */
    val pairingUri: StateFlow<String?>

    suspend fun connect(): Result<String>
    suspend fun disconnect()
    suspend fun signTypedDataV4(json: String, chainId: Long): Result<String>
}