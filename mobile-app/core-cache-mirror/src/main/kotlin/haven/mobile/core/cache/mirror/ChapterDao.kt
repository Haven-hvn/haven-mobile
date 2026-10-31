package haven.mobile.core.cache.mirror

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ChapterDao {
    @Query("SELECT * FROM cached_chapters WHERE albumId = :albumId ORDER BY chapterIndex ASC")
    suspend fun getForAlbum(albumId: String): List<CachedChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CachedChapterEntity>)

    @Query("DELETE FROM cached_chapters WHERE albumId = :albumId")
    suspend fun deleteForAlbum(albumId: String)

    @Query("DELETE FROM cached_chapters WHERE albumId IN (:ids)")
    suspend fun deleteForAlbums(ids: Collection<String>)

    @Query("DELETE FROM cached_chapters")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceForAlbum(albumId: String, items: List<CachedChapterEntity>) {
        deleteForAlbum(albumId)
        insertAll(items)
    }
}
