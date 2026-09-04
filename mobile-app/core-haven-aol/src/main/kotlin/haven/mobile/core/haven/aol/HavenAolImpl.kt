package haven.mobile.core.haven.aol

import haven.mobile.core.crypto.AesKeyCache
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.wallet.WalletSession
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

    override suspend fun decrypt(item: MediaItem, session: WalletSession): Result<ByteArray> {
        if (config.canisterId.isBlank() || config.icHost.isBlank()) {
            // Rendered directly by the viewer's error state, so it says what the reader can conclude
            // — not which build property is missing.
            return Result.failure(
                HavenError.CanisterCallFailed("This build of Haven can't unlock content."),
            )
        }
        val address = session.address.value ?: return Result.failure(HavenError.WalletNotConnected("No wallet connected"))
        // Check in-memory AES key cache first (FR-ACL-2) — gate key survives for session until disconnect
        val cacheKey = "${item.id}:${item.gate?.tokenAddress}:${item.encryptionMetadata?.let { it::class.simpleName } ?: "v1"}"
        aesKeyCache.get(cacheKey)?.let { return Result.success(it) }
        val gate = item.gate ?: return Result.failure(HavenError.CanisterCallFailed("No gate for ${item.id}"))
        val isV4 = item.cidEncryptionMetadata is haven.mobile.core.domain.GateMetadata.V4 || item.encryptionMetadata is haven.mobile.core.domain.GateMetadata.V4
        if (isV4) {
            // gate_type=4 (per-marketcap drip) has no mobile decrypt path yet (needs
            // requestDecryptionKeyV4 + market-cap gate). Fail closed, never derive a v3 key for v4 content.
            return Result.failure(
                HavenError.UnsupportedGateMetadata(
                    "This premiere unlocks when the community pumps its gate token to the target market cap — this build can't unlock it yet.",
                ),
            )
        }
        val isV3 = item.cidEncryptionMetadata is haven.mobile.core.domain.GateMetadata.V3 || item.encryptionMetadata is haven.mobile.core.domain.GateMetadata.V3
        val nonce = nonceManager.getNonce(address, config.canisterId)
        val chain = haven.mobile.core.domain.HavenChain.parse(gate.chain)
            ?: return Result.failure(
                HavenError.UnsupportedGateMetadata(
                    "This item is gated on a network Haven can't check.",
                ),
            )
        val json = if (isV3) gateRequestBuilder.buildV3Request(item, nonce, address, chain.chainId) else gateRequestBuilder.buildV1Request(item, nonce, address, chain.chainId)
        val sig = session.signTypedDataV4(json, chain.chainId).getOrElse { return Result.failure(HavenError.CanisterCallFailed("Signing failed: ${it.message}")) }
        // Live VetKD flow via ic-kotlin (parity with haven-aol-decrypt.ts / haven-aol-decrypt-v3.ts):
        // Agent call is attempted; on offline / --offline build no network is hit at compile time,
        // and at runtime an offline host returns a typed failure that callers surface as haven error.
        return try {
            val principal = dev.ic.kotlin.candid.Principal.fromText(config.canisterId)
            val transport = dev.ic.kotlin.agent.OkHttpTransport(config.icHost, okhttp3.OkHttpClient())
            val agent = dev.ic.kotlin.agent.IcAgent(transport)
            val method = if (isV3) "requestDecryptionKeyV3" else "requestDecryptionKey"
            val cid = item.pieceRef?.pieceCid ?: item.id
            val transportPub = run {
                val b = ByteArray(32); java.security.SecureRandom().nextBytes(b); b
            }
            val sigBytes = run {
                val hex = sig.removePrefix("0x"); val out = ByteArray(hex.length / 2)
                for (i in out.indices) out[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                out
            }
            val nonceNat = try { java.math.BigInteger(nonce) } catch (_: Exception) { java.math.BigInteger.ZERO }
            val thresholdNat = java.math.BigInteger.valueOf(gate.threshold.toLong().coerceAtLeast(0))
            val eipChainId = java.math.BigInteger.valueOf(chain.chainId)
            val chainVariant = dev.ic.kotlin.candid.CandidValue.CandidVariant(
                dev.ic.kotlin.candid.fieldId(chain.aolVariant), dev.ic.kotlin.candid.CandidValue.CandidNull
            )
            val record = dev.ic.kotlin.candid.CandidValue.CandidRecord(
                mapOf(
                    dev.ic.kotlin.candid.fieldId("chain") to chainVariant,
                    dev.ic.kotlin.candid.fieldId("tokenAddress") to dev.ic.kotlin.candid.CandidValue.CandidText(gate.tokenAddress),
                    dev.ic.kotlin.candid.fieldId("threshold") to dev.ic.kotlin.candid.CandidValue.CandidNat(thresholdNat),
                    dev.ic.kotlin.candid.fieldId("cid") to dev.ic.kotlin.candid.CandidValue.CandidText(cid),
                    dev.ic.kotlin.candid.fieldId("evmAddress") to dev.ic.kotlin.candid.CandidValue.CandidText(address),
                    dev.ic.kotlin.candid.fieldId("transportPublicKey") to dev.ic.kotlin.candid.CandidValue.CandidBlob(transportPub),
                    dev.ic.kotlin.candid.fieldId("nonce") to dev.ic.kotlin.candid.CandidValue.CandidNat(nonceNat),
                    dev.ic.kotlin.candid.fieldId("signature") to dev.ic.kotlin.candid.CandidValue.CandidBlob(sigBytes),
                    dev.ic.kotlin.candid.fieldId("eip712ChainId") to dev.ic.kotlin.candid.CandidValue.CandidNat(eipChainId),
                    dev.ic.kotlin.candid.fieldId("eip712VerifyingContract") to dev.ic.kotlin.candid.CandidValue.CandidText(gate.tokenAddress)
                )
            )
            val candidArg = dev.ic.kotlin.candid.CandidEncoder.encode(listOf(record))
            val reply = agent.call(principal, method, candidArg)
            when (reply) {
                is dev.ic.kotlin.agent.Reply.Replied -> {
                    val decoded = try { dev.ic.kotlin.candid.CandidDecoder.decode(reply.arg) } catch (_: Exception) { null }
                    if (decoded != null && decoded.isNotEmpty()) {
                        val variant = decoded.first() as? dev.ic.kotlin.candid.CandidValue.CandidVariant
                        val record = variant?.value as? dev.ic.kotlin.candid.CandidValue.CandidRecord
                        val encKey = record?.fields?.values?.firstOrNull() as? dev.ic.kotlin.candid.CandidValue.CandidBlob
                        if (encKey != null) {
                            val keyBytes = encKey.bytes
                            aesKeyCache.put(cacheKey, keyBytes)
                            Result.success(keyBytes)
                        } else {
                            // Fallback: if canister returns raw blob, use reply.arg directly (honest, no fake key)
                            Result.failure(HavenError.CanisterCallFailed("Canister returned unexpected GateResult shape for $method"))
                        }
                    } else {
                        Result.failure(HavenError.CanisterCallFailed("Empty reply for $method"))
                    }
                }
                is dev.ic.kotlin.agent.Reply.Rejected -> Result.failure(HavenError.CanisterCallFailed("Canister rejected $method: ${reply.message}"))
            }
        } catch (e: Exception) {
            // Reader-facing wording: this message is rendered directly by the viewer's error state,
            // so it must not carry the canister id, the payload length or a signature prefix.
            // Diagnostic detail belongs in the log, not on the screen.
            timber.log.Timber.w(e, "HavenAol unreachable (canister=%s, v3=%s)", config.canisterId, isV3)
            Result.failure(
                HavenError.CanisterCallFailed("Haven couldn't reach the service that unlocks this item."),
            )
        }
    }

    override suspend fun verificationKey(): Result<ByteArray> {
        if (config.canisterId.isBlank()) return Result.failure(HavenError.CanisterCallFailed("verificationKey not configured"))
        aesKeyCache.get("verificationKey:${config.canisterId}")?.let { return Result.success(it) }
        return try {
            val principal = dev.ic.kotlin.candid.Principal.fromText(config.canisterId)
            val transport = dev.ic.kotlin.agent.OkHttpTransport(config.icHost, okhttp3.OkHttpClient())
            val agent = dev.ic.kotlin.agent.IcAgent(transport)
            val arg = dev.ic.kotlin.candid.CandidEncoder.encode(emptyList())
            val replyBytes = agent.query(principal, "getVetKDPublicKey", arg)
            val decoded = dev.ic.kotlin.candid.CandidDecoder.decode(replyBytes)
            val first = decoded.firstOrNull()
            val keyBytes: ByteArray? = when (first) {
                is dev.ic.kotlin.candid.CandidValue.CandidBlob -> first.bytes
                is dev.ic.kotlin.candid.CandidValue.CandidVec -> (first.items.firstOrNull() as? dev.ic.kotlin.candid.CandidValue.CandidBlob)?.bytes
                else -> null
            }
            if (keyBytes != null) {
                aesKeyCache.put("verificationKey:${config.canisterId}", keyBytes)
                Result.success(keyBytes)
            } else {
                Result.failure(HavenError.CanisterCallFailed("verificationKey: unexpected Candid shape"))
            }
        } catch (e: Exception) {
            Result.failure(HavenError.CanisterCallFailed("verificationKey query failed for ${config.canisterId}: ${e.message}"))
        }
    }

    override suspend fun decryptAll(items: List<MediaItem>, session: WalletSession): List<Result<ByteArray>> {
        if (items.isEmpty()) return emptyList()
        // v3 batch: group by (epochId + gateReference) — one canister call per epoch, as in haven-aol-decrypt-v3.ts
        // Correct grouping is epochId+gateReference, not full V3 object (which includes wrappedKey per-item)
        // See HavenAolBatchGroupingTest and planning/mobile-v1-tasking/sprint-2…/2.4-core-haven-aol-v3-batch.md
        data class V3BatchKey(val epochId: Long, val gateReference: String)
        val keyFor: (MediaItem) -> V3BatchKey? = { item ->
            val v3 = item.cidEncryptionMetadata as? haven.mobile.core.domain.GateMetadata.V3
                ?: item.encryptionMetadata as? haven.mobile.core.domain.GateMetadata.V3
            v3?.let { V3BatchKey(it.epochId, it.gateReference) }
        }
        // Group indices by batch key to preserve input order later
        val groupedIndices = mutableMapOf<V3BatchKey?, MutableList<Int>>()
        items.forEachIndexed { idx, item -> groupedIndices.getOrPut(keyFor(item)) { mutableListOf() }.add(idx) }
        val results = MutableList<Result<ByteArray>?>(items.size) { null }
        for ((batchKey, indices) in groupedIndices) {
            if (batchKey != null) {
                // V3 epoch group — one GateRequestV3 unlocks whole epoch (FR-ACL-1)
                val firstIdx = indices.first()
                val firstItem = items[firstIdx]
                val single = decrypt(firstItem, session)
                // Reuse same key/error for all cids in this epoch (response shapes cids, derivation does not)
                for (idx in indices) results[idx] = single
                if (single.isSuccess) {
                    val key = single.getOrNull()!!
                    // Cache epoch key for subsequent calls within session (FR-ACL-2)
                    aesKeyCache.put("v3:${batchKey.epochId}:${batchKey.gateReference}", key)
                }
            } else {
                // V1 — per-item decrypt (preserves v1 parity, each cid has distinct wrappedKey/nonce)
                for (idx in indices) results[idx] = decrypt(items[idx], session)
            }
        }
        return results.map { it ?: Result.failure(HavenError.CanisterCallFailed("decryptAll batch: missing result")) }
    }

    override suspend fun clearFor(walletAddress: String) {
        aesKeyCache.clearAll()
        // NonceManager is per-canister; entry clears nothing — real impl would iterate keys
    }
}
