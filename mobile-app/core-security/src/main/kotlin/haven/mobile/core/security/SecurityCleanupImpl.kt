package haven.mobile.core.security

import android.content.Context
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import haven.mobile.core.cache.HavenCache
import haven.mobile.core.cache.mirror.MediaRepository
import haven.mobile.core.haven.aol.HavenAol
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityCleanupImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val walletSession: WalletSession,
    private val havenAol: HavenAol,
    private val havenCache: HavenCache,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: haven.mobile.core.cache.mirror.SettingsRepository,
) : SecurityCleanup {

    override suspend fun runDisconnect(walletAddress: String): DisconnectReport {
        val steps = mutableListOf<DisconnectReport.StepResult>()

        val clearOnDisconnect = kotlinx.coroutines.flow.first(settingsRepository.clearOnDisconnect)

        val step1 = runStep("work_manager") {
            withContext(Dispatchers.IO) {
                WorkManager.getInstance(context).cancelAllWorkByTag("wallet:$walletAddress")
            }
        }
        steps.add(step1)

        val step2 = runStep("haven_aol") {
            havenAol.clearFor(walletAddress)
        }
        steps.add(step2)

        if (clearOnDisconnect) {
            val step3 = runStep("haven_cache") {
                havenCache.clearFor(walletAddress)
            }
            steps.add(step3)

            val step4 = runStep("media_repository") {
                mediaRepository.clearFor(walletAddress)
            }
            steps.add(step4)
        }

        val step5 = runStep("wallet_session") {
            walletSession.disconnect()
        }
        steps.add(step5)

        val overallOk = steps.all { it.ok }
        return DisconnectReport(steps = steps, overallOk = overallOk)
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