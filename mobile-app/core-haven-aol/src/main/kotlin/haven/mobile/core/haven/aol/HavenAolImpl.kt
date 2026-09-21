package haven.mobile.core.haven.aol

import haven.mobile.core.crypto.AesKeyCache
import haven.mobile.core.crypto.Keccak256
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class HavenAolImpl @Inject constructor(
    private val config: HavenAolConfig,
    private val walletSession: WalletSession,
    private val aesKeyCache: AesKeyCache,
    private val nonceManager: NonceManager,
    private val gateRequestBuilder: GateRequestBuilder,
    private val vetKdUnwrap: haven.mobile.core.haven.aol.vetkeys.VetKdUnwrap,
    /**
     * One pooled client for every IC call this singleton makes. A fresh client per
     * request re-pays TLS to `ic0.app` on each unlock; the pool amortises it.
     * Bound in [HavenAolDiModule]; the default keeps test subclasses compiling.
     */
    private val icHttp: OkHttpClient = defaultIcHttpClient(),
) : HavenAol {

    override val canisterId: String get() = config.canisterId

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
        val cacheKey = cacheKeyFor(item)
        aesKeyCache.get(cacheKey)?.let { return Result.success(it) }
        val gate = item.gate
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
        // Sealed content routes on its record version: v1 unwraps below, anything else fails
        // closed (v3/v4 need their own canister methods). Only the content layer gates
        // playback — the CID layer seals a locator mobile never needs.
        val sealed = item.encryptionMetadata as? haven.mobile.core.domain.GateMetadata.Sealed
        if (sealed != null) {
            if (sealed.version != 1L) {
                val label = if (sealed.version > 0) " v${sealed.version}" else ""
                return Result.failure(
                    HavenError.UnsupportedGateMetadata(
                        "This item is sealed with Haven-AOL$label — this build unwraps v1 seals only.",
                    ),
                )
            }
            return decryptSealedV1(item, sealed, session, address, cacheKey)
        }
        val legacyGate = gate ?: return Result.failure(HavenError.CanisterCallFailed("No gate for ${item.id}"))
        return decryptLegacy(item, legacyGate, session, address, cacheKey)
    }

    /**
     * VetKD v1 unlock — dapp parity with `decryptContentKey`: transport keypair, canonical
     * EIP-712 signature, `requestDecryptionKey`, then the device unwrap of the bundled
     * `encrypted_key` against the record's sealed AES key.
     *
     * The record's own gate fields bind the derivation and the request (never the attribute
     * gate, which can disagree) — and anything incomplete fails closed before any signing
     * prompt, so the wallet never signs for an unlock that cannot complete.
     */
    private suspend fun decryptSealedV1(
        item: MediaItem,
        sealed: haven.mobile.core.domain.GateMetadata.Sealed,
        session: WalletSession,
        address: String,
        cacheKey: String,
    ): Result<ByteArray> {
        if (!vetKdUnwrap.isAvailable()) {
            return Result.failure(
                HavenError.Internal("Sealed unlock needs the native vetkeys library, which is missing from this build."),
            )
        }
        if (sealed.cid.isBlank() || sealed.chain.isBlank() || sealed.tokenAddress.isBlank()) {
            return Result.failure(
                HavenError.UnsupportedGateMetadata("This item's seal record is incomplete — this build can't unwrap it."),
            )
        }
        val chainVariant = haven.mobile.core.domain.HavenChain.parse(sealed.chain)?.aolVariant
            ?: return Result.failure(
                HavenError.UnsupportedGateMetadata("This item is gated on a network Haven can't check."),
            )
        val thresholdNorm = normalizeSealedThreshold(sealed.threshold)
        val transport = vetKdUnwrap.generateTransportKeypair().getOrElse {
            timber.log.Timber.w(it, "VetKD transport keypair failed")
            return Result.failure(HavenError.Internal("Could not prepare the sealed unlock."))
        }
        val nonce = nonceManager.getNonce(address, config.canisterId)
        val typedData = gateRequestBuilder.buildV1Request(
            evmAddress = address,
            transportPublicKeyHex = "0x" + transport.publicKey.toHex(),
            nonceDecimal = nonce,
        )
        val sig = session.signTypedDataV4(typedData, GateRequestBuilder.EIP712_CHAIN_ID).getOrElse {
            return Result.failure(HavenError.CanisterCallFailed("Signing failed: ${it.message}"))
        }
        val sigBytes = parseWalletSignature(sig) ?: return Result.failure(
            HavenError.InvalidSignatureFormat("The wallet returned an unusable signature."),
        )
        val nonceNat = try {
            java.math.BigInteger(nonce)
        } catch (_: Exception) {
            return Result.failure(HavenError.Internal("Could not prepare the sealed unlock."))
        }
        val record = dev.ic.kotlin.candid.CandidValue.CandidRecord(
            mapOf(
                dev.ic.kotlin.candid.fieldId("chain") to dev.ic.kotlin.candid.CandidValue.CandidVariant(
                    dev.ic.kotlin.candid.fieldId(chainVariant), dev.ic.kotlin.candid.CandidValue.CandidNull,
                ),
                dev.ic.kotlin.candid.fieldId("tokenAddress") to dev.ic.kotlin.candid.CandidValue.CandidText(sealed.tokenAddress),
                dev.ic.kotlin.candid.fieldId("threshold") to dev.ic.kotlin.candid.CandidValue.CandidNat(java.math.BigInteger(thresholdNorm)),
                dev.ic.kotlin.candid.fieldId("cid") to dev.ic.kotlin.candid.CandidValue.CandidText(sealed.cid),
                dev.ic.kotlin.candid.fieldId("evmAddress") to dev.ic.kotlin.candid.CandidValue.CandidText(address),
                dev.ic.kotlin.candid.fieldId("transportPublicKey") to dev.ic.kotlin.candid.CandidValue.CandidBlob(transport.publicKey),
                dev.ic.kotlin.candid.fieldId("nonce") to dev.ic.kotlin.candid.CandidValue.CandidNat(nonceNat),
                dev.ic.kotlin.candid.fieldId("signature") to dev.ic.kotlin.candid.CandidValue.CandidBlob(sigBytes),
                dev.ic.kotlin.candid.fieldId("eip712ChainId") to dev.ic.kotlin.candid.CandidValue.CandidNat(
                    java.math.BigInteger.valueOf(GateRequestBuilder.EIP712_CHAIN_ID),
                ),
                dev.ic.kotlin.candid.fieldId("eip712VerifyingContract") to dev.ic.kotlin.candid.CandidValue.CandidText(
                    GateRequestBuilder.EIP712_VERIFYING_CONTRACT,
                ),
            ),
        )
        return try {
            val replyArg = callCanister("requestDecryptionKey", dev.ic.kotlin.candid.CandidEncoder.encode(listOf(record)))
                .getOrElse { return Result.failure(it) }
            val decoded = try {
                dev.ic.kotlin.candid.CandidDecoder.decode(replyArg)
            } catch (_: Exception) {
                null
            } ?: return Result.failure(HavenError.CanisterCallFailed("Canister returned an unreadable response."))
            val keys = parseGateKeyResult(decoded).getOrElse { return Result.failure(it) }
            val derivation = vetkdDerivationInput(chainVariant, sealed.tokenAddress, thresholdNorm, sealed.cid)
            val aesKey = vetKdUnwrap.unwrapContentKey(
                haven.mobile.core.haven.aol.vetkeys.UnwrapParams(
                    encryptedVetKey = keys.encryptedKey,
                    transportSecret = transport.secretKey,
                    verificationKey = keys.verificationKey,
                    derivationInput = derivation,
                    sealedKeyUtf8 = sealed.encryptedAesKey.toByteArray(Charsets.UTF_8),
                ),
            ).getOrElse {
                timber.log.Timber.w(it, "VetKD unwrap failed")
                return Result.failure(HavenError.PlaybackDecryptFailed("The sealed key would not open (${it.message})."))
            }
            if (aesKey.size != 32) {
                return Result.failure(HavenError.PlaybackDecryptFailed("The sealed key unwrapped to the wrong size."))
            }
            aesKeyCache.put(cacheKey, aesKey)
            Result.success(aesKey)
        } catch (e: Exception) {
            timber.log.Timber.w(e, "Sealed v1 unlock failed")
            Result.failure(HavenError.CanisterCallFailed("Haven couldn't unlock this sealed item."))
        }
    }

    /**
     * Legacy (unwrapped-key) decrypt path: pre-seal V1/V3 gate shapes whose key material the
     * canister returns directly. Untouched by the sealed flow above.
     */
    private suspend fun decryptLegacy(
        item: MediaItem,
        gate: haven.mobile.core.domain.TokenGate,
        session: WalletSession,
        address: String,
        cacheKey: String,
    ): Result<ByteArray> {
        val isV3 = item.cidEncryptionMetadata is haven.mobile.core.domain.GateMetadata.V3 || item.encryptionMetadata is haven.mobile.core.domain.GateMetadata.V3
        val nonce = nonceManager.getNonce(address, config.canisterId)
        val chain = haven.mobile.core.domain.HavenChain.parse(gate.chain)
            ?: return Result.failure(
                HavenError.UnsupportedGateMetadata(
                    "This item is gated on a network Haven can't check.",
                ),
            )
        if (!isV3) {
            // Pre-seal V1 shapes carry their key inline and no writer emits them; the live v1
            // protocol is the sealed flow above. Fail closed instead of signing a request whose
            // reply could never become a valid key.
            return Result.failure(
                HavenError.UnsupportedGateMetadata("This item uses a legacy gate shape this build can't unlock."),
            )
        }
        val json = gateRequestBuilder.buildV3Request(item, nonce, address, chain.chainId)
        val sig = session.signTypedDataV4(json, chain.chainId).getOrElse { return Result.failure(HavenError.CanisterCallFailed("Signing failed: ${it.message}")) }
        // Live VetKD flow via ic-kotlin (parity with haven-aol-decrypt.ts / haven-aol-decrypt-v3.ts):
        // Agent call is attempted; on offline / --offline build no network is hit at compile time,
        // and at runtime an offline host returns a typed failure that callers surface as haven error.
        return try {
            val principal = dev.ic.kotlin.candid.Principal.fromText(config.canisterId)
            val transport = dev.ic.kotlin.agent.OkHttpTransport(config.icHost, icHttp)
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

    /**
     * Raw canister call, seammed for tests: production hits the network, tests override with a
     * canned reply. Returns the reply argument bytes, or the rejection as a failure.
     */
    internal open suspend fun callCanister(method: String, candidArg: ByteArray): Result<ByteArray> {
        return try {
            val principal = dev.ic.kotlin.candid.Principal.fromText(config.canisterId)
            // `requestDecryptionKey` runs EVM-RPC checks then VetKD derivation (10s+), and the
            // v3 sync response waits on execution — OkHttp's 10s read default would abort slow
            // but healthy executions, so the shared client carries sized timeouts for that
            // reality. The overall 5-minute poll timeout in IcCallWithPolling still bounds
            // the whole operation.
            val transport = dev.ic.kotlin.agent.OkHttpTransport(config.icHost, icHttp)
            when (val reply = IcCallWithPolling(transport).call(principal, method, candidArg)) {
                is dev.ic.kotlin.agent.Reply.Replied -> Result.success(reply.arg)
                is dev.ic.kotlin.agent.Reply.Rejected ->
                    Result.failure(HavenError.CanisterCallFailed("Canister rejected $method: ${reply.message}"))
            }
        } catch (e: Exception) {
            timber.log.Timber.w(e, "HavenAol call failed (method=%s)", method)
            Result.failure(HavenError.CanisterCallFailed("Haven couldn't reach the service that unlocks this item."))
        }
    }

    /**
     * `requestDecryptionKey` reply -> bundled keys. Reads `encrypted_key` and
     * `verification_key` by field id (never positionally — record fields sort by hash), and
     * maps `err` variants to the dapp's `mapGateError` messages. Internal so canned replies
     * pin the mapping.
     */
    internal fun parseGateKeyResult(
        decoded: List<dev.ic.kotlin.candid.CandidValue>,
    ): Result<GateKeys> {
        val variant = decoded.firstOrNull() as? dev.ic.kotlin.candid.CandidValue.CandidVariant
            ?: return Result.failure(HavenError.CanisterCallFailed("Canister returned an unexpected response."))
        if (variant.tag == dev.ic.kotlin.candid.fieldId("ok")) {
            val fields = (variant.value as? dev.ic.kotlin.candid.CandidValue.CandidRecord)?.fields
            val encKey = fields?.get(dev.ic.kotlin.candid.fieldId("encrypted_key"))
                as? dev.ic.kotlin.candid.CandidValue.CandidBlob
            val verificationKey = fields?.get(dev.ic.kotlin.candid.fieldId("verification_key"))
                as? dev.ic.kotlin.candid.CandidValue.CandidBlob
            if (encKey == null || verificationKey == null) {
                return Result.failure(HavenError.CanisterCallFailed("Canister returned an incomplete response."))
            }
            return Result.success(GateKeys(encKey.bytes, verificationKey.bytes))
        }
        if (variant.tag == dev.ic.kotlin.candid.fieldId("err")) {
            return Result.failure(mapGateError(variant.value))
        }
        return Result.failure(HavenError.CanisterCallFailed("Canister returned an unexpected response."))
    }

    /**
     * `batchRequestDecryptionKey` reply -> per-cid keys. Field ids, never positions.
     * `err` variants share the single-call mapping; a malformed `keys` entry is
     * skipped rather than failing the group (the missing cid then fails closed
     * per item, exactly like a single call that returned no key).
     */
    internal fun parseBatchKeyResult(
        decoded: List<dev.ic.kotlin.candid.CandidValue>,
    ): Result<BatchKeyBundle> {
        val variant = decoded.firstOrNull() as? dev.ic.kotlin.candid.CandidValue.CandidVariant
            ?: return Result.failure(HavenError.CanisterCallFailed("Canister returned an unexpected response."))
        if (variant.tag == dev.ic.kotlin.candid.fieldId("ok")) {
            val fields = (variant.value as? dev.ic.kotlin.candid.CandidValue.CandidRecord)?.fields
            val keys = fields?.get(dev.ic.kotlin.candid.fieldId("keys"))
                as? dev.ic.kotlin.candid.CandidValue.CandidVec
            val verificationKey = fields?.get(dev.ic.kotlin.candid.fieldId("verification_key"))
                as? dev.ic.kotlin.candid.CandidValue.CandidBlob
            if (keys == null || verificationKey == null) {
                return Result.failure(HavenError.CanisterCallFailed("Canister returned an incomplete response."))
            }
            val entries = keys.items.mapNotNull { entry ->
                val rec = (entry as? dev.ic.kotlin.candid.CandidValue.CandidRecord)?.fields
                    ?: return@mapNotNull null
                val cid = (rec[dev.ic.kotlin.candid.fieldId("cid")]
                    as? dev.ic.kotlin.candid.CandidValue.CandidText)?.value
                    ?: return@mapNotNull null
                val enc = (rec[dev.ic.kotlin.candid.fieldId("encrypted_key")]
                    as? dev.ic.kotlin.candid.CandidValue.CandidBlob)?.bytes
                    ?: return@mapNotNull null
                BatchKeyEntry(cid = cid, encryptedKey = enc)
            }
            return Result.success(BatchKeyBundle(entries, verificationKey.bytes))
        }
        if (variant.tag == dev.ic.kotlin.candid.fieldId("err")) {
            return Result.failure(mapGateError(variant.value))
        }
        return Result.failure(HavenError.CanisterCallFailed("Canister returned an unexpected response."))
    }

    /** Canister `GateError` variant -> reader-facing failure, mirroring dapp `mapGateError`. */
    internal fun mapGateError(value: dev.ic.kotlin.candid.CandidValue): HavenError {
        val err = value as? dev.ic.kotlin.candid.CandidValue.CandidVariant
            ?: return HavenError.CanisterCallFailed("The unlock request was rejected.")
        fun tag(name: String) = err.tag == dev.ic.kotlin.candid.fieldId(name)
        return when {
            tag("InsufficientBalance") -> {
                val details = err.value as? dev.ic.kotlin.candid.CandidValue.CandidRecord
                val required = (details?.fields?.get(dev.ic.kotlin.candid.fieldId("required"))
                    as? dev.ic.kotlin.candid.CandidValue.CandidNat)?.value?.toString() ?: "?"
                val actual = (details?.fields?.get(dev.ic.kotlin.candid.fieldId("actual"))
                    as? dev.ic.kotlin.candid.CandidValue.CandidNat)?.value?.toString() ?: "0"
                HavenError.GateVerificationFailed(
                    "Insufficient token balance. Required: $required, your balance: $actual. " +
                        "Make sure you hold the required tokens on the correct chain.",
                )
            }
            tag("InvalidSignature") -> HavenError.SigningFailed(
                "Invalid signature. Please try signing again with your wallet.",
            )
            tag("NonceAlreadyUsed") -> HavenError.Internal(
                "This decrypt request was already submitted (nonce replay protection). " +
                    "Try playing the video again — you should only need one wallet signature.",
            )
            tag("InvalidAddress") -> HavenError.CanisterCallFailed(
                "Invalid address: ${(err.value as? dev.ic.kotlin.candid.CandidValue.CandidText)?.value ?: "?"}",
            )
            tag("EvmRpcError") -> HavenError.CanisterCallFailed(
                "Balance check failed (${(err.value as? dev.ic.kotlin.candid.CandidValue.CandidText)?.value ?: "RPC error"}). Try again.",
            )
            tag("VetKDError") -> HavenError.CanisterCallFailed(
                "Key service error (${(err.value as? dev.ic.kotlin.candid.CandidValue.CandidText)?.value ?: "unknown"}). Try again.",
            )
            tag("InvalidThreshold") -> HavenError.CanisterCallFailed("The gate threshold is invalid.")
            else -> HavenError.CanisterCallFailed("The unlock request was rejected.")
        }
    }

    /**
     * VetKD derivation input — dapp parity with `computeDerivationInput` (derivation-spec.md):
     * the preimage binds the gate the key derives for, and doubles as the IBE identity at
     * unwrap. Any drift here derives a key that cannot open the sealed record.
     */
    internal fun vetkdDerivationInput(
        chainVariant: String,
        tokenAddress: String,
        thresholdNorm: String,
        cid: String,
    ): ByteArray {
        val preimage = "accessol:$chainVariant:$tokenAddress:$thresholdNorm:$cid"
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(preimage.toByteArray(Charsets.UTF_8))
    }

    /** Record threshold -> positive integer string, mirroring `normalizeDerivationThreshold`. */
    internal fun normalizeSealedThreshold(raw: String): String =
        raw.trim().toLongOrNull()?.coerceAtLeast(1L)?.toString() ?: "1"

    /**
     * Batch commitment for `batchRequestDecryptionKey`: `keccak256` over the
     * concatenated per-cid derivation inputs, in submitted order — exactly the
     * canister's `eip712BatchGateStructHash` commitment. Same inputs the single
     * path derives with, so one formula serves signing and unwrapping.
     */
    internal fun batchCidsCommitmentHex(
        chainVariant: String,
        tokenAddress: String,
        thresholdNorm: String,
        cids: List<String>,
    ): String {
        require(cids.isNotEmpty() && cids.size <= MAX_BATCH_CIDS) {
            "batch needs 1..$MAX_BATCH_CIDS cids"
        }
        val packed = ByteArray(32 * cids.size)
        cids.forEachIndexed { index, cid ->
            vetkdDerivationInput(chainVariant, tokenAddress, thresholdNorm, cid)
                .copyInto(packed, index * 32)
        }
        return "0x" + Keccak256.hashHex(packed)
    }

    /**
     * True v1 batch unlock: one transport keypair, one nonce, ONE wallet signature
     * and ONE canister call (single EVM check) for every cid in the group, then a
     * per-cid local unwrap. Fails closed per item — a missing key or bad unwrap
     * for one cid never poisons its neighbours.
     */
    private suspend fun decryptBatchSealedV1(
        items: List<MediaItem>,
        key: V1BatchKey,
        session: WalletSession,
        address: String,
    ): List<Result<ByteArray>> {
        fun allFailed(throwable: Throwable): List<Result<ByteArray>> =
            items.map { Result.failure<ByteArray>(throwable) }
        if (!vetKdUnwrap.isAvailable()) {
            return allFailed(
                HavenError.Internal("Sealed unlock needs the native vetkeys library, which is missing from this build."),
            )
        }
        val transport = vetKdUnwrap.generateTransportKeypair().getOrElse {
            timber.log.Timber.w(it, "VetKD transport keypair failed")
            return allFailed(HavenError.Internal("Could not prepare the sealed unlock."))
        }
        val nonce = nonceManager.getNonce(address, config.canisterId)
        val cids = items.map { (it.encryptionMetadata as haven.mobile.core.domain.GateMetadata.Sealed).cid }
        val commitment = batchCidsCommitmentHex(key.chainVariant, key.tokenAddress, key.thresholdNorm, cids)
        // The batch struct hashes the transport key itself (`bytes32 transportKeyHash`),
        // unlike the single request where the wallet hashes the dynamic `bytes` field —
        // so the raw key goes in the Candid call but only its keccak goes in the typed data.
        val typedData = gateRequestBuilder.buildBatchV1Request(
            evmAddress = address,
            transportKeyHashHex = "0x" + Keccak256.hashHex(transport.publicKey),
            cidsCommitmentHex = commitment,
            nonceDecimal = nonce,
        )
        val sig = session.signTypedDataV4(typedData, GateRequestBuilder.EIP712_CHAIN_ID).getOrElse {
            return allFailed(HavenError.CanisterCallFailed("Signing failed: ${it.message}"))
        }
        val sigBytes = parseWalletSignature(sig) ?: return allFailed(
            HavenError.InvalidSignatureFormat("The wallet returned an unusable signature."),
        )
        val nonceNat = try {
            java.math.BigInteger(nonce)
        } catch (_: Exception) {
            return allFailed(HavenError.Internal("Could not prepare the sealed unlock."))
        }
        val record = dev.ic.kotlin.candid.CandidValue.CandidRecord(
            mapOf(
                dev.ic.kotlin.candid.fieldId("chain") to dev.ic.kotlin.candid.CandidValue.CandidVariant(
                    dev.ic.kotlin.candid.fieldId(key.chainVariant), dev.ic.kotlin.candid.CandidValue.CandidNull,
                ),
                dev.ic.kotlin.candid.fieldId("tokenAddress") to dev.ic.kotlin.candid.CandidValue.CandidText(key.tokenAddress),
                dev.ic.kotlin.candid.fieldId("threshold") to dev.ic.kotlin.candid.CandidValue.CandidNat(java.math.BigInteger(key.thresholdNorm)),
                dev.ic.kotlin.candid.fieldId("cids") to dev.ic.kotlin.candid.CandidValue.CandidVec(
                    cids.map { dev.ic.kotlin.candid.CandidValue.CandidText(it) },
                ),
                dev.ic.kotlin.candid.fieldId("evmAddress") to dev.ic.kotlin.candid.CandidValue.CandidText(address),
                dev.ic.kotlin.candid.fieldId("transportPublicKey") to dev.ic.kotlin.candid.CandidValue.CandidBlob(transport.publicKey),
                dev.ic.kotlin.candid.fieldId("nonce") to dev.ic.kotlin.candid.CandidValue.CandidNat(nonceNat),
                dev.ic.kotlin.candid.fieldId("signature") to dev.ic.kotlin.candid.CandidValue.CandidBlob(sigBytes),
                dev.ic.kotlin.candid.fieldId("eip712ChainId") to dev.ic.kotlin.candid.CandidValue.CandidNat(
                    java.math.BigInteger.valueOf(GateRequestBuilder.EIP712_CHAIN_ID),
                ),
                dev.ic.kotlin.candid.fieldId("eip712VerifyingContract") to dev.ic.kotlin.candid.CandidValue.CandidText(
                    GateRequestBuilder.EIP712_VERIFYING_CONTRACT,
                ),
            ),
        )
        val replyArg = try {
            callCanister("batchRequestDecryptionKey", dev.ic.kotlin.candid.CandidEncoder.encode(listOf(record)))
                .getOrElse { return allFailed(it) }
        } catch (e: Exception) {
            timber.log.Timber.w(e, "Sealed v1 batch unlock failed")
            return allFailed(HavenError.CanisterCallFailed("Haven couldn't unlock these sealed items."))
        }
        val decoded = try {
            dev.ic.kotlin.candid.CandidDecoder.decode(replyArg)
        } catch (_: Exception) {
            null
        } ?: return allFailed(HavenError.CanisterCallFailed("Canister returned an unreadable response."))
        val batch = parseBatchKeyResult(decoded).getOrElse { return allFailed(it) }
        val byCid = batch.entries.associate { it.cid to it.encryptedKey }
        return items.map { item ->
            val sealed = item.encryptionMetadata as haven.mobile.core.domain.GateMetadata.Sealed
            val encryptedVetKey = byCid[sealed.cid]
                ?: return@map Result.failure<ByteArray>(
                    HavenError.CanisterCallFailed("Canister returned no key for this item."),
                )
            val derivation = vetkdDerivationInput(key.chainVariant, key.tokenAddress, key.thresholdNorm, sealed.cid)
            val aesKey = vetKdUnwrap.unwrapContentKey(
                haven.mobile.core.haven.aol.vetkeys.UnwrapParams(
                    encryptedVetKey = encryptedVetKey,
                    transportSecret = transport.secretKey,
                    verificationKey = batch.verificationKey,
                    derivationInput = derivation,
                    sealedKeyUtf8 = sealed.encryptedAesKey.toByteArray(Charsets.UTF_8),
                ),
            ).getOrElse {
                timber.log.Timber.w(it, "VetKD batch unwrap failed")
                return@map Result.failure<ByteArray>(HavenError.PlaybackDecryptFailed("The sealed key would not open (${it.message})."))
            }
            if (aesKey.size != 32) {
                return@map Result.failure<ByteArray>(HavenError.PlaybackDecryptFailed("The sealed key unwrapped to the wrong size."))
            }
            aesKeyCache.put(cacheKeyFor(item), aesKey)
            Result.success(aesKey)
        }
    }

    /**
     * Wallet signature -> 65 bytes, mirroring `parseSignatureHex`. Anything else fails closed
     * before the Candid call rather than encoding a short signature.
     */
    internal fun parseWalletSignature(sig: String): ByteArray? {
        val hex = sig.removePrefix("0x")
        if (hex.length != 130 || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return ByteArray(65) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    override suspend fun verificationKey(): Result<ByteArray> {
        if (config.canisterId.isBlank()) return Result.failure(HavenError.CanisterCallFailed("verificationKey not configured"))
        aesKeyCache.get("verificationKey:${config.canisterId}")?.let { return Result.success(it) }
        return try {
            val principal = dev.ic.kotlin.candid.Principal.fromText(config.canisterId)
            val transport = dev.ic.kotlin.agent.OkHttpTransport(config.icHost, icHttp)
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

    override suspend fun attestationPublicKey(): Result<ByteArray> {
        if (config.canisterId.isBlank()) return Result.failure(HavenError.CanisterCallFailed("attestationPublicKey not configured"))
        aesKeyCache.get("attestationPublicKey:${config.canisterId}")?.let { return Result.success(it) }
        return try {
            val principal = dev.ic.kotlin.candid.Principal.fromText(config.canisterId)
            val transport = dev.ic.kotlin.agent.OkHttpTransport(config.icHost, icHttp)
            val agent = dev.ic.kotlin.agent.IcAgent(transport)
            val arg = dev.ic.kotlin.candid.CandidEncoder.encode(emptyList())
            val replyBytes = agent.query(principal, "getAttestationPublicKey", arg)
            val decoded = dev.ic.kotlin.candid.CandidDecoder.decode(replyBytes)
            val first = decoded.firstOrNull()
            val keyBytes: ByteArray? = when (first) {
                is dev.ic.kotlin.candid.CandidValue.CandidBlob -> first.bytes
                is dev.ic.kotlin.candid.CandidValue.CandidVec -> (first.items.firstOrNull() as? dev.ic.kotlin.candid.CandidValue.CandidBlob)?.bytes
                else -> null
            }
            if (keyBytes != null) {
                aesKeyCache.put("attestationPublicKey:${config.canisterId}", keyBytes)
                Result.success(keyBytes)
            } else {
                Result.failure(HavenError.CanisterCallFailed("attestationPublicKey: unexpected Candid shape"))
            }
        } catch (e: Exception) {
            Result.failure(HavenError.CanisterCallFailed("attestationPublicKey query failed for ${config.canisterId}: ${e.message}"))
        }
    }

    override suspend fun hasCachedKey(item: MediaItem): Boolean =
        aesKeyCache.getSuspend(cacheKeyFor(item)) != null

    /** Session cache identity for an item's key — one formula for lookup and store. */
    private fun cacheKeyFor(item: MediaItem): String =
        "${item.id}:${item.gate?.tokenAddress}:${item.encryptionMetadata?.let { it::class.simpleName } ?: "v1"}"

    /**
     * Batch unlock: sealed-v1 items sharing a gate go out as ONE
     * `batchRequestDecryptionKey` (one signature, one EVM check, per-cid keys
     * back); V3 epoch groups keep one single call whose key is shared; the rest
     * decrypt per item. Groups run concurrently, order is preserved, one item's
     * failure never cancels the rest (`supervisorScope`) — the batch UI states
     * the signature count up front.
     */
    override suspend fun decryptAll(
        items: List<MediaItem>,
        session: WalletSession,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ): List<Result<ByteArray>> {
        if (items.isEmpty()) return emptyList()
        return try {
            decryptAllInner(items, session, onProgress)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The batch screen reports per-item Results — an unexpected throw here
            // used to escape as an app crash with no error screen at all.
            timber.log.Timber.e(e, "decryptAll failed closed for ${items.size} items")
            items.map { Result.failure<ByteArray>(HavenError.Internal("Batch unlock hit an unexpected error.")) }
        }
    }

    private suspend fun decryptAllInner(
        items: List<MediaItem>,
        session: WalletSession,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ): List<Result<ByteArray>> {
        // v3 batch: group by (epochId + gateReference) — one canister call per epoch, as in haven-aol-decrypt-v3.ts
        // Correct grouping is epochId+gateReference, not full V3 object (which includes wrappedKey per-item)
        // See HavenAolBatchGroupingTest and planning/mobile-v1-tasking/sprint-2…/2.4-core-haven-aol-v3-batch.md
        data class V3BatchKey(val epochId: Long, val gateReference: String)
        val keyForV3: (MediaItem) -> V3BatchKey? = { item ->
            val v3 = item.cidEncryptionMetadata as? haven.mobile.core.domain.GateMetadata.V3
                ?: item.encryptionMetadata as? haven.mobile.core.domain.GateMetadata.V3
            v3?.let { V3BatchKey(it.epochId, it.gateReference) }
        }
        // Sealed-v1 items with a complete record share one true batch call per gate.
        // Anything failing the preconditions falls through to single decrypt, which
        // fails it closed before signing — batching never signs for what it can't do.
        val v1Groups = mutableMapOf<V1BatchKey, MutableList<Int>>()
        val rest = mutableListOf<Int>()
        items.forEachIndexed { idx, item ->
            val key = v1BatchKeyOrNull(item)
            if (key != null) v1Groups.getOrPut(key) { mutableListOf() }.add(idx)
            else rest.add(idx)
        }
        // v3 grouping over the remainder only; null key means single decrypt.
        val v3Groups = mutableMapOf<V3BatchKey?, MutableList<Int>>()
        rest.forEach { idx -> v3Groups.getOrPut(keyForV3(items[idx])) { mutableListOf() }.add(idx) }
        val address = session.address.value
        val completed = java.util.concurrent.atomic.AtomicInteger(0)
        suspend fun finish(pairs: List<Pair<Int, Result<ByteArray>>>): List<Pair<Int, Result<ByteArray>>> {
            onProgress(completed.addAndGet(pairs.size), items.size)
            return pairs
        }
        return supervisorScope {
            val jobs = mutableListOf<kotlinx.coroutines.Deferred<List<Pair<Int, Result<ByteArray>>>>>()
            // True v1 batches, chunked at the canister cap — one signature per chunk.
            v1Groups.forEach { (key, indices) ->
                indices.chunked(MAX_BATCH_CIDS).forEach { chunk ->
                    jobs += async {
                        val pairs = if (address == null) {
                            chunk.map { idx ->
                                idx to Result.failure<ByteArray>(
                                    HavenError.WalletNotConnected("No wallet connected"),
                                )
                            }
                        } else {
                            val results = decryptBatchSealedV1(chunk.map { items[it] }, key, session, address)
                            chunk.zip(results)
                        }
                        finish(pairs)
                    }
                }
            }
            v3Groups.forEach { (batchKey, indices) ->
                jobs += async {
                    val pairs = if (batchKey != null) {
                        // V3 epoch group — one GateRequestV3 unlocks whole epoch (FR-ACL-1)
                        val single = decrypt(items[indices.first()], session)
                        if (single.isSuccess) {
                            val key = single.getOrNull()!!
                            // Cache epoch key for subsequent calls within session (FR-ACL-2)
                            aesKeyCache.put("v3:${batchKey.epochId}:${batchKey.gateReference}", key)
                        }
                        // Reuse same key/error for all cids in this epoch (response shapes cids, derivation does not)
                        indices.map { idx -> idx to single }
                    } else {
                        // Anything else — per-item decrypt preserves existing parity.
                        indices.map { idx -> async { idx to decrypt(items[idx], session) } }.awaitAll()
                    }
                    finish(pairs)
                }
            }
            jobs.awaitAll().flatten().sortedBy { it.first }.map { it.second }
        }
    }

    /**
     * Sealed-v1 batch precondition, mirroring `decryptSealedV1`'s fail-closed
     * gates: v1, complete record, known chain. Null routes to single decrypt,
     * which fails the item closed before any signing prompt.
     */
    private fun v1BatchKeyOrNull(item: MediaItem): V1BatchKey? {
        val sealed = item.encryptionMetadata as? haven.mobile.core.domain.GateMetadata.Sealed
            ?: return null
        if (sealed.version != 1L) return null
        if (sealed.cid.isBlank() || sealed.chain.isBlank() || sealed.tokenAddress.isBlank()) return null
        val chainVariant = haven.mobile.core.domain.HavenChain.parse(sealed.chain)?.aolVariant
            ?: return null
        return V1BatchKey(
            chainVariant = chainVariant,
            tokenAddress = sealed.tokenAddress,
            thresholdNorm = normalizeSealedThreshold(sealed.threshold),
        )
    }

    override suspend fun clearFor(walletAddress: String) {
        aesKeyCache.clearAll()
        // NonceManager is per-canister; entry clears nothing — real impl would iterate keys
    }
}

/** Bundled canister reply: transport-encrypted VetKey plus the key that verifies it. */
internal data class GateKeys(
    val encryptedKey: ByteArray,
    val verificationKey: ByteArray,
)

/** One entry of a `BatchGateResult.ok.keys` vector: which cid this key opens. */
internal data class BatchKeyEntry(
    val cid: String,
    val encryptedKey: ByteArray,
)

/** A parsed batch reply: per-cid keys plus the shared verification key. */
internal data class BatchKeyBundle(
    val entries: List<BatchKeyEntry>,
    val verificationKey: ByteArray,
)

/**
 * One sealed-v1 batch group: every item derives under the same gate, so one
 * signature and one EVM check unlocks them all. Thresholds are normalized
 * before grouping — records disagreeing only in spelling share a call.
 */
internal data class V1BatchKey(
    val chainVariant: String,
    val tokenAddress: String,
    val thresholdNorm: String,
)

/** Canister cap on `BatchGateRequest.cids` — larger groups chunk. */
internal const val MAX_BATCH_CIDS = 20

/**
 * Pooled IC client: `requestDecryptionKey` needs 90s reads (EVM-RPC + VetKD),
 * and one shared pool amortises TLS across unlocks, key fetches and polls.
 */
internal fun defaultIcHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(90, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()
