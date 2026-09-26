package haven.mobile.core.cache

import cloud.filecoin.foc.cache.PieceRef
import cloud.filecoin.foc.cache.PieceTransform
import kotlinx.coroutines.flow.Flow
import java.io.File

interface HavenCache {
    suspend fun get(ref: PieceRef): Result<ByteArray>
    fun stream(ref: PieceRef): Flow<ByteArray>

    /**
     * The piece as a local file. On a miss it's fetched and [transform]ed once, and the output is
     * what's kept. In unlocked mode pass `decryptOnStore` for every gated piece — the store holds one
     * form per piece and doesn't record which.
     */
    suspend fun file(ref: PieceRef, transform: PieceTransform? = null): Result<File>

    /** True when the cache keeps gated pieces decrypted ("Keep unlocked content on this device"). */
    suspend fun storesUnlocked(): Boolean
    suspend fun exists(pieceCid: String): Boolean
    suspend fun fetch(ref: PieceRef): Result<Unit>
    suspend fun remove(pieceCid: String)
    suspend fun space(): CacheSpace
    suspend fun clearFor(walletAddress: String)

    /** Deletes decrypted content for [walletAddress], whatever the current mode. */
    suspend fun clearUnlockedFor(walletAddress: String)
    suspend fun clearExpiredFor(walletAddress: String)
}