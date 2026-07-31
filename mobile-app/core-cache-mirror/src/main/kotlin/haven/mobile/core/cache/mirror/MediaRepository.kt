package haven.mobile.core.cache.mirror

import haven.mobile.core.domain.MediaItem
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun observeLibrary(owner: String): Flow<List<MediaItem>>
    suspend fun refreshLibrary(owner: String): Result<Unit>
    fun observeItem(id: String): Flow<MediaItem?>
    suspend fun refreshItem(id: String): Result<Unit>
    suspend fun clearFor(walletAddress: String)
}