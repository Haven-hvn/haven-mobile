package haven.mobile.core.arkiv

import cloud.filecoin.foc.cache.FocChain
import cloud.filecoin.foc.cache.PieceRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

import haven.mobile.core.domain.ArkivStatus
import haven.mobile.core.domain.Attestation
import haven.mobile.core.domain.Community
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.GateMetadata
import haven.mobile.core.domain.HavenChain
import haven.mobile.core.domain.MediaItem
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

    /**
     * Direct `arkiv_query` JSON-RPC against the chain, byte-compatible with `@arkiv-network/sdk`
     * 0.8 `runQuery` (CLI `ArkivEngineClient.query` sends the same shape).
     *
     * There is no REST gateway in front of Arkiv. Predicates serialize to a query string with
     * type-tagged literals (`gate_token = str('0x…') AND gate_chain = i32(8453)`,
     * `$owner = addr(0x…)`, `$key = key(0x…)`); options carry the full `select` map plus a hex
     * `limit` and an opaque `cursor`; the result is `{data, blockNumber, cursor}`. Iteration
     * ends when `cursor` is absent/empty or the page comes back short.
     *
     * The tagged literals are load-bearing, verified against the live node: bare hex is
     * rejected ("a hex literal must carry its type"), double-quoted strings are a parse error
     * (single quotes only), and the combinators are `AND`/`OR`, not `&&`/`||`.
     */
    private fun arkivQuery(query: String, limit: Int, cursor: String?): JSONObject {
        val options = JSONObject()
            .put("select", JSONObject()
                .put("key", true)
                .put("owner", true)
                .put("creator", true)
                .put("createdAt", true)
                .put("updatedAt", false)
                .put("expiresAt", true)
                .put("creationFlags", false)
                .put("contentType", true)
                .put("payload", false)
                .put("attributeSchema", false)
                .put("attributes", true))
            .put("limit", "0x" + limit.toString(16))
            .apply { cursor?.let { put("cursor", it) } }
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "arkiv_query")
            .put("params", JSONArray().put(query).put(options))
            .toString()
            .toRequestBody("application/json".toMediaType())
        httpClient.newCall(Request.Builder().url(config.endpointUrl).post(body).build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw HavenError.NetworkError("Arkiv query failed (${response.code}).")
            }
            val text = response.body?.string()
                ?: throw HavenError.NetworkError("Arkiv query came back empty.")
            val result = runCatching { JSONObject(text).getJSONObject("result") }.getOrNull()
                ?: throw HavenError.NetworkError("Arkiv query came back unreadable.")
            val error = JSONObject(text).optJSONObject("error")
            if (error != null) {
                throw HavenError.NetworkError("Arkiv rejected the query (${error.optString("message")}).")
            }
            return result
        }
    }

    /**
     * `key = str('value')` predicate, exactly like the SDK serializes `eq(name, string)`.
     * Single quotes only — a double-quoted string is a node parse error.
     */
    private fun eqStr(key: String, value: String): String =
        "$key = str('${value.replace("'", "")}')"

    /**
     * `key = i32(123)` predicate for numeric attributes (chain ids), like the SDK's
     * `eq(name, number)`. Bare ints parse, but writers stamp numbers as `i32` and comparison
     * is type-exact, so the tag stays.
     */
    private fun eqNum(key: String, value: Long): String = "$key = i32($value)"

    /**
     * Raw chain row -> the flat shape the item parser reads.
     *
     * The node returns SDK 0.8 `RpcEntity` rows: flat `key`/`owner`/`creator`/`createdAt`/
     * `expiresAt`/`contentType` fields plus `attributes` as `[{name, type, value}]` with
     * type-tagged values. Decoding mirrors CLI `decode_rpc_entity` / SDK
     * `entityFromRpcResult`: block heights land under the `*Block` keys the readers expect,
     * attributes merge in decoded, and anything unknown is skipped rather than guessed.
     */
    internal fun normalizeRpcEntity(raw: JSONObject): JSONObject {
        val out = JSONObject()
        raw.optString("key", null)?.let { out.put("id", it); out.put("key", it); out.put("entityKey", it) }
        raw.optString("owner", null)?.let { out.put("owner", it) }
        raw.optString("creator", null)?.let { out.put("creator", it) }
        raw.optString("contentType", null)?.let { out.put("contentType", it) }
        decodeRpcInt(raw.opt("createdAt"))?.let { out.put("createdAtBlock", it) }
        decodeRpcInt(raw.opt("expiresAt"))?.let { out.put("expiresAtBlock", it) }
        val attributes = raw.optJSONArray("attributes")
        if (attributes != null) {
            for (i in 0 until attributes.length()) {
                val entry = attributes.optJSONObject(i) ?: continue
                val name = entry.optString("name", null) ?: continue
                decodeRpcValue(entry.optString("type", null), entry.opt("value"))
                    ?.let { out.put(name, it) }
            }
        }
        return out
    }

    /**
     * One `{name, type, value}` attribute -> the flat value readers expect.
     *
     * Tags mirror CLI `decode_rpc_value` (`bool`, `i32`, `u64`, `u256`, `dec`, `bytes32`,
     * `str`, `addr`, `key`, `bytes`). Writers stamp strings as `str` and numbers as `i32`
     * (see dapp `lib/arkiv-attrs`), so those two carry every Haven attribute in practice;
     * the rest decode for completeness. Unknown tags and malformed values decode to null
     * and the attribute is skipped — a corrupt row degrades to fewer fields, not a
     * failed page.
     */
    private fun decodeRpcValue(tag: String?, value: Any?): Any? {
        if (value == null || value === JSONObject.NULL) return null
        return when (tag) {
            "bool" -> when (value) {
                is Boolean -> value
                is String -> when (value) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }
                else -> null
            }
            "i32" -> decodeRpcInt(value)
            // u64 can exceed Long.MAX_VALUE; u256 always might. Long when exact, Double
            // otherwise — the threshold reader is Double-based, so nothing downstream breaks.
            "u64", "u256" -> decodeRpcU256(value)
            "dec", "str", "addr", "key", "bytes32" -> value as? String
            // No reader takes raw bytes; keep the 0x spelling so a future one can hex-decode it.
            "bytes" -> (value as? String)?.takeIf { it.startsWith("0x") }
            else -> null
        }
    }

    /** Block heights and `i32` values: a JSON number, `0x` quantity, or decimal string. */
    private fun decodeRpcInt(value: Any?): Long? = when (value) {
        is Number -> value.toLong()
        is String -> {
            val clean = value.trim()
            runCatching {
                if (clean.startsWith("0x", ignoreCase = true)) clean.substring(2).toBigInteger(16).longValueExact()
                else clean.toLong()
            }.getOrNull()
        }
        else -> null
    }

    /** `u64`/`u256` values: exact Long when it fits, Double otherwise (never null on overflow). */
    private fun decodeRpcU256(value: Any?): Any? {
        val big: BigInteger? = when (value) {
            is BigInteger -> value
            is Number -> BigInteger.valueOf(value.toLong())
            is String -> {
                val clean = value.trim()
                runCatching {
                    if (clean.startsWith("0x", ignoreCase = true)) clean.substring(2).toBigInteger(16)
                    else clean.toBigInteger()
                }.getOrNull()
            }
            else -> null
        } ?: return null
        return try {
            big.longValueExact()
        } catch (_: ArithmeticException) {
            big.toDouble()
        }
    }

    /** One page of normalized entities plus the next cursor (null when iteration ends). */
    private fun queryPage(query: String, pageSize: Int, cursor: String?): Pair<List<JSONObject>, String?> {
        val result = arkivQuery(query, pageSize, cursor)
        val data = result.optJSONArray("data") ?: JSONArray()
        val items = List(data.length()) { idx -> normalizeRpcEntity(data.getJSONObject(idx)) }
        val next = result.optString("cursor", null).takeIf { it.isNotEmpty() }
            ?.takeIf { items.size >= pageSize }
        return items to next
    }

    override suspend fun listMediaForOwner(
        owner: String,
        pageSize: Int,
        cursor: String?,
    ): Result<ArkivPage<MediaItem>> {
        notConfigured<ArkivPage<MediaItem>>()?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                // Same `$owner` lookup the SDK emits for `ownedBy` (lowercase verifies OK on-chain).
                val (items, nextCursor) = queryPage("\$owner = addr(${owner.lowercase()})", pageSize, cursor)
                Result.success(ArkivPage(items = items.map { it.toMediaItem() }, nextCursor = nextCursor))
            } catch (e: HavenError) {
                Result.failure(e)
            } catch (e: Exception) {
                // The exception text ("Failed to connect to /93.184.216.34:443") is diagnostic, not
                // something to show a reader — so it travels as the cause instead of the message.
                Result.failure(
                    HavenError.NetworkError("Couldn't reach the network. Check your connection.", e),
                )
            }
        }
    }

    override suspend fun listMediaForCommunity(
        gate: TokenGate,
        pageSize: Int,
        cursor: String?,
    ): Result<ArkivPage<MediaItem>> {
        notConfigured<ArkivPage<MediaItem>>()?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                // Filters on the gating asset rather than the author, which is what makes this a feed
                // of a community's archive instead of a list of one wallet's uploads. Same predicates
                // as the dapp's `fetchCommunityFeedForToken`; the chain is stored as the EIP-155 id.
                val chainId = HavenChain.parse(gate.chain)?.chainId
                    ?: return@withContext Result.failure(
                        HavenError.Internal("Unknown gate chain: ${gate.chain}"),
                    )
                val query = "${eqStr("gate_token", gate.tokenAddress)} AND ${eqNum("gate_chain", chainId)}"
                val (items, nextCursor) = queryPage(query, pageSize, cursor)
                Result.success(ArkivPage(items = items.map { it.toMediaItem() }, nextCursor = nextCursor))
            } catch (e: HavenError) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(
                    HavenError.NetworkError("Couldn't reach the network. Check your connection.", e),
                )
            }
        }
    }

    override suspend fun discoverGates(chains: Set<HavenChain>): Result<List<TokenGate>> {
        notConfigured<List<TokenGate>>()?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                // Arkiv has no concept of gates — they are Haven's reading of entity attributes
                // (`gate_token`/`gate_chain`/`gate_threshold`, stamped at publish time). So discovery
                // mirrors the dapp: page Haven video entities and collect distinct gate attributes
                // client-side. Bounded: discovery pages the listing, it never crawls the archive.
                val gates = mutableListOf<TokenGate>()
                var cursor: String? = null
                var pages = 0
                do {
                    val (items, next) = queryPage(VIDEO_GROUPS_QUERY, SCAN_PAGE_SIZE, cursor)
                    for (item in items) {
                        runCatching { item.toMediaItem().gate }.getOrNull()?.let { gates.add(it) }
                    }
                    cursor = next
                    pages++
                } while (cursor != null && pages < MAX_SCAN_PAGES)
                Result.success(dedupeGates(gates, chains))
            } catch (e: HavenError) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(
                    HavenError.NetworkError("Couldn't reach the network. Check your connection.", e),
                )
            }
        }
    }

    /**
     * One gate per (chain, contract): thresholds vary per entity, and the lowest is the one
     * that decides whether anything under it is readable. Shared by the attribute scan and
     * the legacy endpoint path.
     */
    private fun dedupeGates(gates: List<TokenGate>, chains: Set<HavenChain>): List<TokenGate> =
        gates
            .filter { HavenChain.parse(it.chain) in chains }
            .groupBy { "${HavenChain.parse(it.chain)?.aolVariant}:${it.tokenAddress.lowercase()}" }
            .mapNotNull { (_, group) -> group.minByOrNull { it.threshold } }

    private companion object {
        /** Entities per scan page; discovery pages the listing, it never loads the archive. */
        const val SCAN_PAGE_SIZE = 50
        /** Hard bound so a huge archive cannot turn discovery into an unbounded crawl. */
        const val MAX_SCAN_PAGES = 10
        /**
         * Haven video groups that carry gate attributes (see dapp `arkiv-publish`).
         * Flat `OR` chain exactly like the SDK renders `or(...)` — the language has no
         * `||`, and parenthesised groups are untested against the node, so none are used.
         */
        const val VIDEO_GROUPS_QUERY =
            "grp = str('haven.video.full') OR grp = str('haven.video.drip.series') OR grp = str('haven.video.drip.part')"
    }

    override suspend fun discoverUserCommunities(address: String): Result<List<Community>> {
        notConfigured<List<Community>>()?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                // Dapp parity (`discoverUserCommunities`): the wallet's own entities, gate attributes
                // read locally. Answers "nothing" for a reader who never published — that is why
                // `discoverGates` exists alongside it.
                val (items, _) = queryPage("\$owner = addr(${address.lowercase()})", SCAN_PAGE_SIZE, null)
                val communities = items
                    .mapNotNull { runCatching { it.toCommunity() }.getOrNull() }
                    // Local key, not the checker: core-collections owns gate keys and already
                    // depends on this module, so importing it here would be circular.
                    .distinctBy { "${it.gate.chain}:${it.gate.tokenAddress.lowercase()}" }
                Result.success(communities)
            } catch (e: HavenError) {
                Result.failure(e)
            } catch (e: Exception) {
                // The exception text ("Failed to connect to /93.184.216.34:443") is diagnostic, not
                // something to show a reader — so it travels as the cause instead of the message.
                Result.failure(
                    HavenError.NetworkError("Couldn't reach the network. Check your connection.", e),
                )
            }
        }
    }

    override suspend fun getMedia(id: String): Result<MediaItem?> {
        notConfigured<MediaItem?>()?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                // Same `$key` lookup as the SDK's `getEntity`; keys are 32 bytes of hex.
                val clean = id.trim().removePrefix("0x")
                if (clean.length != 64 || !clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                    return@withContext Result.success(null)
                }
                val (items, _) = queryPage("\$key = key(0x${clean.lowercase()})", 1, null)
                Result.success(items.firstOrNull()?.let { runCatching { it.toMediaItem() }.getOrNull() })
            } catch (e: HavenError) {
                Result.failure(e)
            } catch (e: Exception) {
                // The exception text ("Failed to connect to /93.184.216.34:443") is diagnostic, not
                // something to show a reader — so it travels as the cause instead of the message.
                Result.failure(
                    HavenError.NetworkError("Couldn't reach the network. Check your connection.", e),
                )
            }
        }
    }

    /**
     * Returns a typed failure when there is no endpoint to call, and null when there is.
     *
     * Generic so each caller keeps its own `Result<T>`. Without this, an unconfigured build threw
     * out of URL building for every query, which surfaced to the user as an unexplained failure
     * rather than "not set up yet".
     */
    private fun <T> notConfigured(): Result<T>? =
        if (config.isConfigured) {
            null
        } else {
            Result.failure(
                HavenError.NetworkError(
                    "This build of Haven can't look for new content.",
                ),
            )
        }

    /**
     * Entity -> `MediaItem`, against ARKIV_FORMAT 2.0.0 canonical keys.
     *
     * `lib/parse-arkiv-video.ts` (haven-dapp) is the spec: it merges `entity.attributes` with the
     * decoded payload and reads canonical `snake_case` keys off the result. Anything it does not read
     * does not exist in practice — which ruled out two fields this parser previously invented, and one
     * it required:
     *
     *  - **`size_bytes`** — no entity carries a size. `PieceRef.size` is the size of record once foc
     *    resolves the piece, so nothing is read here and [MediaItem.sizeBytes] stays null until then.
     *  - **`thumbnail_cid`** — absent from the dapp's read path, written by nothing. Removed rather
     *    than carried as a permanently null column.
     *  - **`arkivStatus` / `contentCacheStatus` / `createdAt` / `title` / `owner`** — all were read with
     *    `getString`, which throws. The dapp hard-codes status to "active", cache state is local, and
     *    title/owner have documented fallbacks. Nothing here is required now.
     *
     * 2.0 key map (old -> new): `content_mime_type` -> `mime` (enum int),
     * `filecoin_root_cid` -> `fcid`, `piece_cid` -> `piece`, `cid_hash` -> `sha256_ct`,
     * `duration` -> `dur_s`, `creator_handle` -> `creator`, `source_uri` -> `src`,
     * `encryption_metadata` -> `gate`, `cid_encryption_metadata` -> `cid_gate`,
     * `segment_metadata` -> `seg`, `attestation` -> `attn`. Deleted with no replacement:
     * `is_encrypted` (gate presence decides), `encrypted_cid` (never indexed),
     * `created_at`/`updated_at` (system fields), `project`/`type`/`category`/`tags`
     * (replaced by `grp`).
     *
     * The camelCase spellings are accepted alongside the canonical ones because the HTTP gateway in
     * front of Arkiv may already be reshaping them.
     */
    private fun JSONObject.toMediaItem(): MediaItem {
        val mimeType = firstMime("mime", "mimeType", "contentMimeType")
        val sourceUri = firstString("src", "sourceUri")
        val extension = deriveExtension(mimeType, sourceUri)
        val title = firstString("title") ?: "Untitled"
        val pieceCid = firstString("piece", "pieceCid")

        // Gate presence decides encryption — 2.0 carries no is_encrypted flag.
        val gateMetadata = parseGateMetadata("gate")
        val cidGateMetadata = parseGateMetadata("cid_gate", "cidGate")

        return MediaItem(
            // `key` is the entity id in Arkiv; `id` is what a gateway usually renames it to.
            id = firstString("id", "key", "entityKey") ?: "",
            kind = MediaKindResolver.resolve(mimeType, extension ?: sourceUri),
            owner = (firstString("owner") ?: "").lowercase(),
            title = title,
            description = firstString("description"),
            mimeType = mimeType,
            fileExtension = extension,
            // Derived, not stored: the FILE viewer needs something to pre-fill the save dialog with.
            filenameHint = extension?.let { "${title.take(64)}$it" } ?: title.take(64),
            // Not an entity field. foc reports the real size when it resolves the piece.
            sizeBytes = null,
            createdAt = parseCreatedAt(),
            createdAtBlock = firstLong("created_at_block", "createdAtBlock"),
            expiresAtBlock = firstLong("expires_at_block", "expiresAtBlock"),
            pieceRef = pieceCid?.let { cid ->
                PieceRef(
                    pieceCid = cid,
                    // Unknown here, and foc does not need telling: it resolves size, providers, CDN and
                    // gateways itself. A stale provider list baked in from an index would send fetches
                    // at the wrong hosts.
                    size = 0L,
                    providerServiceUrls = emptyList(),
                    walletAddress = null,
                    cdnEnabled = false,
                    chain = FocChain.MAINNET,
                    ipfsIndexed = false,
                    unixFsRoot = null,
                    trustlessGateways = emptyList(),
                )
            },
            filecoinCid = firstString("fcid", "filecoinCid"),
            // 2.0 never indexes the encrypted locator — always null (kept on the model for API stability).
            encryptedCid = null,
            cidHash = firstString("sha256_ct", "cidHash"),
            gate = toTokenGate(),
            isEncrypted = gateMetadata != null,
            encryptionMetadata = gateMetadata,
            cidEncryptionMetadata = cidGateMetadata,
            attestation = parseAttestationOrNull(),
            // The dapp hard-codes "active"; expiry is decided from `expires_at_block` against the head,
            // not from a status the entity carries.
            arkivStatus = ArkivStatus.FRESH,
            // Local state. The mirror resolves it against the content cache immediately after this.
            contentCacheStatus = ContentCacheStatus.UNCACHED,
            lastAccessedAt = null,
            durationSeconds = firstLong("dur_s"),
            creatorHandle = firstString("creator", "creatorHandle"),
        )
    }

    /**
     * `created_at` is an ISO-8601 attribute written by the entity store, but not every entity has one.
     *
     * Falling back to "now" would make an old archive look newly published and sort to the top of every
     * screen, so an entity with no timestamp sorts to the bottom instead.
     */
    private fun JSONObject.parseCreatedAt(): Instant {
        firstString("created_at", "createdAt")?.let { raw ->
            runCatching { Instant.parse(raw) }.getOrNull()?.let { return it }
        }
        return Instant.fromEpochMilliseconds(0)
    }

    /** Attestations come from the canister, not the entity, so absence is normal. */
    private fun JSONObject.parseAttestationOrNull(): Attestation? {
        val attObj = optJSONObject("attn") ?: optJSONObject("attestation") ?: return null
        val subject = attObj.optString("subject", null)?.takeIf { it.isNotEmpty() } ?: return null
        val signature = attObj.optString("signature", null)?.takeIf { it.isNotEmpty() } ?: return null
        return Attestation(
            subject = subject,
            signature = signature.toByteArray(Charsets.UTF_8),
            signerKeyId = attObj.optString("signerKeyId", ""),
            merkleProof = attObj.optJSONArray("merkleProof")?.let { arr ->
                List(arr.length()) { idx -> arr.getString(idx).toByteArray(Charsets.UTF_8) }
            },
            issuedAt = attObj.optString("issuedAt", null)
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: Instant.fromEpochMilliseconds(0),
        )
    }

    /**
     * Extension for viewer selection and the save dialog.
     *
     * Entities carry no filename, so it comes from the MIME type where that is unambiguous, and from the
     * source URI's tail otherwise.
     */
    private fun deriveExtension(mimeType: String?, sourceUri: String?): String? {
        val fromMime = when (mimeType?.substringBefore(';')?.trim()?.lowercase()) {
            "video/mp4" -> ".mp4"
            "video/webm" -> ".webm"
            "video/x-matroska" -> ".mkv"
            "video/quicktime" -> ".mov"
            "audio/mpeg" -> ".mp3"
            "audio/flac" -> ".flac"
            "audio/ogg" -> ".ogg"
            "audio/wav", "audio/x-wav" -> ".wav"
            "audio/mp4", "audio/m4a" -> ".m4a"
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "image/gif" -> ".gif"
            "image/heic" -> ".heic"
            "application/pdf" -> ".pdf"
            else -> null
        }
        if (fromMime != null) return fromMime

        val tail = sourceUri?.substringAfterLast('/')?.substringBefore('?') ?: return null
        if (!tail.contains('.')) return null
        return "." + tail.substringAfterLast('.').lowercase().take(8)
    }

    /** Null when the row carries no gate Haven-AOL can evaluate; the caller drops it. */
    private fun JSONObject.toCommunity(): Community? = toTokenGate()?.let { Community(gate = it) }

    /**
     * A gate condition as stored on Arkiv.
     *
     * Attribute names follow the entity spec (`gate_chain`, `gate_token`, `gate_threshold`); the camelCase
     * spellings are accepted too because the gateway's JSON shape has used both. `gate_chain` is the
     * EIP-155 id in 2.0 (a JSON number) — [HavenChain.parse] already accepts bare ids, so numbers are
     * read directly. A row missing either the chain or the contract is skipped rather than defaulted —
     * a gate with a guessed chain checks the wrong balance and answers confidently.
     */
    internal fun JSONObject.toTokenGate(): TokenGate? {
        val rawChain = firstChain("gate_chain", "gateChain", "chain") ?: return null
        val token = firstString("gateTokenAddress", "gate_token", "tokenAddress") ?: return null
        val chain = HavenChain.parse(rawChain) ?: return null
        val threshold = firstDouble("gateThreshold", "gate_threshold", "threshold") ?: 1.0
        val standard = firstString("gateTokenStandard", "gate_token_standard")
            ?.let { runCatching { TokenStandard.valueOf(it) }.getOrNull() }
            ?: TokenStandard.ERC20

        return TokenGate(
            // Stored canonically from here on, so downstream comparisons do not have to re-normalise.
            chain = chain.caip2,
            tokenAddress = token,
            threshold = threshold,
            tokenStandard = standard,
        )
    }

    private fun JSONObject.firstString(vararg keys: String): String? = keys
        .asSequence()
        .mapNotNull { key -> optString(key, null)?.takeIf { it.isNotEmpty() } }
        .firstOrNull()

    /** Chain as stored (`gate_chain` EIP id number in 2.0) or gateway-shaped string. */
    private fun JSONObject.firstChain(vararg keys: String): String? = keys
        .asSequence()
        .filter { has(it) && !isNull(it) }
        .mapNotNull { key ->
            when (val value = opt(key)) {
                is Number -> value.toLong().toString()
                is String -> value.takeIf { it.isNotEmpty() }
                else -> null
            }
        }
        .firstOrNull()

    /**
     * Shared MIME enum (ARKIV_FORMAT 2.0.0 §MIME enum — mirrors
     * `haven_cli.services.arkiv_sync.MIME_TO_ENUM` and dapp `lib/mime-enum`).
     * The `mime` attribute stores the enum int; gateway-reshaped responses
     * may already carry a MIME string, which passes through untouched.
     */
    private fun JSONObject.firstMime(vararg keys: String): String? {
        for (key in keys) {
            if (isNull(key)) continue
            when (val value = opt(key)) {
                is Number -> mimeEnumToMime(value.toInt())?.let { return it }
                is String -> value.takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        return null
    }

    private fun mimeEnumToMime(id: Int): String? = when (id) {
        1 -> "video/mp4"
        2 -> "video/webm"
        3 -> "video/quicktime"
        4 -> "audio/mpeg"
        5 -> "audio/wav"
        6 -> "audio/ogg"
        7 -> "image/png"
        8 -> "image/jpeg"
        9 -> "image/webp"
        10 -> "image/gif"
        11 -> "image/svg+xml"
        12 -> "text/plain"
        13 -> "text/markdown"
        14 -> "application/pdf"
        else -> null
    }

    private fun JSONObject.firstDouble(vararg keys: String): Double? = keys
        .asSequence()
        .filter { has(it) && !isNull(it) }
        .map { optDouble(it) }
        .firstOrNull { !it.isNaN() }

    /** Null rather than 0 for absent numbers: "no duration recorded" is not "zero seconds long". */
    private fun JSONObject.firstLong(vararg keys: String): Long? = keys
        .asSequence()
        .filter { has(it) && !isNull(it) }
        .map { optLong(it) }
        .firstOrNull { it > 0 }

    /**
     * The content gate (`gate`) and CID-layer gate (`cid_gate`), any version.
     *
     * The spec allows a JSON object or a string, and the version is decided by content rather than by
     * which key it arrived under: v4 records carry `marketCapTarget` (+ epoch), v3 records carry an
     * epoch (`epoch`/`epochId`), v1 records a nonce.
     * The Arkiv-level marker is `gate_type` (1|3|4 = per-file/per-epoch/per-marketcap).
     * Numeric only — no `gate_version` fallback.
     */
    private fun JSONObject.parseGateMetadata(vararg keys: String): GateMetadata? {
        val obj = keys.asSequence().mapNotNull { key ->
            // The gateway may pass the gate blob through as a JSON string
            // (how writers store it) or as a decoded object (reshaped).
            optJSONObject(key) ?: optString(key, null)?.takeIf { it.isNotEmpty() }?.let {
                runCatching { JSONObject(it) }.getOrNull()
            }
        }.firstOrNull() ?: return null

        val wrappedKey = obj.firstString("wrappedKey", "wrapped_key", "ciphertext")
            ?.toByteArray(Charsets.UTF_8)
            ?: return null

        val marketCapTarget = obj.firstLong("marketCapTarget", "market_cap_target", "marketCapTargetUsd", "market_cap_target_usd")
        val epoch = obj.firstLong("epoch", "epochId", "epoch_id")
        if (marketCapTarget != null && epoch != null) {
            return GateMetadata.V4(
                epochId = epoch,
                marketCapTargetUsd = marketCapTarget,
                wrappedKey = wrappedKey,
                gateReference = obj.firstString("gateReference", "gate_reference", "gate") ?: "",
                // v4 gate JSON carries the pump target token (`tokenAddress`) and its
                // chain (`chain`, Haven-AOL variant) — the buy-link inputs.
                tokenAddress = obj.firstString("tokenAddress", "token_address") ?: "",
                chain = obj.firstChain("chain", "gate_chain", "gateChain") ?: "",
            )
        }
        if (epoch != null) {
            return GateMetadata.V3(
                epochId = epoch,
                wrappedKey = wrappedKey,
                gateReference = obj.firstString("gateReference", "gate_reference", "gate") ?: "",
            )
        }
        return GateMetadata.V1(
            wrappedKey = wrappedKey,
            nonce = obj.firstString("nonce") ?: "",
        )
    }

    // No `parseStringList` any more: the only arrays it read were the PieceRef provider and gateway
    // lists, which are foc's to resolve rather than the index's to declare.
}