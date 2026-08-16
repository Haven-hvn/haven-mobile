package haven.mobile.core.cache.mirror

import haven.mobile.core.domain.MediaItem
import kotlinx.coroutines.flow.Flow

/**
 * The mirror, as the screens see it.
 *
 * Two questions, and the difference is the whole reading model: **you join a community, and that is
 * how you read.** So the interesting set is not "what did I publish" — it is "what are the communities
 * I belong to keeping".
 */
interface MediaRepository {
    /**
     * Items published by [owner].
     *
     * Parity with `haven-dapp`'s `fetchAllVideos(ownerAddress)`. Kept because it is a real question
     * with a real answer, but it is not what a reader's library is built from: on mobile, publishing
     * happens in `haven-cli`, so a reader's own-publication list is empty and stays empty.
     */
    fun observeLibrary(owner: String): Flow<List<MediaItem>>

    suspend fun refreshLibrary(owner: String): Result<Unit>

    /**
     * Everything this wallet can open: every item under every gate it belongs to, plus anything it
     * published itself.
     *
     * Mirrors what `haven-dapp` shows after its cache merge — Arkiv's answer *plus* what is already
     * mirrored, including entities that have since expired on Arkiv (FR-CACHE-3). Nothing is filtered
     * out for not being downloaded; residency is reported per item by its cache status, exactly as the
     * dapp reports it with a badge.
     */
    fun observeAccessible(): Flow<List<MediaItem>>

    /**
     * Resolve the wallet's communities, then page each archive into the mirror.
     *
     * Communities come from two places, unioned:
     *  - what the wallet **holds** (the collections access check) — the reader's path;
     *  - what the wallet has **published under** (Arkiv's own discovery, which derives gates from your
     *    entities) — the publisher's path, and all `haven-dapp` has.
     *
     * Partial success is success: one unreachable community must not blank a library that four others
     * answered for.
     */
    suspend fun refreshAccessible(): Result<Unit>

    fun observeItem(id: String): Flow<MediaItem?>

    suspend fun refreshItem(id: String): Result<Unit>

    suspend fun clearFor(walletAddress: String)
}
