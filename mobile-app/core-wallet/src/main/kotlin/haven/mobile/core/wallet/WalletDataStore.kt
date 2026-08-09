package haven.mobile.core.wallet

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.walletDataStore by preferencesDataStore(name = "haven_wallet")

@Singleton
class WalletDataStore @Inject constructor(
    private val context: Context
) {
    private val KEY_ADDRESS = stringPreferencesKey("wallet_address")
    private val KEY_CONNECTOR = stringPreferencesKey("wallet_connector")

    // In-memory mirror for synchronous getAddress() used by WalletSession restore
    @Volatile private var cachedAddress: String? = null
    @Volatile private var cachedConnector: String? = null

    suspend fun saveAddress(address: String) {
        cachedAddress = address
        context.walletDataStore.edit { it[KEY_ADDRESS] = address }
    }
    suspend fun saveLastConnector(connector: String) {
        cachedConnector = connector
        context.walletDataStore.edit { it[KEY_CONNECTOR] = connector }
    }
    suspend fun clearAddress() {
        cachedAddress = null
        context.walletDataStore.edit { it.remove(KEY_ADDRESS) }
    }
    suspend fun clearLastConnector() {
        cachedConnector = null
        context.walletDataStore.edit { it.remove(KEY_CONNECTOR) }
    }
    suspend fun clearAll() {
        cachedAddress = null
        cachedConnector = null
        context.walletDataStore.edit { it.clear() }
    }

    fun getAddress(): String? = cachedAddress
    fun getLastConnector(): String? = cachedConnector

    // Async load for startup restore — call once from WalletSession
    suspend fun loadPersistedAddress(): String? {
        val persisted = context.walletDataStore.data.map { it[KEY_ADDRESS] }.first()
        cachedAddress = persisted
        return persisted
    }
    suspend fun loadPersistedConnector(): String? {
        val persisted = context.walletDataStore.data.map { it[KEY_CONNECTOR] }.first()
        cachedConnector = persisted
        return persisted
    }
}
