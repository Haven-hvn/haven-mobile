package haven.mobile.core.cache.mirror

import android.content.Context
import androidx.room.Room
import haven.mobile.core.domain.ArkivStatus
import haven.mobile.core.domain.Attestation
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.GateMetadata
import haven.mobile.core.domain.HavenChain
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import haven.mobile.core.domain.MerkleProofStep
import haven.mobile.core.domain.TokenGate
import haven.mobile.core.domain.TokenStandard
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.arkiv.ArkivClient
import haven.mobile.core.arkiv.ArkivPage
import haven.mobile.core.cache.HavenCache
import haven.mobile.core.collections.CollectionRepository
import haven.mobile.core.collections.GateAccessChecker
import haven.mobile.core.collections.gateKeyOrNull
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class MediaRepositoryImpl @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val walletSession: WalletSession,
    private val arkivClient: ArkivClient,
    private val havenCache: HavenCache,
    private val settingsRepository: SettingsRepository,
    /**
     * Where the reader's communities actually come from: what the wallet holds.
     *
     * Arkiv's `discoverUserCommunities` derives gates from entities you *own*, which is the publisher's
     * view — useless to a reader who has published nothing. The access check answers the question that
     * matters instead.
     */
    private val collectionRepository: CollectionRepository,
    /** Intersects Arkiv's stored gate conditions with the wallet's balances across the enabled chains. */
    private val gateAccessChecker: GateAccessChecker,
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
            )
                // The mirror is a cache of Arkiv, so a schema change drops it and the next refresh
                // rebuilds it. See HavenMirrorDatabase for why there are no migrations.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
            currentWalletAddress = walletAddress
        }
        return database!!
    }

    private companion object {
        /** Matches the web dApp's page size so paging behaviour stays comparable. */
        const val PAGE_SIZE = 20
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

    override fun observeAccessible(): Flow<List<MediaItem>> {
        return getDatabase().mediaDao().observeAccessible().map { entities ->
            entities.map { it.toMediaItem() }
        }
    }

    override suspend fun refreshAccessible(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val walletAddress = walletSession.address.value
                    ?: return@withContext Result.failure(
                        HavenError.WalletNotConnected("No wallet connected"),
                    )

                val chains = settingsRepository.enabledChains.first()
                    .ifEmpty { HavenChain.mainnets.toSet() }

                // Every Arkiv call below degrades to empty on failure (partial success is
                // success), but the failures are collected: if NOTHING comes back and every
                // call errored, that is an outage, not an empty library, and returning
                // success renders a lie the user cannot distinguish from "holds nothing".
                val failures = mutableListOf<Throwable>()
                fun <T> Result<T>.orEmptyLogged(source: String, default: T): T {
                    exceptionOrNull()?.let {
                        failures.add(it)
                        Timber.w(it, "refreshAccessible: %s failed", source)
                    }
                    return getOrDefault(default)
                }

                // ── The intersection ──────────────────────────────────────────────────────────
                // Arkiv stores what every archive requires; the wallet's balances say what it has.
                // What overlaps is what this reader can open. Neither side is authoritative alone:
                // holdings without conditions is a wallet inventory, conditions without holdings is a
                // catalogue.
                val candidateGates = buildList {
                    addAll(arkivClient.discoverGates(chains).orEmptyLogged("discoverGates", emptyList()))
                    // The roster now merges its bundled seed with the same live index, so this is
                    // belt-and-braces for when the index is reachable from one call site but not the
                    // other — not the only dynamic source.
                    addAll(collectionRepository.accessibleGates(chains))
                    // Gates this wallet has published under. A creator keeps access to their own
                    // community even if the index cannot be reached.
                    addAll(
                        arkivClient.discoverUserCommunities(walletAddress)
                            .orEmptyLogged("discoverUserCommunities", emptyList())
                            .map { it.gate },
                    )
                }.distinctBy { gate ->
                    "${HavenChain.parse(gate.chain)?.aolVariant}:${gate.tokenAddress.lowercase()}"
                }

                val satisfiedKeys = if (candidateGates.isEmpty()) {
                    emptySet()
                } else {
                    gateAccessChecker.satisfied(walletAddress, candidateGates, chains)
                }
                val openable = candidateGates.filter { it.gateKeyOrNull() in satisfiedKeys }

                // ── Own uploads ───────────────────────────────────────────────────────────────
                // A creator sees their own work regardless of what they hold: they published it, and
                // they may well have moved the gating asset on since.
                val ownItems = arkivClient.listMediaForOwner(walletAddress, PAGE_SIZE, null)
                    .orEmptyLogged("listMediaForOwner", null)
                    ?.items
                    .orEmpty()

                if (openable.isEmpty() && ownItems.isEmpty()) {
                    // Nothing to fetch is not a failure: a wallet that holds nothing yet has an empty
                    // library, and the Communities screen is where that gets fixed. But nothing
                    // fetched BECAUSE every call errored is an outage — fail with the first cause
                    // so the screen says "couldn't reach" instead of rendering fake-empty.
                    unreachableCauseOrNull(openable.size, ownItems.size, failures)?.let {
                        return@withContext Result.failure(it)
                    }
                    return@withContext Result.success(Unit)
                }

                var reached = 0
                var lastFailure: Throwable? = null

                if (ownItems.isNotEmpty()) {
                    persist(ownItems, walletAddress)
                    reached++
                }

                openable.forEach { gate ->
                    var cursor: String? = null
                    do {
                        val page = arkivClient.listMediaForCommunity(
                            gate = gate,
                            pageSize = PAGE_SIZE,
                            cursor = cursor,
                        ).getOrElse { throwable ->
                            lastFailure = throwable
                            return@forEach
                        }

                        persist(page.items, walletAddress)
                        cursor = page.nextCursor
                    } while (cursor != null)
                    reached++
                }

                if (reached == 0 && lastFailure != null) {
                    Timber.w(lastFailure, "refreshAccessible: all community pages failed")
                    Result.failure(lastFailure!!)
                } else {
                    lastFailure?.let { Timber.w(it, "refreshAccessible: some community pages failed") }
                    Result.success(Unit)
                }
            } catch (e: HavenError) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(HavenError.Internal(e.message ?: "Refresh failed"))
            }
        }
    }

    /** Write-through, with each item's residency resolved against the content cache. */
    private suspend fun persist(items: List<MediaItem>, walletAddress: String) {
        if (items.isEmpty()) return
        val entities = items.map { item ->
            item.copy(contentCacheStatus = resolveCacheStatus(item)).toMirrorEntity(walletAddress)
        }
        getDatabase().mediaDao().insertAll(entities)
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
                // deleteAll, not deleteForOwner: the mirror now also holds items published by other
                // wallets (the feed), and owner-scoped deletion would leave those behind.
                getDatabase().mediaDao().deleteAll()
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
            durationSeconds = durationSeconds,
            creatorHandle = creatorHandle,
            creatorAddress = creatorAddress,
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
            durationSeconds = durationSeconds,
            creatorHandle = creatorHandle,
            creatorAddress = creatorAddress,
        )
    }

    private fun jsonFromList(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    private fun jsonFromAttestation(attestation: Attestation): String {
        val obj = JSONObject()
        obj.put("evmAddress", attestation.evmAddress)
        obj.put("chain", attestation.chain)
        obj.put("tokenAddress", attestation.tokenAddress)
        obj.put("threshold", attestation.threshold)
        obj.put("balanceAtCheck", attestation.balanceAtCheck)
        obj.put("cidHash", attestation.cidHash)
        obj.put("timestamp", attestation.timestamp)
        when (attestation) {
            is Attestation.Single -> {
                obj.put("type", "single")
                obj.put("signature", attestation.signature)
            }
            is Attestation.Merkle -> {
                obj.put("type", "merkle")
                obj.put("cidCount", attestation.cidCount)
                val proofArr = JSONArray()
                attestation.merkleProof.forEach {
                    proofArr.put(JSONObject().put("side", it.side).put("hash", it.hash))
                }
                obj.put("merkleProof", proofArr)
                obj.put("merkleRoot", attestation.merkleRoot)
                obj.put("rootSignature", attestation.rootSignature)
            }
        }
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

    /**
     * Mirror rows are disposable cache: anything unreadable (including rows written by older
     * app versions) degrades to "no attestation" and is re-fetched on the next refresh.
     */
    private fun parseAttestation(json: String): Attestation? {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val evmAddress = obj.optString("evmAddress", null)?.takeIf { it.isNotEmpty() } ?: return null
        val chain = obj.optString("chain", null)?.takeIf { it.isNotEmpty() } ?: return null
        val tokenAddress = obj.optString("tokenAddress", null)?.takeIf { it.isNotEmpty() } ?: return null
        val threshold = obj.optDouble("threshold").takeIf { !it.isNaN() } ?: return null
        val balanceAtCheck = obj.optDouble("balanceAtCheck").takeIf { !it.isNaN() } ?: return null
        val cidHash = obj.optString("cidHash", null)?.takeIf { it.isNotEmpty() } ?: return null
        if (obj.isNull("timestamp")) return null
        val timestamp = obj.optLong("timestamp")
        if (obj.optString("type", null) == "merkle") {
            if (obj.isNull("cidCount")) return null
            val proofArr = obj.optJSONArray("merkleProof") ?: return null
            val steps = (0 until proofArr.length()).map { idx ->
                val step = proofArr.optJSONObject(idx) ?: return null
                MerkleProofStep(
                    side = step.optString("side", null) ?: return null,
                    hash = step.optString("hash", null) ?: return null,
                )
            }
            return Attestation.Merkle(
                evmAddress = evmAddress,
                chain = chain,
                tokenAddress = tokenAddress,
                threshold = threshold,
                balanceAtCheck = balanceAtCheck,
                cidHash = cidHash,
                timestamp = timestamp,
                cidCount = obj.optLong("cidCount"),
                merkleProof = steps,
                merkleRoot = obj.optString("merkleRoot", null)?.takeIf { it.isNotEmpty() } ?: return null,
                rootSignature = obj.optString("rootSignature", null)?.takeIf { it.isNotEmpty() } ?: return null,
            )
        }
        return Attestation.Single(
            evmAddress = evmAddress,
            chain = chain,
            tokenAddress = tokenAddress,
            threshold = threshold,
            balanceAtCheck = balanceAtCheck,
            cidHash = cidHash,
            timestamp = timestamp,
            signature = obj.optString("signature", null)?.takeIf { it.isNotEmpty() } ?: return null,
        )
    }
}

/**
 * Outage, or genuinely empty?
 *
 * `refreshAccessible` degrades every Arkiv call to empty so one unreachable community cannot
 * blank a library others answered for — but when NOTHING comes back and calls errored, success
 * renders a lie. The first cause is the one the screen shows ("couldn't reach …"); the rest
 * are in logcat. Pure so the rule pins without Room or a wallet.
 */
internal fun unreachableCauseOrNull(
    openCount: Int,
    ownCount: Int,
    failures: List<Throwable>,
): Throwable? =
    if (openCount == 0 && ownCount == 0 && failures.isNotEmpty()) failures.first() else null

/**
 * Gate metadata <-> its mirror-column JSON. Pure and top-level so the codec pins without
 * Room or a wallet; the `type` tag routes each variant, with unknown tags degrading to V1
 * exactly as before (old rows keep parsing).
 */
internal fun jsonFromGateMetadata(metadata: GateMetadata): String {
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
        is GateMetadata.V4 -> {
            obj.put("type", "V4")
            obj.put("epochId", metadata.epochId)
            obj.put("marketCapTargetUsd", metadata.marketCapTargetUsd)
            obj.put("wrappedKey", metadata.wrappedKey.toString(Charsets.UTF_8))
            obj.put("gateReference", metadata.gateReference)
            obj.put("tokenAddress", metadata.tokenAddress)
            obj.put("chain", metadata.chain)
        }
        is GateMetadata.Sealed -> {
            obj.put("type", "Sealed")
            obj.put("version", metadata.version)
            obj.put("encryptedAesKey", metadata.encryptedAesKey)
        }
    }
    return obj.toString()
}

internal fun parseGateMetadata(json: String): GateMetadata {
    val obj = JSONObject(json)
    val type = obj.optString("type", "V1")
    return if (type == "V3") {
        GateMetadata.V3(
            epochId = obj.getLong("epochId"),
            wrappedKey = obj.getString("wrappedKey").toByteArray(Charsets.UTF_8),
            gateReference = obj.getString("gateReference"),
        )
    } else if (type == "V4") {
        GateMetadata.V4(
            epochId = obj.optLong("epochId", 0),
            marketCapTargetUsd = obj.optLong("marketCapTargetUsd", 0),
            wrappedKey = obj.optString("wrappedKey", "").toByteArray(Charsets.UTF_8),
            gateReference = obj.optString("gateReference", ""),
            tokenAddress = obj.optString("tokenAddress", ""),
            chain = obj.optString("chain", ""),
        )
    } else if (type == "Sealed") {
        GateMetadata.Sealed(
            version = obj.optLong("version", 0),
            encryptedAesKey = obj.optString("encryptedAesKey", ""),
        )
    } else {
        GateMetadata.V1(
            wrappedKey = obj.getString("wrappedKey").toByteArray(Charsets.UTF_8),
            nonce = obj.getString("nonce"),
        )
    }
}