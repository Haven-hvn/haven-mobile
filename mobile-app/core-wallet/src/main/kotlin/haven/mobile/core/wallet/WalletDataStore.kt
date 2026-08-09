package haven.mobile.core.wallet

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletDataStore @Inject constructor(
    private val context: Context
) {
    private var cachedAddress: String? = null
    private var cachedConnector: String? = null

    suspend fun saveAddress(address: String) { cachedAddress = address }
    suspend fun saveLastConnector(connector: String) { cachedConnector = connector }
    suspend fun clearAddress() { cachedAddress = null }
    suspend fun clearLastConnector() { cachedConnector = null }
    suspend fun clearAll() { cachedAddress = null; cachedConnector = null }

    fun getAddress(): String? = cachedAddress
    fun getLastConnector(): String? = cachedConnector
}
