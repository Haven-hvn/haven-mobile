package haven.mobile.core.arkiv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

import haven.mobile.core.domain.ArkivStatus
import haven.mobile.core.domain.Attestation
import haven.mobile.core.domain.Community
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.GateMetadata
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import haven.mobile.core.domain.TokenGate
import haven.mobile.core.domain.TokenStandard
import haven.mobile.core.domain.error.HavenError

@Singleton
class ArkivClientImpl @Inject constructor(
    private val config: ArkivConfig,
) : ArkivClient {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.timeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(config.timeoutMillis, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun listMediaForOwner(
        owner: String,
        pageSize: Int,
        cursor: String?,
    ): Result<ArkivPage<MediaItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = buildUrl("/api/arkiv/media")
                    .addQueryParameter("owner", owner)
                    .addQueryParameter("pageSize", pageSize.toString())
                    .apply { cursor?.let { addQueryParameter("cursor", it) } }
                    .build()
                    .toString()
                val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        HavenError.NetworkError("Arkiv listMedia request failed: ${response.code}"),
                    )
                }
                val body = response.body?.string() ?: return@withContext Result.failure(
                    HavenError.CacheMiss("Empty response from Arkiv"),
                )
                val json = JSONObject(body)
                val items = json.getJSONArray("items")
                val mediaItems = (0 until items.length()).map {
                    items.getJSONObject(it).toMediaItem()
                }
                val nextCursor = json.optString("nextCursor", null).takeIf { it.isNotEmpty() }
                Result.success(ArkivPage(items = mediaItems, nextCursor = nextCursor))
            } catch (e: HavenError) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(HavenError.NetworkError(e.message ?: "Unknown error"))
            }
        }
    }

    override suspend fun discoverUserCommunities(address: String): Result<List<Community>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = buildUrl("/api/arkiv/communities")
                    .addQueryParameter("address", address)
                    .build()
                    .toString()
                val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        HavenError.NetworkError("Arkiv discoverUserCommunities request failed: ${response.code}"),
                    )
                }
                val body = response.body?.string() ?: return@withContext Result.failure(
                    HavenError.CacheMiss("Empty response from Arkiv"),
                )
                val jsonArray = JSONArray(body)
                val communities = (0 until jsonArray.length()).map {
                    jsonArray.getJSONObject(it).toCommunity()
                }
                Result.success(communities)
            } catch (e: HavenError) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(HavenError.NetworkError(e.message ?: "Unknown error"))
            }
        }
    }

    override suspend fun getMedia(id: String): Result<MediaItem?> {
        return withContext(Dispatchers.IO) {
            try {
                val url = buildUrl("/api/arkiv/media/$id").build().toString()
                val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
                if (!response.isSuccessful) {
                    if (response.code == 404) {
                        return@withContext Result.success(null)
                    }
                    return@withContext Result.failure(
                        HavenError.NetworkError("Arkiv getMedia request failed: ${response.code}"),
                    )
                }
                val body = response.body?.string() ?: return@withContext Result.success(null)
                val json = JSONObject(body)
                Result.success(json.toMediaItem())
            } catch (e: HavenError) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(HavenError.NetworkError(e.message ?: "Unknown error"))
            }
        }
    }

    private fun buildUrl(path: String): okhttp3.HttpUrl.Builder {
        val baseUrl = okhttp3.HttpUrl.parse(config.endpointUrl)
            ?: throw HavenError.Internal("Invalid Arkiv endpoint URL: ${config.endpointUrl}")
        return baseUrl.newBuilder().addPathSegments(path)
    }

    private fun JSONObject.toMediaItem(): MediaItem {
        return MediaItem(
            id = getString("id"),
            kind = deriveMediaKind(optString("mimeType", null), optString("fileExtension", null)),
            owner = getString("owner"),
            title = getString("title"),
            description = optString("description", null).takeIf { it.isNotEmpty() },
            mimeType = optString("mimeType", null).takeIf { it.isNotEmpty() },
            fileExtension = optString("fileExtension", null).takeIf { it.isNotEmpty() },
            filenameHint = optString("filenameHint", null).takeIf { it.isNotEmpty() },
            sizeBytes = optLong("sizeBytes"),
            createdAt = Instant.parse(getString("createdAt")),
            createdAtBlock = optLong("createdAtBlock"),
            expiresAtBlock = optLong("expiresAtBlock"),
            pieceRef = if (has("pieceCid") && !isNull("pieceCid")) {
                cloud.filecoin.foc.cache.PieceRef(
                    pieceCid = getString("pieceCid"),
                    size = optLong("pieceSize"),
                    providerServiceUrls = parseStringList("providerServiceUrls"),
                    walletAddress = optString("walletAddress", null).takeIf { it.isNotEmpty() },
                    cdnEnabled = optBoolean("cdnEnabled"),
                    chain = if (has("chain") && !isNull("chain")) {
                        cloud.filecoin.foc.cache.FocChain.valueOf(getString("chain"))
                    } else cloud.filecoin.foc.cache.FocChain.MAINNET,
                    ipfsIndexed = optBoolean("ipfsIndexed"),
                    unixFsRoot = optString("unixFsRoot", null).takeIf { it.isNotEmpty() },
                    trustlessGateways = parseStringList("trustlessGateways"),
                )
            } else null,
            filecoinCid = optString("filecoinCid", null).takeIf { it.isNotEmpty() },
            encryptedCid = optString("encryptedCid", null).takeIf { it.isNotEmpty() },
            cidHash = optString("cidHash", null).takeIf { it.isNotEmpty() },
            gate = if (has("gateChain") && !isNull("gateChain")) {
                TokenGate(
                    chain = getString("gateChain"),
                    tokenAddress = optString("gateTokenAddress", null).takeIf { it.isNotEmpty() } ?: "",
                    threshold = optDouble("gateThreshold"),
                    tokenStandard = if (has("gateTokenStandard") && !isNull("gateTokenStandard")) {
                        TokenStandard.valueOf(getString("gateTokenStandard"))
                    } else TokenStandard.ERC20,
                )
            } else null,
            isEncrypted = optBoolean("isEncrypted"),
            encryptionMetadata = parseGateMetadata("encryptionMetadataV1", "encryptionMetadataV3"),
            cidEncryptionMetadata = parseGateMetadata("cidEncryptionMetadataV1", "cidEncryptionMetadataV3"),
            attestation = if (has("attestation") && !isNull("attestation")) {
                val attObj = getJSONObject("attestation")
                Attestation(
                    subject = attObj.getString("subject"),
                    signature = attObj.getString("signature").toByteArray(Charsets.UTF_8),
                    signerKeyId = attObj.getString("signerKeyId"),
                    merkleProof = if (attObj.has("merkleProof") && !attObj.isNull("merkleProof")) {
                        val arr = attObj.getJSONArray("merkleProof")
                        (0 until arr.length()).map { arr.getString(it).toByteArray(Charsets.UTF_8) }
                    } else null,
                    issuedAt = Instant.parse(attObj.getString("issuedAt")),
                )
            } else null,
            arkivStatus = ArkivStatus.valueOf(getString("arkivStatus")),
            contentCacheStatus = ContentCacheStatus.valueOf(getString("contentCacheStatus")),
            lastAccessedAt = optString("lastAccessedAt", null)?.takeIf { it.isNotEmpty() }?.let { Instant.parse(it) },
        )
    }

    private fun JSONObject.toCommunity(): Community {
        return Community(
            gate = TokenGate(
                chain = getString("gateChain"),
                tokenAddress = optString("gateTokenAddress", null).takeIf { it.isNotEmpty() } ?: "",
                threshold = optDouble("gateThreshold"),
                tokenStandard = if (has("gateTokenStandard") && !isNull("gateTokenStandard")) {
                    TokenStandard.valueOf(getString("gateTokenStandard"))
                } else TokenStandard.ERC20,
            ),
        )
    }

    private fun deriveMediaKind(mimeType: String?, fileExtension: String?): MediaKind {
        if (mimeType != null) {
            return when {
                mimeType.startsWith("video/") -> MediaKind.VIDEO
                mimeType.startsWith("audio/") -> MediaKind.AUDIO
                mimeType.startsWith("image/") -> MediaKind.IMAGE
                mimeType == "application/pdf" -> MediaKind.DOCUMENT
                else -> deriveMediaKindFromExtension(fileExtension)
            }
        }
        return deriveMediaKindFromExtension(fileExtension)
    }

    private fun deriveMediaKindFromExtension(ext: String?): MediaKind {
        if (ext == null) return MediaKind.FILE
        val lower = ext.lowercase()
        return when {
            lower in setOf(".mp4", ".mkv", ".webm", ".mov") -> MediaKind.VIDEO
            lower in setOf(".mp3", ".flac", ".ogg", ".wav", ".m4a") -> MediaKind.AUDIO
            lower in setOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".heic") -> MediaKind.IMAGE
            lower == ".pdf" -> MediaKind.DOCUMENT
            else -> MediaKind.FILE
        }
    }

    private fun JSONObject.parseGateMetadata(v1Key: String, v3Key: String): GateMetadata? {
        if (has(v1Key) && !isNull(v1Key)) {
            val obj = getJSONObject(v1Key)
            return GateMetadata.V1(
                wrappedKey = obj.getString("wrappedKey").toByteArray(Charsets.UTF_8),
                nonce = obj.getString("nonce"),
            )
        }
        if (has(v3Key) && !isNull(v3Key)) {
            val obj = getJSONObject(v3Key)
            return GateMetadata.V3(
                epochId = obj.getLong("epochId"),
                wrappedKey = obj.getString("wrappedKey").toByteArray(Charsets.UTF_8),
                gateReference = obj.getString("gateReference"),
            )
        }
        return null
    }

    private fun JSONObject.parseStringList(key: String): List<String>? {
        if (!has(key) || isNull(key)) return null
        val arr = getJSONArray(key)
        return (0 until arr.length()).map { arr.getString(it) }
    }
}