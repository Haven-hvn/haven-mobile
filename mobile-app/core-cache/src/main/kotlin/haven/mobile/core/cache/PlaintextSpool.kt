package haven.mobile.core.cache

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where decrypted content lands.
 *
 * Haven originally held plaintext in memory only. That is the strongest privacy position, but it
 * caps playable content at whatever a single buffer can occupy — hopeless on a 2GB device, and it
 * made every viewer allocate the whole file twice (ciphertext + plaintext) before drawing a frame.
 *
 * So plaintext is now *staged to disk*, and the trade is made explicitly:
 *
 *  - Staged under `cacheDir/plaintext/<wallet>/`, which is app-private storage: unreadable by other
 *    apps without root, and dropped by the OS under storage pressure.
 *  - Namespaced per wallet, wiped by [clearFor] on disconnect alongside the ciphertext cache.
 *  - Trimmed to a budget, oldest-touched first, so a session of large videos cannot fill the device.
 *  - Written to `<cid>.part` and renamed on success, so an interrupted decrypt never leaves a
 *    half-file that looks complete.
 *
 * Peak memory becomes one pipeline chunk instead of one file, which is the entire point.
 */
interface PlaintextSpool {
    /** Completed staging file for this piece, or null if it has not been staged (or was trimmed). */
    suspend fun find(pieceCid: String): File?

    /**
     * Drain [chunks] to disk and return the completed file. [onBytesWritten] reports cumulative
     * progress so callers can show a determinate bar rather than an indefinite spinner.
     */
    suspend fun write(
        pieceCid: String,
        chunks: Flow<ByteArray>,
        onBytesWritten: (Long) -> Unit = {},
    ): Result<File>

    suspend fun evict(pieceCid: String)

    suspend fun clearFor(walletAddress: String)
}

@Singleton
class PlaintextSpoolImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val walletSession: WalletSession,
    private val config: CacheConfig,
) : PlaintextSpool {

    override suspend fun find(pieceCid: String): File? = withContext(Dispatchers.IO) {
        val file = fileFor(pieceCid) ?: return@withContext null
        if (!file.isFile || file.length() == 0L) return@withContext null
        // Touch so the trimmer treats a replayed item as recently used.
        file.setLastModified(System.currentTimeMillis())
        file
    }

    override suspend fun write(
        pieceCid: String,
        chunks: Flow<ByteArray>,
        onBytesWritten: (Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        val target = fileFor(pieceCid)
            ?: return@withContext Result.failure(
                HavenError.WalletNotConnected("No wallet connected"),
            )
        val partial = File(target.parentFile, "${target.name}$PARTIAL_SUFFIX")

        try {
            target.parentFile?.mkdirs()
            if (partial.exists()) partial.delete()

            var written = 0L
            partial.outputStream().buffered(BUFFER_BYTES).use { sink ->
                chunks.collect { chunk ->
                    sink.write(chunk)
                    written += chunk.size
                    onBytesWritten(written)
                }
                sink.flush()
            }

            if (written == 0L) {
                partial.delete()
                return@withContext Result.failure(HavenError.CacheMiss("Decrypt produced no bytes"))
            }

            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) {
                partial.delete()
                return@withContext Result.failure(
                    HavenError.CacheWriteFailed("Could not finalise staged content for $pieceCid"),
                )
            }

            trimToBudget(target)
            Result.success(target)
        } catch (e: Exception) {
            // Never leave a partial behind: it would be indistinguishable from a short file and
            // would fail later inside a decoder, where the cause is much harder to see.
            runCatching { partial.delete() }
            Result.failure(
                when (e) {
                    is HavenError -> e
                    else -> HavenError.CacheWriteFailed(e.message ?: "Staging failed for $pieceCid", e)
                },
            )
        }
    }

    override suspend fun evict(pieceCid: String) {
        withContext(Dispatchers.IO) {
            runCatching { fileFor(pieceCid)?.delete() }
        }
    }

    override suspend fun clearFor(walletAddress: String) {
        withContext(Dispatchers.IO) {
            runCatching { directoryFor(walletAddress).deleteRecursively() }
        }
    }

    private fun fileFor(pieceCid: String): File? {
        val wallet = walletSession.address.value ?: return null
        return File(directoryFor(wallet), "${sanitise(pieceCid)}$STAGED_SUFFIX")
    }

    private fun directoryFor(walletAddress: String): File =
        File(context.cacheDir, "plaintext/${sanitise(walletAddress)}")

    /**
     * A piece CID is base32/base58 in practice, but it arrives from a remote entity, so it is
     * treated as untrusted input: anything outside the safe set is replaced rather than allowed to
     * become a path separator.
     */
    private fun sanitise(value: String): String =
        value.map { char -> if (char.isLetterOrDigit() || char == '-' || char == '_') char else '_' }
            .joinToString("")
            .take(MAX_NAME_LENGTH)

    /**
     * Oldest-touched-first trim, skipping the file just written. Staged plaintext is a convenience
     * copy — the ciphertext cache is the durable one — so dropping it is always safe.
     */
    private fun trimToBudget(justWritten: File) {
        val wallet = walletSession.address.value ?: return
        val directory = directoryFor(wallet)
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(STAGED_SUFFIX) }
            ?: return

        var total = files.sumOf { it.length() }
        if (total <= config.plaintextBudgetBytes) return

        files.sortedBy { it.lastModified() }
            .filter { it.absolutePath != justWritten.absolutePath }
            .forEach { candidate ->
                if (total <= config.plaintextBudgetBytes) return
                val size = candidate.length()
                if (candidate.delete()) total -= size
            }
    }

    private companion object {
        const val STAGED_SUFFIX = ".bin"
        const val PARTIAL_SUFFIX = ".part"
        const val BUFFER_BYTES = 64 * 1024
        const val MAX_NAME_LENGTH = 96
    }
}
