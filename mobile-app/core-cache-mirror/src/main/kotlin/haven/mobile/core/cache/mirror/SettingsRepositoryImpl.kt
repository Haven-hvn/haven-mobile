package haven.mobile.core.cache.mirror

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import haven.mobile.core.domain.HavenChain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "haven-settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) : SettingsRepository {

    companion object {
        private val QUOTA_BYTES_KEY = stringPreferencesKey("cache.quota_bytes")
        private val TTL_DAYS_KEY = stringPreferencesKey("cache.ttl_days")
        private val CLEAR_ON_DISCONNECT_KEY = stringPreferencesKey("cache.clear_on_disconnect")
        private val ENABLED_CHAINS_KEY = stringPreferencesKey("access.enabled_chains")
        private const val SEPARATOR = ","
        private val DEFAULT_QUOTA_BYTES = 2L * 1024 * 1024 * 1024
        private val DEFAULT_TTL_DAYS = 30
        private val DEFAULT_CLEAR_ON_DISCONNECT = true
    }

    override val cacheQuotaBytes: Flow<Long> = context.settingsDataStore.data.map { prefs ->
        prefs[QUOTA_BYTES_KEY]?.toLongOrNull() ?: DEFAULT_QUOTA_BYTES
    }

    override val cacheTtlDays: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[TTL_DAYS_KEY]?.toIntOrNull() ?: DEFAULT_TTL_DAYS
    }

    override val clearOnDisconnect: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[CLEAR_ON_DISCONNECT_KEY]?.toBoolean() ?: DEFAULT_CLEAR_ON_DISCONNECT
    }

    override val enabledChains: Flow<Set<HavenChain>> = context.settingsDataStore.data.map { prefs ->
        val stored = prefs[ENABLED_CHAINS_KEY]
            ?.split(SEPARATOR)
            ?.mapNotNull { name -> HavenChain.entries.firstOrNull { it.name == name } }
            ?.toSet()
        // Empty or unreadable falls back to the defaults: a stored value that no longer parses (a chain
        // removed from the enum, say) must not leave a reader with nothing checked and no explanation.
        stored?.takeIf { it.isNotEmpty() } ?: HavenChain.mainnets.toSet()
    }

    override suspend fun setEnabledChains(chains: Set<HavenChain>) {
        context.settingsDataStore.edit { prefs ->
            prefs[ENABLED_CHAINS_KEY] = chains.joinToString(SEPARATOR) { it.name }
        }
    }

    override suspend fun setCacheQuotaBytes(bytes: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[QUOTA_BYTES_KEY] = bytes.toString()
        }
    }

    override suspend fun setCacheTtlDays(days: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[TTL_DAYS_KEY] = days.toString()
        }
    }

    override suspend fun setClearOnDisconnect(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[CLEAR_ON_DISCONNECT_KEY] = enabled.toString()
        }
    }
}