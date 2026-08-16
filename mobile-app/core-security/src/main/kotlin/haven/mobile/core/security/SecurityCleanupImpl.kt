package haven.mobile.core.security

import android.content.Context
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import haven.mobile.core.cache.HavenCache
import haven.mobile.core.cache.PlaintextSpool
import haven.mobile.core.cache.mirror.MediaRepository
import haven.mobile.core.cache.mirror.SettingsRepository
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.haven.aol.HavenAol
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Disconnect cleanup (FR-SEC-1/2).
 *
 * Order matters and is deliberate:
 *
 *  1. cancel queued work first, so nothing re-populates what is about to be deleted;
 *  2. wipe in-memory key material, so anything still running cannot decrypt further;
 *  3. **staged plaintext**, because it is the most sensitive thing on disk;
 *  4. ciphertext cache;
 *  5. the Room mirror;
 *  6. end the wallet session last, since the earlier steps need the address to know what to delete.
 *
 * Every step runs even if an earlier one failed, and each result is reported: a partial wipe is
 * precisely the case a user must be told about (FR-SEC-2).
 */
@Singleton
class SecurityCleanupImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val walletSession: WalletSession,
    private val havenAol: HavenAol,
    private val havenCache: HavenCache,
    private val plaintextSpool: PlaintextSpool,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository,
) : SecurityCleanup {

    override suspend fun runDisconnect(walletAddress: String): DisconnectReport {
        val steps = mutableListOf<DisconnectReport.StepResult>()
        val clearOnDisconnect = runCatching { settingsRepository.clearOnDisconnect.first() }
            .getOrDefault(true)

        steps += runStep("work_manager") {
            withContext(Dispatchers.IO) {
                WorkManager.getInstance(context).cancelAllWorkByTag("wallet:$walletAddress")
            }
        }

        // Not conditional on the preference: unwrapped keys must never survive a disconnect, whatever
        // the user chose about cached content.
        steps += runStep("haven_aol_keys") { havenAol.clearFor(walletAddress) }

        // Also unconditional. "Keep cached content" is a convenience choice about *ciphertext*;
        // leaving decrypted files behind for a wallet that is no longer connected is not something a
        // user is asking for when they tick that box.
        steps += runStep("plaintext_spool") { plaintextSpool.clearFor(walletAddress) }

        if (clearOnDisconnect) {
            steps += runStep("content_cache") { havenCache.clearFor(walletAddress) }
            steps += runStep("media_mirror") { mediaRepository.clearFor(walletAddress) }
        }

        steps += runStep("wallet_session") { walletSession.disconnect() }

        return DisconnectReport(steps = steps, overallOk = steps.all { it.ok })
    }

    private suspend fun runStep(name: String, block: suspend () -> Unit): DisconnectReport.StepResult {
        return try {
            block()
            DisconnectReport.StepResult(name = name, ok = true, errorCode = null)
        } catch (e: HavenError) {
            DisconnectReport.StepResult(name = name, ok = false, errorCode = e.code)
        } catch (e: Exception) {
            DisconnectReport.StepResult(name = name, ok = false, errorCode = e.message)
        }
    }
}
