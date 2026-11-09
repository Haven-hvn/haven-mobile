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

/** Persisted identity registry: the known wallets plus which one is active. */
data class IdentityState(
    val identities: List<WalletIdentity> = emptyList(),
    val activeAddress: String? = null,
)

@Singleton
class WalletDataStore @Inject constructor(
    private val context: Context
) {
    private val KEY_ADDRESS = stringPreferencesKey("wallet_address")
    private val KEY_CONNECTOR = stringPreferencesKey("wallet_connector")
    private val KEY_IDENTITIES = stringPreferencesKey("identities_v1")
    private val KEY_ACTIVE = stringPreferencesKey("active_address")

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

    /**
     * Loads the registry, migrating the legacy single-wallet record on first
     * read: a pre-multi-identity install becomes a one-identity registry with
     * that wallet active, and nothing is deleted.
     */
    suspend fun loadIdentityState(nowMs: Long = System.currentTimeMillis()): IdentityState {
        val prefs = context.walletDataStore.data.first()
        val decoded = IdentitiesCodec.decode(prefs[KEY_IDENTITIES])
        if (decoded.isNotEmpty()) {
            val active = prefs[KEY_ACTIVE]?.takeIf { a -> decoded.any { IdentityAddress.same(it.address, a) } }
                ?: decoded.maxByOrNull { it.lastActiveAtMs }?.address
            cachedAddress = active
            return IdentityState(decoded, active)
        }
        val legacy = prefs[KEY_ADDRESS]
        val migrated = IdentityRegistry.migrateLegacy(legacy, prefs[KEY_CONNECTOR] ?: "", nowMs)
        if (migrated.isNotEmpty()) {
            saveIdentityState(IdentityState(migrated, migrated.first().address))
            cachedAddress = migrated.first().address
            return IdentityState(migrated, migrated.first().address)
        }
        return IdentityState()
    }

    suspend fun saveIdentityState(state: IdentityState) {
        cachedAddress = state.activeAddress
        context.walletDataStore.edit { prefs ->
            prefs[KEY_IDENTITIES] = IdentitiesCodec.encode(state.identities)
            if (state.activeAddress != null) prefs[KEY_ACTIVE] = state.activeAddress else prefs.remove(KEY_ACTIVE)
            // Keep the legacy mirror in sync for any reader that still samples it.
            if (state.activeAddress != null) prefs[KEY_ADDRESS] = state.activeAddress else prefs.remove(KEY_ADDRESS)
        }
    }
}
