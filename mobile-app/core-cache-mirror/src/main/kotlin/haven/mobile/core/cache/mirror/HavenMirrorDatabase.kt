package haven.mobile.core.cache.mirror

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Per-wallet metadata mirror.
 *
 * **v2** adds `durationSeconds` and `creatorHandle` — both fields `haven-dapp` reads off an entity and
 * this model was dropping.
 *
 * There are no migrations, by design: every row here is derived from Arkiv and can be re-fetched, so
 * a schema change drops the tables and the next refresh repopulates them. Writing migrations for a
 * cache would be maintenance with no payoff — and worse, a botched one fails on a user's device where
 * a rebuild would have just worked. Anything that genuinely must survive lives in DataStore or in
 * the content cache, not here.
 */
@Database(entities = [MediaMirrorEntity::class], version = 2, exportSchema = false)
abstract class HavenMirrorDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao

    companion object {
        fun databaseName(walletAddress: String): String = "haven-mirror-$walletAddress.db"
    }
}
