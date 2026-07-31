package haven.mobile.core.wallet

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class WalletDataStore(private val context: Context) {
    private val dataStore = context.dataStore

    companion object {
        val KEY_ADDRESS = stringPreferencesKey("wallet.address")
        val KEY_LAST_CONNECTOR = stringPreferencesKey("wallet.last_connector")
    }

    val address: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_ADDRESS]
    }

    val lastConnector: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_CONNECTOR]
    }

    suspend fun saveAddress(address: String) {
        dataStore.edit { prefs ->
            prefs[KEY_ADDRESS] = address
        }
    }

    suspend fun saveLastConnector(connector: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_CONNECTOR] = connector
        }
    }

    suspend fun clearAddress() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_ADDRESS)
        }
    }

    suspend fun clearLastConnector() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_LAST_CONNECTOR)
        }
    }

    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_ADDRESS)
            prefs.remove(KEY_LAST_CONNECTOR)
        }
    }
}

private val Context.dataStore by preferencesDataStore(name = "haven-settings")