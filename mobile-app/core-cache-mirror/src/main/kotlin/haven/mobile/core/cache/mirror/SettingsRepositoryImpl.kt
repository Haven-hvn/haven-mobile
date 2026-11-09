package haven.mobile.core.cache.mirror

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import haven.mobile.core.domain.HavenChain
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "haven-settings")

/** DataStore key for [base] under [walletLower]'s namespace; the plain base key when no wallet. */
internal fun scopedSettingsKey(walletLower: String?, base: String): String =
    if (walletLower.isNullOrBlank()) base else "id/$walletLower/$base"

/** Per-identity value wins, then the legacy global value, then the default. */
internal fun <T> resolveScoped(scoped: T?, legacy: T?, default: T): T = scoped ?: legacy ?: default

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val walletSession: WalletSession,
) : SettingsRepository {

    companion object {
        private const val QUOTA_BASE = "cache.quota_bytes"
        private const val TTL_BASE = "cache.ttl_days"
        private const val CLEAR_BASE = "cache.clear_on_disconnect"
        private const val CHAINS_BASE = "access.enabled_chains"
        private const val KEEP_BASE = "cache.keep_unlocked"
        private const val SEPARATOR = ","
        /** Automatic (`AUTOMATIC_QUOTA`): foc sizes the cache from free space. */
        private const val DEFAULT_QUOTA_BYTES = 0L
        private const val DEFAULT_TTL_DAYS = 30
        private const val DEFAULT_CLEAR_ON_DISCONNECT = true
    }

    private fun walletLower(): String? = walletSession.address.value?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

    private fun keyFor(base: String, walletLower: String?) = stringPreferencesKey(scopedSettingsKey(walletLower, base))
    private fun legacyKeyFor(base: String) = stringPreferencesKey(base)

    private fun <T> scopedFlow(base: String, default: T, parse: (String) -> T?): Flow<T> =
        walletSession.address.flatMapLatest { address ->
            val lower = address?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
            context.settingsDataStore.data.map { prefs ->
                resolveScoped(
                    prefs[keyFor(base, lower)]?.let(parse),
                    prefs[legacyKeyFor(base)]?.let(parse),
                    default,
                )
            }
        }

    override val cacheQuotaBytes: Flow<Long> =
        scopedFlow(QUOTA_BASE, DEFAULT_QUOTA_BYTES) { it.toLongOrNull() }

    override val cacheTtlDays: Flow<Int> =
        scopedFlow(TTL_BASE, DEFAULT_TTL_DAYS) { it.toIntOrNull() }

    override val clearOnDisconnect: Flow<Boolean> =
        scopedFlow(CLEAR_BASE, DEFAULT_CLEAR_ON_DISCONNECT) { it.toBooleanStrictOrNull() }

    override val keepUnlocked: Flow<Boolean> =
        scopedFlow(KEEP_BASE, false) { it.toBooleanStrictOrNull() }

    override val enabledChains: Flow<Set<HavenChain>> =
        walletSession.address.flatMapLatest { address ->
            val lower = address?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
            context.settingsDataStore.data.map { prefs ->
                val raw = prefs[keyFor(CHAINS_BASE, lower)] ?: prefs[legacyKeyFor(CHAINS_BASE)]
                val stored = raw
                    ?.split(SEPARATOR)
                    ?.mapNotNull { name -> HavenChain.entries.firstOrNull { it.name == name } }
                    ?.toSet()
                // Empty or unreadable falls back to the defaults: a stored value that no longer
                // parses must not leave a reader with nothing checked and no explanation.
                stored?.takeIf { it.isNotEmpty() } ?: HavenChain.mainnets.toSet()
            }
        }

    private suspend fun write(base: String, value: String) {
        val key = keyFor(base, walletLower())
        context.settingsDataStore.edit { prefs -> prefs[key] = value }
    }

    override suspend fun setEnabledChains(chains: Set<HavenChain>) {
        write(CHAINS_BASE, chains.joinToString(SEPARATOR) { it.name })
    }

    override suspend fun setCacheQuotaBytes(bytes: Long) = write(QUOTA_BASE, bytes.toString())

    override suspend fun setCacheTtlDays(days: Int) = write(TTL_BASE, days.toString())

    override suspend fun setKeepUnlocked(enabled: Boolean) = write(KEEP_BASE, enabled.toString())

    override suspend fun setClearOnDisconnect(enabled: Boolean) = write(CLEAR_BASE, enabled.toString())

    override suspend fun clearFor(walletAddress: String) {
        val prefix = "id/${walletAddress.trim().lowercase()}/"
        context.settingsDataStore.edit { prefs ->
            prefs.asMap().keys
                .filter { it.name.startsWith(prefix) }
                .forEach { prefs.remove(it) }
        }
    }
}
