package haven.mobile.core.cache

import android.content.Context
import cloud.filecoin.foc.cache.Config
import cloud.filecoin.foc.cache.FocCache
import cloud.filecoin.foc.cache.PieceRef
import cloud.filecoin.foc.cache.SpaceInfo
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HavenCacheImpl @Inject constructor(
    private val context: Context,
    private val walletSession: WalletSession,
    private val config: CacheConfig,
    private val pieceCidVerifier: PieceCidVerifier,
) : HavenCache {

    private var currentWalletAddress: String? = null
    private var focCache: FocCache? = null

    private fun getFocCache(): FocCache {
        val walletAddress = walletSession.address.value
            ?: throw HavenError.WalletNotConnected("No wallet connected")
        if (walletAddress != currentWalletAddress) {
            focCache = null
            val cacheDir = File(context.cacheDir, "foc/$walletAddress")
            cacheDir.mkdirs()
            focCache = FocCache(
                context,
                Config(
                    cacheDir = cacheDir,
                    quotaBytes = config.quotaBytes,
                    blockTtl = Duration.ofDays(config.blockTtlDays.toLong()),
                    chunkSize = config.chunkSize,
                    maxParallelFetches = config.maxParallelFetches,
                    hedgeDelay = Duration.ofMillis(config.hedgeDelayMillis),
                )
            )
            currentWalletAddress = walletAddress
        }
        return focCache!!
    }

    override suspend fun get(ref: PieceRef): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                val bytes = getFocCache().get(ref)
                val verified = pieceCidVerifier.verify(ref.pieceCid, bytes)
                if (!verified) {
                    getFocCache().remove(ref.pieceCid)
                    return@withContext Result.failure(HavenError.CachePieceVerifyFailed)
                }
                Result.success(bytes)
            } catch (e: HavenError) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(HavenError.CacheMiss(e.message ?: "Unknown error"))
            }
        }
    }

    override fun stream(ref: PieceRef): Flow<ByteArray> {
        val walletAddress = walletSession.address.value
        if (walletAddress == null) {
            return flow { throw HavenError.WalletNotConnected("No wallet connected") }
        }
        return flow {
            val cache = getFocCache()
            cache.stream(ref).collect { chunk ->
                emit(chunk)
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun exists(pieceCid: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val walletAddress = walletSession.address.value
                if (walletAddress == null) return@withContext false
                getFocCache().exists(pieceCid)
            } catch (_: Exception) {
                false
            }
        }
    }

    override suspend fun fetch(ref: PieceRef): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                getFocCache().fetch(ref)
                Result.success(Unit)
            } catch (e: HavenError) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(HavenError.CacheMiss(e.message ?: "Unknown error"))
            }
        }
    }

    override suspend fun remove(pieceCid: String) {
        withContext(Dispatchers.IO) {
            try {
                getFocCache().remove(pieceCid)
            } catch (_: Exception) {
            }
        }
    }

    override suspend fun space(): CacheSpace {
        return withContext(Dispatchers.IO) {
            try {
                val walletAddress = walletSession.address.value
                if (walletAddress == null) {
                    return@withContext CacheSpace(
                        quotaBytes = config.quotaBytes,
                        usedBytes = 0,
                        itemCount = 0,
                    )
                }
                val spaceInfo: SpaceInfo = getFocCache().space()
                CacheSpace(
                    quotaBytes = spaceInfo.quotaMaxBytes,
                    usedBytes = spaceInfo.quotaUsedBytes,
                    itemCount = spaceInfo.totalPieces.toInt(),
                )
            } catch (_: Exception) {
                CacheSpace(
                    quotaBytes = config.quotaBytes,
                    usedBytes = 0,
                    itemCount = 0,
                )
            }
        }
    }

    override suspend fun clearFor(walletAddress: String) {
        withContext(Dispatchers.IO) {
            try {
                val cacheDir = File(context.cacheDir, "foc/$walletAddress")
                cacheDir.deleteRecursively()
                if (currentWalletAddress == walletAddress) {
                    focCache = null
                    currentWalletAddress = null
                }
            } catch (_: Exception) {
            }
        }
    }
}

class NoOpPieceCidVerifier : PieceCidVerifier {
    override suspend fun verify(pieceCid: String, bytes: ByteArray): Boolean = true
}