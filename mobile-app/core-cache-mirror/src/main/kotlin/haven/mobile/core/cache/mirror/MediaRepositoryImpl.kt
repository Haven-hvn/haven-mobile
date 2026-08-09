package haven.mobile.core.cache.mirror

import android.content.Context
import androidx.room.Room
import haven.mobile.core.domain.ArkivStatus
import haven.mobile.core.domain.Attestation
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.GateMetadata
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import haven.mobile.core.domain.TokenGate
import haven.mobile.core.domain.TokenStandard
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.arkiv.ArkivClient
import haven.mobile.core.arkiv.ArkivPage
import haven.mobile.core.cache.HavenCache
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val walletSession: WalletSession,
    private val arkivClient: ArkivClient,
    private val havenCache: HavenCache,
    private val settingsRepository: SettingsRepository,
) : MediaRepository {

    private var database: HavenMirrorDatabase? = null
    private var currentWalletAddress: String? = null

    private fun getDatabase(): HavenMirrorDatabase {
        val walletAddress = walletSession.address.value
            ?: throw HavenError.WalletNotConnected("No wallet connected")
        if (walletAddress != currentWalletAddress) {
            database?.close()
            database = null
            val dbName = HavenMirrorDatabase.databaseName(walletAddress)
            database = Room.databaseBuilder(
                context,
                HavenMirrorDatabase::class.java,
                dbName,
            ).build()
            currentWalletAddress = walletAddress
        }
        return database!!
    }

    override fun observeLibrary(owner: String): Flow<List<MediaItem>> {
        return getDatabase().mediaDao().observeLibrary(owner).map { entities ->
            entities.map { it.toMediaItem() }
        }
    }

    override suspend fun refreshLibrary(owner: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val walletAddress = walletSession.address.value
                    ?: return@withContext Result.failure(HavenError.WalletNotConnected("No wallet connected"))
                // Pagination 20 → Room mirror write-through, per-wallet namespaced (MOBILE_V1_REQUIREMENTS M1)
                // Loops cursor until nextCursor null, matching web dApp 20-per-page
                var cursor: String? = null
                val allEntities = mutableListOf<MediaMirrorEntity>()
                do {
                    val page = arkivClient.listMediaForOwner(owner, pageSize = 20, cursor = cursor)
                        .getOrElse { return@withContext Result.failure(it) }
                    val entities = page.items.map { item ->
                        item.copy(contentCacheStatus = resolveCacheStatus(item))
                            .toMirrorEntity(walletAddress)
                    }
                    allEntities.addAll(entities)
                    cursor = page.nextCursor
                } while (cursor != null)
                if (allEntities.isNotEmpty()) {
                    getDatabase().mediaDao().insertAll(allEntities)
                }
                Result.success(Unit)
            } catch (e: HavenError) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(HavenError.Internal(e.message ?: "Unknown error"))
            }
        }
    }

    override fun observeItem(id: String): Flow<MediaItem?> {
        return getDatabase().mediaDao().observeItem(id).map { entity ->
            entity?.toMediaItem()
        }
    }

    override suspend fun refreshItem(id: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val walletAddress = walletSession.address.value
                    ?: return@withContext Result.failure(HavenError.WalletNotConnected("No wallet connected"))
                val result = arkivClient.getMedia(id)
                val item = result.getOrElse { return@withContext Result.failure(it) }
                    ?: return@withContext Result.success(Unit)
                val entity = item.copy(
                    contentCacheStatus = resolveCacheStatus(item),
                ).toMirrorEntity(walletAddress)
                getDatabase().mediaDao().insertAll(listOf(entity))
                Result.success(Unit)
            } catch (e: HavenError) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(HavenError.Internal(e.message ?: "Unknown error"))
            }
        }
    }

    override suspend fun clearFor(walletAddress: String) {
        withContext(Dispatchers.IO) {
            try {
                getDatabase().mediaDao().deleteForOwner(walletAddress)
                if (currentWalletAddress == walletAddress) {
                    database?.close()
                    database = null
                    currentWalletAddress = null
                }
                val dbFile = File(context.filesDir.parent, "databases/haven-mirror-$walletAddress.db")
                dbFile.deleteRecursively()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun resolveCacheStatus(item: MediaItem): ContentCacheStatus {
        val pieceCid = item.pieceRef?.pieceCid ?: return ContentCacheStatus.UNCACHED
        return try {
            if (havenCache.exists(pieceCid)) ContentCacheStatus.CACHED else ContentCacheStatus.UNCACHED
        } catch (_: Exception) {
            ContentCacheStatus.UNCACHED
        }
    }

    private fun MediaItem.toMirrorEntity(walletAddress: String): MediaMirrorEntity {
        return MediaMirrorEntity(
            id = id,
            kind = kind.name,
            owner = owner,
            title = title,
            description = description,
            mimeType = mimeType,
            fileExtension = fileExtension,
            filenameHint = filenameHint,
            sizeBytes = sizeBytes,
            createdAt = createdAt.toString(),
            createdAtBlock = createdAtBlock,
            expiresAtBlock = expiresAtBlock,
            pieceCid = pieceRef?.pieceCid,
            pieceSize = pieceRef?.size,
            providerServiceUrls = pieceRef?.providerServiceUrls?.let { jsonFromList(it) },
            walletAddress = pieceRef?.walletAddress,
            cdnEnabled = pieceRef?.cdnEnabled ?: false,
            chain = pieceRef?.chain?.name,
            ipfsIndexed = pieceRef?.ipfsIndexed ?: false,
            unixFsRoot = pieceRef?.unixFsRoot,
            trustlessGateways = pieceRef?.trustlessGateways?.let { jsonFromList(it) },
            filecoinCid = filecoinCid,
            encryptedCid = encryptedCid,
            cidHash = cidHash,
            gateChain = gate?.chain,
            gateTokenAddress = gate?.tokenAddress,
            gateThreshold = gate?.threshold,
            gateTokenStandard = gate?.tokenStandard?.name,
            isEncrypted = isEncrypted,
            encryptionMetadata = encryptionMetadata?.let { jsonFromGateMetadata(it) },
            cidEncryptionMetadata = cidEncryptionMetadata?.let { jsonFromGateMetadata(it) },
            attestation = attestation?.let { jsonFromAttestation(it) },
            arkivStatus = arkivStatus.name,
            contentCacheStatus = contentCacheStatus.name,
            lastAccessedAt = lastAccessedAt?.toString(),
        )
    }

    private fun MediaMirrorEntity.toMediaItem(): MediaItem {
        return MediaItem(
            id = id,
            kind = MediaKind.valueOf(kind),
            owner = owner,
            title = title,
            description = description,
            mimeType = mimeType,
            fileExtension = fileExtension,
            filenameHint = filenameHint,
            sizeBytes = sizeBytes,
            createdAt = Instant.parse(createdAt),
            createdAtBlock = createdAtBlock,
            expiresAtBlock = expiresAtBlock,
            pieceRef = if (pieceCid != null) cloud.filecoin.foc.cache.PieceRef(
                pieceCid = pieceCid,
                size = pieceSize ?: 0,
                providerServiceUrls = parseJsonStringList(providerServiceUrls) ?: emptyList(),
                walletAddress = walletAddress,
                cdnEnabled = cdnEnabled,
                chain = if (chain != null) cloud.filecoin.foc.cache.FocChain.valueOf(chain) else cloud.filecoin.foc.cache.FocChain.MAINNET,
                ipfsIndexed = ipfsIndexed,
                unixFsRoot = unixFsRoot,
                trustlessGateways = parseJsonStringList(trustlessGateways) ?: emptyList(),
            ) else null,
            filecoinCid = filecoinCid,
            encryptedCid = encryptedCid,
            cidHash = cidHash,
            gate = if (gateChain != null) TokenGate(
                chain = gateChain,
                tokenAddress = gateTokenAddress ?: "",
                threshold = gateThreshold ?: 0.0,
                tokenStandard = if (gateTokenStandard != null) TokenStandard.valueOf(gateTokenStandard) else TokenStandard.ERC20,
            ) else null,
            isEncrypted = isEncrypted,
            encryptionMetadata = encryptionMetadata?.let { parseGateMetadata(it) },
            cidEncryptionMetadata = cidEncryptionMetadata?.let { parseGateMetadata(it) },
            attestation = attestation?.let { parseAttestation(it) },
            arkivStatus = ArkivStatus.valueOf(arkivStatus),
            contentCacheStatus = ContentCacheStatus.valueOf(contentCacheStatus),
            lastAccessedAt = lastAccessedAt?.let { Instant.parse(it) },
        )
    }

    private fun jsonFromList(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    private fun jsonFromGateMetadata(metadata: GateMetadata): String {
        val obj = JSONObject()
        when (metadata) {
            is GateMetadata.V1 -> {
                obj.put("type", "V1")
                obj.put("wrappedKey", metadata.wrappedKey.toString(Charsets.UTF_8))
                obj.put("nonce", metadata.nonce)
            }
            is GateMetadata.V3 -> {
                obj.put("type", "V3")
                obj.put("epochId", metadata.epochId)
                obj.put("wrappedKey", metadata.wrappedKey.toString(Charsets.UTF_8))
                obj.put("gateReference", metadata.gateReference)
            }
        }
        return obj.toString()
    }

    private fun jsonFromAttestation(attestation: Attestation): String {
        val obj = JSONObject()
        obj.put("subject", attestation.subject)
        obj.put("signature", attestation.signature.toString(Charsets.UTF_8))
        obj.put("signerKeyId", attestation.signerKeyId)
        val proofArr = JSONArray()
        attestation.merkleProof?.forEach { proofArr.put(it.toString(Charsets.UTF_8)) }
        obj.put("merkleProof", proofArr)
        obj.put("issuedAt", attestation.issuedAt.toString())
        return obj.toString()
    }

    private fun parseJsonStringList(json: String?): List<String>? {
        if (json == null) return null
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseGateMetadata(json: String): GateMetadata {
        val obj = JSONObject(json)
        val type = obj.optString("type", "V1")
        return if (type == "V3") {
            GateMetadata.V3(
                epochId = obj.getLong("epochId"),
                wrappedKey = obj.getString("wrappedKey").toByteArray(Charsets.UTF_8),
                gateReference = obj.getString("gateReference"),
            )
        } else {
            GateMetadata.V1(
                wrappedKey = obj.getString("wrappedKey").toByteArray(Charsets.UTF_8),
                nonce = obj.getString("nonce"),
            )
        }
    }

    private fun parseAttestation(json: String): Attestation {
        val obj = JSONObject(json)
        val proofArr = obj.optJSONArray("merkleProof")
        val merkleProof = if (proofArr != null) {
            (0 until proofArr.length()).map { proofArr.getString(it).toByteArray(Charsets.UTF_8) }
        } else {
            null
        }
        return Attestation(
            subject = obj.getString("subject"),
            signature = obj.getString("signature").toByteArray(Charsets.UTF_8),
            signerKeyId = obj.getString("signerKeyId"),
            merkleProof = merkleProof,
            issuedAt = Instant.parse(obj.getString("issuedAt")),
        )
    }
}