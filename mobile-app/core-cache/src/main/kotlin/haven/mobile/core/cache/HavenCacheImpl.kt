package haven.mobile.core.cache

import android.content.Context
import cloud.filecoin.foc.cache.AutoQuota
import cloud.filecoin.foc.cache.Config
import cloud.filecoin.foc.cache.PieceTransform
import cloud.filecoin.foc.cache.FocCache
import cloud.filecoin.foc.cache.PieceRef
import cloud.filecoin.foc.cache.SpaceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin facade over `foc-cache`.
 *
 * Three jobs, all boring on purpose:
 *  1. namespace the cache directory per wallet (`<cacheDir>/foc/<address>/`) so a disconnect wipes
 *     exactly one wallet's content;
 *  2. apply the user's quota and TTL, rebuilding the cache when either changes;
 *  3. translate foc failures into the app's [HavenError] taxonomy.
 *
 * **Content integrity is not checked here.** An earlier revision ran a "PieceCID verifier" over every
 * returned buffer, which was both misplaced and misleading: foc already proves possession and
 * integrity through PDP against the storage providers, and the local check that shipped only inspected
 * the CID's string prefix — it would have passed any corrupt buffer whose CID merely started with
 * `baga`. Verification belongs at the layer that holds the proofs, so it stays in foc.
 */
@Singleton
class HavenCacheImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val walletSession: WalletSession,
    private val defaults: CacheConfig,
    private val settings: CacheSettingsSource,
) : HavenCache {

    /** What the live instance was built for. A change in any field means it has to be rebuilt. */
    private data class CacheKey(
        val walletAddress: String,
        val quotaBytes: Long,
        val ttlDays: Int,
        val keepUnlocked: Boolean,
    )

    private val lock = Mutex()
    private var key: CacheKey? = null
    private var focCache: FocCache? = null

    /**
     * Suspending because it reads the current preferences.
     *
     * `foc` takes its `Config` at construction, so applying a new quota or TTL means building a new
     * instance — which is why this compares a key rather than caching one instance forever. The mutex
     * keeps two concurrent reads from each building one.
     */
    private suspend fun requireFocCache(): FocCache {
        val walletAddress = walletSession.address.value
            ?: throw HavenError.WalletNotConnected("No wallet connected")

        val desired = CacheKey(
            walletAddress = walletAddress,
            quotaBytes = settings.quotaBytes.first(),
            ttlDays = settings.ttlDays.first(),
            keepUnlocked = settings.keepUnlocked.first(),
        )

        lock.withLock {
            val existing = focCache
            if (existing != null && key == desired) return existing

            // One directory per storage mode, so ciphertext and decrypted copies of a piece can never
            // share a name. Switching mode deletes the other directory: gated content re-downloads
            // on next open, and turning "keep unlocked" off leaves no decrypted file behind.
            val cacheDir = dirFor(walletAddress, desired.keepUnlocked)
            dirFor(walletAddress, !desired.keepUnlocked).deleteRecursively()
            cacheDir.mkdirs()
            val automatic = desired.quotaBytes == AUTOMATIC_QUOTA
            val created = FocCache(
                context,
                Config(
                    cacheDir = cacheDir,
                    quotaBytes = if (automatic) FALLBACK_QUOTA_BYTES else desired.quotaBytes,
                    autoQuota = if (automatic) AutoQuota() else null,
                    blockTtl = Duration.ofDays(desired.ttlDays.toLong()),
                    chunkSize = defaults.chunkSize,
                    maxParallelFetches = defaults.maxParallelFetches,
                    hedgeDelay = Duration.ofMillis(defaults.hedgeDelayMillis),
                ),
            )
            focCache = created
            key = desired
            return created
        }
    }

    override suspend fun get(ref: PieceRef): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching { requireFocCache().get(ref) }
            .recoverToHavenError("Fetch failed for ${ref.pieceCid}")
    }

    override fun stream(ref: PieceRef): Flow<ByteArray> = flow {
        requireFocCache().stream(ref).collect { chunk -> emit(chunk) }
    }.flowOn(Dispatchers.IO)

    override suspend fun file(ref: PieceRef, transform: PieceTransform?): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching { requireFocCache().file(ref, transform) }
                .recoverToHavenError("Fetch failed for ${ref.pieceCid}")
        }

    override suspend fun storesUnlocked(): Boolean = settings.keepUnlocked.first()

    override suspend fun exists(pieceCid: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { requireFocCache().exists(pieceCid) }.getOrDefault(false)
    }

    override suspend fun fetch(ref: PieceRef): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { requireFocCache().fetch(ref) }
            .map { }
            .recoverToHavenError("Prefetch failed for ${ref.pieceCid}")
    }

    override suspend fun remove(pieceCid: String) {
        withContext(Dispatchers.IO) {
            runCatching { requireFocCache().remove(pieceCid) }
        }
    }

    override suspend fun space(): CacheSpace = withContext(Dispatchers.IO) {
        val info: SpaceInfo? = runCatching { requireFocCache().space() }.getOrNull()
        CacheSpace(
            quotaBytes = info?.quotaMaxBytes ?: settings.quotaBytes.first(),
            usedBytes = info?.quotaUsedBytes ?: 0L,
            itemCount = info?.totalPieces?.toInt() ?: 0,
        )
    }

    /**
     * TTL eviction is foc's job — it holds the per-block timestamps and the quota accounting, and runs
     * eviction as part of its own read/write path. There is no selective "drop the expired ones" call
     * to make from here, and the previous implementation quietly delegated to [clearFor], so "clear
     * expired" deleted the entire cache.
     */
    override suspend fun clearExpiredFor(walletAddress: String) = Unit

    override suspend fun clearFor(walletAddress: String) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    dirFor(walletAddress, keepUnlocked = false).deleteRecursively()
                    dirFor(walletAddress, keepUnlocked = true).deleteRecursively()
                    if (key?.walletAddress == walletAddress) {
                        focCache = null
                        key = null
                    }
                }
            }
        }
    }

    override suspend fun clearUnlockedFor(walletAddress: String) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    dirFor(walletAddress, keepUnlocked = true).deleteRecursively()
                    if (key?.walletAddress == walletAddress && key?.keepUnlocked == true) {
                        focCache = null
                        key = null
                    }
                }
            }
        }
    }

    /**
     * Ciphertext stays in `cacheDir` (the OS may reclaim it; nothing is lost). Decrypted content is
     * something the user chose to keep, so it lives in `filesDir`, which the OS doesn't purge.
     * Neither is backed up or transferred (`data_extraction_rules.xml`, `allowBackup=false`).
     */
    private fun dirFor(walletAddress: String, keepUnlocked: Boolean): File =
        if (keepUnlocked) File(context.filesDir, "foc-unlocked/$walletAddress")
        else File(context.cacheDir, "foc/$walletAddress")
}

private const val FALLBACK_QUOTA_BYTES = 2L * 1024 * 1024 * 1024

/** Keep [HavenError]s as themselves; wrap anything else so callers only handle one taxonomy. */
private fun <T> Result<T>.recoverToHavenError(fallbackMessage: String): Result<T> =
    recoverCatching { throwable ->
        throw when (throwable) {
            is HavenError -> throwable
            else -> HavenError.CacheReadFailed(throwable.message ?: fallbackMessage, throwable)
        }
    }
