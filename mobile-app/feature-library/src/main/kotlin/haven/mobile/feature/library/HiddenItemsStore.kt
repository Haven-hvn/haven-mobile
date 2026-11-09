package haven.mobile.feature.library

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.hiddenItemsDataStore by preferencesDataStore(name = "haven_library_hidden")

/**
 * Ids the reader hid from the library list (top-bar Hide on the checked set).
 *
 * Scoped per identity (`hidden_ids/<walletLower>`), with the legacy global
 * set as fallback for the first identity after upgrade: hiding an item under
 * one wallet must not hide it under another. Ids for items that no longer
 * exist (or for a removed identity's namespace) linger harmlessly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class HiddenItemsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val walletSession: WalletSession,
) {
    private val legacyKey = stringSetPreferencesKey("hidden_ids")

    private fun scopedKey(walletLower: String?) =
        stringSetPreferencesKey(if (walletLower.isNullOrBlank()) "hidden_ids" else "hidden_ids/$walletLower")

    private fun walletLower(): String? =
        walletSession.address.value?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

    val hiddenIds: Flow<Set<String>> =
        walletSession.address.flatMapLatest { address ->
            val lower = address?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
            context.hiddenItemsDataStore.data.map { prefs ->
                prefs[scopedKey(lower)] ?: prefs[legacyKey] ?: emptySet()
            }
        }

    suspend fun setHidden(id: String, hidden: Boolean) {
        val key = scopedKey(walletLower())
        context.hiddenItemsDataStore.edit { prefs ->
            val current = prefs[key] ?: prefs[legacyKey] ?: emptySet()
            prefs[key] = if (hidden) current + id else current - id
        }
    }
}
