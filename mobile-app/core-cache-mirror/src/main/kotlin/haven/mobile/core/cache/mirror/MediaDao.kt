package haven.mobile.core.cache.mirror

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    /**
     * Items published by [owner] — the Library's query.
     *
     * `lastAccessedAt` is null until something is opened, and SQLite sorts nulls first on DESC, so
     * `createdAt` breaks the tie. Without it a fresh library appeared in arbitrary order.
     */
    @Query(
        "SELECT * FROM media_items WHERE owner = :owner " +
            "ORDER BY lastAccessedAt DESC, createdAt DESC",
    )
    fun observeLibrary(owner: String): Flow<List<MediaMirrorEntity>>

    /**
     * Everything mirrored for the connected wallet, newest first — the accessible set.
     *
     * Safe to read unscoped because the database file is itself per wallet
     * (`haven-mirror-<address>.db`), so "everything here" already means "everything this wallet was
     * shown". Includes entities that have since expired on Arkiv, which is deliberate (FR-CACHE-3):
     * the dapp keeps showing them, cached, rather than making them vanish.
     */
    @Query("SELECT * FROM media_items ORDER BY createdAt DESC")
    fun observeAccessible(): Flow<List<MediaMirrorEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    fun observeItem(id: String): Flow<MediaMirrorEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaMirrorEntity>)

    @Query("DELETE FROM media_items WHERE owner = :owner")
    suspend fun deleteForOwner(owner: String)

    @Query("DELETE FROM media_items")
    suspend fun deleteAll()

    @Query("UPDATE media_items SET contentCacheStatus = :status WHERE id = :id")
    suspend fun updateContentCacheStatus(id: String, status: String)
}
