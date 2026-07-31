package haven.mobile.core.wallet

import haven.mobile.core.wallet.WalletSession

interface WalletSession {
    val address: kotlinx.coroutines.flow.StateFlow<String?>
    suspend fun connect(): Result<String>
    suspend fun disconnect()
    suspend fun signTypedDataV4(json: String): Result<String>
}