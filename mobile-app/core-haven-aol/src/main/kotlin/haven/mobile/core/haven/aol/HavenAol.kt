package haven.mobile.core.haven.aol

import haven.mobile.core.domain.GateMetadata
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.crypto.AesKeyCache
import haven.mobile.core.wallet.WalletSession
import dev.ic.kotlin.agent.Agent
import dev.ic.kotlin.agent.HttpTransport
import dev.ic.kotlin.agent.OkHttpTransport
import dev.ic.kotlin.agent.RejectedException
import dev.ic.kotlin.candid.CandidDecoder
import dev.ic.kotlin.candid.CandidEncoder
import dev.ic.kotlin.candid.CandidValue
import dev.ic.kotlin.candid.Principal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

interface HavenAol {
    suspend fun decrypt(item: MediaItem, session: WalletSession): Result<ByteArray>
    suspend fun verificationKey(): Result<ByteArray>
    suspend fun decryptAll(items: List<MediaItem>, session: WalletSession): List<Result<ByteArray>>
    fun clearFor(walletAddress: String)
}