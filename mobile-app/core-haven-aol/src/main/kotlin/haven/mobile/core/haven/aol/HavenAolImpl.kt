package haven.mobile.core.haven.aol

import haven.mobile.core.domain.GateMetadata
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.crypto.AesKeyCache
import haven.mobile.core.wallet.WalletSession
import dev.ic.kotlin.agent.Agent
import dev.ic.kotlin.agent.AnonymousIdentity
import dev.ic.kotlin.agent.OkHttpTransport
import dev.ic.kotlin.candid.CandidDecoder
import dev.ic.kotlin.candid.CandidEncoder
import dev.ic.kotlin.candid.CandidValue
import dev.ic.kotlin.candid.Principal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HavenAolImpl @Inject constructor(
    private val config: HavenAolConfig,
    private val walletSession: WalletSession,
    private val aesKeyCache: AesKeyCache,
    private val nonceManager: NonceManager,
    private val gateRequestBuilder: GateRequestBuilder
) : HavenAol {

    private val agent: Agent by lazy {
        Agent(
            OkHttpTransport(
                config.icHost.ifBlank { "https://ic0.app" },
                OkHttpClient.Builder()
                    .connectTimeout(config.requestTimeoutMillis, TimeUnit.MILLISECONDS)
                    .readTimeout(config.requestTimeoutMillis, TimeUnit.MILLISECONDS)
                    .build()
            ),
            AnonymousIdentity.INSTANCE,
            10_000L,
            1_000L,
            30_000L
        )
    }

    private val verificationKeyCache = mutableMapOf<String, ByteArray>()

    override suspend fun decrypt(item: MediaItem, session: WalletSession): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                when (item.encryptionMetadata) {
                    is GateMetadata.V1 -> decryptV1(item, session)
                    is GateMetadata.V3 -> decryptAll(listOf(item), session).first()
                    null -> Result.failure(HavenError.UnsupportedGateMetadata("Missing encryptionMetadata"))
                    else -> Result.failure(HavenError.UnsupportedGateMetadata("Unknown metadata type"))
                }
            } catch (e: HavenError) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(HavenError.CanisterCallFailed(e.message ?: "Unknown error"))
            }
        }
    }

    override suspend fun verificationKey(): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                val walletAddress = walletSession.address.value
                    ?: return@withContext Result.failure(HavenError.WalletNotConnected("No wallet connected"))
                val cached = verificationKeyCache[walletAddress]
                if (cached != null) {
                    return@withContext Result.success(cached)
                }
                if (config.canisterId.isBlank()) {
                    return@withContext Result.failure(HavenError.CanisterCallFailed("Haven-AOL canisterId not configured"))
                }
                val principal = Principal.fromText(config.canisterId)
                val args = CandidEncoder.INSTANCE.encode(listOf(CandidValue.CandidEmpty))
                val reply = agent.query(principal, "get_verification_key", args, 0L)
                val decoded = CandidDecoder.INSTANCE.decode(reply)
                val key = (decoded.first() as CandidValue.CandidBlob).bytes
                verificationKeyCache[walletAddress] = key
                Result.success(key)
            } catch (e: HavenError) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(HavenError.CanisterCallFailed(e.message ?: "Unknown error"))
            }
        }
    }

    override suspend fun decryptAll(items: List<MediaItem>, session: WalletSession): List<Result<ByteArray>> {
        return withContext(Dispatchers.IO) {
            try {
                if (items.isEmpty()) return@withContext emptyList()
                val grouped = items.groupBy { item ->
                    val gate = item.gate
                    if (gate != null) "${gate.chain}:${gate.tokenAddress}:${gate.threshold}:${gate.tokenStandard.name}"
                    else "no-gate:${item.id}"
                }
                val groupResults = mutableMapOf<String, List<Result<ByteArray>>>()
                val groupItemsByKey = mutableMapOf<String, List<MediaItem>>()
                for ((groupKey, groupItems) in grouped) {
                    groupItemsByKey[groupKey] = groupItems
                    // Check if any item in group is V3; otherwise treat as V1 per-item
                    val hasV3 = groupItems.any { it.encryptionMetadata is GateMetadata.V3 }
                    groupResults[groupKey] = if (hasV3) {
                        decryptV3Group(groupItems, session)
                    } else {
                        groupItems.map { decryptV1(it, session) }
                    }
                }
                items.map { item ->
                    val gate = item.gate
                    val groupKey = if (gate != null) "${gate.chain}:${gate.tokenAddress}:${gate.threshold}:${gate.tokenStandard.name}" else "no-gate:${item.id}"
                    val groupResult = groupResults[groupKey]!!
                    val itemsInGroup = groupItemsByKey[groupKey]!!
                    val indexInGroup = itemsInGroup.indexOf(item)
                    groupResult[indexInGroup]
                }
            } catch (e: HavenError) {
                items.map { Result.failure(e) }
            } catch (e: Exception) {
                items.map { Result.failure(HavenError.CanisterCallFailed(e.message ?: "Unknown error")) }
            }
        }
    }

    override suspend fun clearFor(walletAddress: String) {
        aesKeyCache.clearAll()
        nonceManager.clearFor(walletAddress, config.canisterId)
        verificationKeyCache.remove(walletAddress)
    }

    private suspend fun decryptV1(item: MediaItem, session: WalletSession): Result<ByteArray> {
        val walletAddress = session.address.value
            ?: return Result.failure(HavenError.WalletNotConnected("No wallet connected"))
        val nonce = nonceManager.getNonce(walletAddress, config.canisterId)
        val payload = gateRequestBuilder.buildV1Request(item, nonce, walletAddress)
        val sigResult = session.signTypedDataV4(payload)
        if (sigResult.isFailure) {
            val ex = sigResult.exceptionOrNull()
            return Result.failure(HavenError.SigningFailed(ex?.message ?: "Signing failed"))
        }
        val signature = sigResult.getOrNull()!!

        if (config.canisterId.isBlank()) {
            return Result.failure(HavenError.CanisterCallFailed("Haven-AOL canisterId not configured"))
        }
        val principal = Principal.fromText(config.canisterId)
        val args = CandidEncoder.INSTANCE.encode(listOf(
            CandidValue.CandidText(item.id),
            CandidValue.CandidText(payload),
            CandidValue.CandidText(signature)
        ))

        val reply = agent.call(principal, "decrypt", args, 0L, ByteArray(0))
        val decoded = CandidDecoder.INSTANCE.decode(reply)
        val aesKey = (decoded.first() as CandidValue.CandidBlob).bytes
        val pieceCid = item.pieceRef?.pieceCid
        if (pieceCid != null) aesKeyCache.put(pieceCid, aesKey)
        return Result.success(aesKey)
    }

    private suspend fun decryptV3Group(items: List<MediaItem>, session: WalletSession): List<Result<ByteArray>> {
        val walletAddress = session.address.value
            ?: return items.map { Result.failure(HavenError.WalletNotConnected("No wallet connected")) }
        val nonce = nonceManager.getNonce(walletAddress, config.canisterId)
        val firstItem = items.first()
        val payload = gateRequestBuilder.buildV3Request(firstItem, nonce, walletAddress)
        val sigResult = session.signTypedDataV4(payload)
        if (sigResult.isFailure) {
            val ex = sigResult.exceptionOrNull()
            return items.map { Result.failure(HavenError.SigningFailed(ex?.message ?: "Signing failed")) }
        }
        val signature = sigResult.getOrNull()!!

        if (config.canisterId.isBlank()) {
            return items.map { Result.failure(HavenError.CanisterCallFailed("Haven-AOL canisterId not configured")) }
        }
        val principal = Principal.fromText(config.canisterId)
        val itemIdsCandid = items.map { CandidValue.CandidText(it.id) }
        val args = CandidEncoder.INSTANCE.encode(listOf(
            CandidValue.CandidVec(itemIdsCandid),
            CandidValue.CandidText(payload),
            CandidValue.CandidText(signature)
        ))

        val reply = agent.call(principal, "decrypt_all", args, 0L, ByteArray(0))
        val decoded = CandidDecoder.INSTANCE.decode(reply)
        val keys = (decoded.first() as CandidValue.CandidVec).content.map {
            (it as CandidValue.CandidBlob).bytes
        }

        return items.mapIndexed { index, item ->
            if (index < keys.size) {
                val pieceCid = item.pieceRef?.pieceCid
                if (pieceCid != null) aesKeyCache.put(pieceCid, keys[index])
                Result.success(keys[index])
            } else {
                Result.failure(HavenError.CanisterCallFailed("Missing key for item ${item.id}"))
            }
        }
    }
}
