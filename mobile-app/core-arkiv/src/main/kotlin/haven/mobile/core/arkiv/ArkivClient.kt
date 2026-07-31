package haven.mobile.core.arkiv

import haven.mobile.core.domain.Community
import haven.mobile.core.domain.MediaItem
import kotlinx.coroutines.flow.Flow

interface ArkivClient {
    suspend fun listMediaForOwner(
        owner: String,
        pageSize: Int = 20,
        cursor: String? = null,
    ): Result<ArkivPage<MediaItem>>

    suspend fun discoverUserCommunities(address: String): Result<List<Community>>

    suspend fun getMedia(id: String): Result<MediaItem?>
}