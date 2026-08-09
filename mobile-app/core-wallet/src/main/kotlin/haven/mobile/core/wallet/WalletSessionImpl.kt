package haven.mobile.core.wallet

import android.content.Context
import com.reown.appkit.AppKit
import com.reown.appkit.models.Wallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletSessionImpl @Inject constructor(
    private val config: WalletConfig,
    private val walletDataStore: WalletDataStore
) : WalletSession {

    private val _address = MutableStateFlow<String?>(null)
    override val address: StateFlow<String?> = _address.asStateFlow()

    private var appKit: AppKit? = null

    init {
        initializeAppKit()
        restoreSession()
    }

    private fun initializeAppKit() {
        appKit = AppKit(
            projectId = config.projectId,
            metadata = com.reown.appkit.models.AppKitMetadata(
                name = config.appName,
                description = config.appDescription,
                url = config.redirectUrl,
                icons = listOf(config.appIconUrl)
            )
        )
    }

    private fun restoreSession() {
        // Restore is async via DataStore; collect once on init via coroutine.
        // Synchronous read is not available — start with null and let the
        // ViewModel observe DataStore separately if needed. This avoids
        // treating Flow<String?> as a String?.
        _address.value = null
    }

    suspend fun restoreFromStore() {
        walletDataStore.address.collect { saved ->
            if (saved != null) _address.value = saved
        }
    }

    override suspend fun connect(): Result<String> {
        return try {
            val wallet = appKit?.connect()
                ?: return Result.failure(WalletError.AppKitNotInitialized)
            val address = wallet.address
            if (address == null) {
                return Result.failure(WalletError.NoAddressReturned)
            }
            walletDataStore.saveAddress(address)
            walletDataStore.saveLastConnector(wallet.connectorName)
            _address.value = address
            Result.success(address)
        } catch (e: Exception) {
            Result.failure(WalletError.ConnectFailed(e.message ?: "Unknown error"))
        }
    }

    override suspend fun disconnect() {
        try {
            appKit?.disconnect()
        } catch (_: Exception) {
        }
        walletDataStore.clearAll()
        _address.value = null
    }

    override suspend fun signTypedDataV4(json: String): Result<String> {
        return try {
            val signature = appKit?.signTypedDataV4(json)
                ?: return Result.failure(WalletError.AppKitNotInitialized)
            // Hex-encoded 65-byte signature is "0x" + 130 hex chars = 132 chars.
            // Accept both forms leniently; strict check is done by the canister.
            if (signature.isBlank()) {
                return Result.failure(WalletError.InvalidSignatureFormat)
            }
            if (signature.length != 132 && signature.length != 130) {
                return Result.failure(WalletError.InvalidSignatureFormat)
            }
            Result.success(signature)
        } catch (e: Exception) {
            Result.failure(WalletError.SigningFailed(e.message ?: "Unknown error"))
        }
    }
}

sealed class WalletError : Exception() {
    object AppKitNotInitialized : WalletError()
    object NoAddressReturned : WalletError()
    data class ConnectFailed(val message: String) : WalletError()
    object InvalidSignatureFormat : WalletError()
    data class SigningFailed(val message: String) : WalletError()
}