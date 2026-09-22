package haven.mobile.feature.library

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.hiddenItemsDataStore by preferencesDataStore(name = "haven_library_hidden")

/**
 * Ids the reader hid from the library list (top-bar Hide on the checked set).
 *
 * A DataStore set — not a Room column — so a mirror refresh (which upserts
 * every row from Arkiv) can never resurrect a hidden item, and no database
 * migration is needed. Ids for items that no longer exist linger harmlessly.
 */
@Singleton
class HiddenItemsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyHiddenIds = stringSetPreferencesKey("hidden_ids")

    val hiddenIds: Flow<Set<String>> =
        context.hiddenItemsDataStore.data.map { it[keyHiddenIds] ?: emptySet() }

    suspend fun setHidden(id: String, hidden: Boolean) {
        context.hiddenItemsDataStore.edit { prefs ->
            val current = prefs[keyHiddenIds] ?: emptySet()
            prefs[keyHiddenIds] = if (hidden) current + id else current - id
        }
    }
}
