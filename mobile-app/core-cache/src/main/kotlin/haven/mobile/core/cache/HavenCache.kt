package haven.mobile.core.cache

import cloud.filecoin.foc.cache.PieceRef
import kotlinx.coroutines.flow.Flow

interface HavenCache {
    suspend fun get(ref: PieceRef): Result<ByteArray>
    fun stream(ref: PieceRef): Flow<ByteArray>
    suspend fun exists(pieceCid: String): Boolean
    suspend fun fetch(ref: PieceRef): Result<Unit>
    suspend fun remove(pieceCid: String)
    suspend fun space(): CacheSpace
    suspend fun clearFor(walletAddress: String)
    suspend fun clearExpiredFor(walletAddress: String)
}