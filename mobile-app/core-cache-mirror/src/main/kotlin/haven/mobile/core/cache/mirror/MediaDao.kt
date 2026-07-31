package haven.mobile.core.cache.mirror

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items WHERE owner = :owner ORDER BY lastAccessedAt DESC")
    fun observeLibrary(owner: String): Flow<List<MediaMirrorEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    fun observeItem(id: String): Flow<MediaMirrorEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaMirrorEntity>)

    @Query("DELETE FROM media_items WHERE owner = :owner")
    suspend fun deleteForOwner(owner: String)

    @Query("UPDATE media_items SET contentCacheStatus = :status WHERE id = :id")
    suspend fun updateContentCacheStatus(id: String, status: String)
}