package haven.mobile.core.haven.aol

import haven.mobile.core.crypto.AesKeyCache
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.wallet.WalletSession
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

    /**
     * Raw canister call, seammed for tests: production hits the network, tests override with a
     * canned reply. Returns the reply argument bytes, or the rejection as a failure.
     */
    internal open suspend fun callCanister(method: String, candidArg: ByteArray): Result<ByteArray> {
        return try {
            val principal = dev.ic.kotlin.candid.Principal.fromText(config.canisterId)
            val transport = dev.ic.kotlin.agent.OkHttpTransport(config.icHost, okhttp3.OkHttpClient())
            val agent = dev.ic.kotlin.agent.IcAgent(transport)
            when (val reply = agent.call(principal, method, candidArg)) {
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

    override suspend fun attestationPublicKey(): Result<ByteArray> {
        if (config.canisterId.isBlank()) return Result.failure(HavenError.CanisterCallFailed("attestationPublicKey not configured"))
        aesKeyCache.get("attestationPublicKey:${config.canisterId}")?.let { return Result.success(it) }
        return try {
            val principal = dev.ic.kotlin.candid.Principal.fromText(config.canisterId)
            val transport = dev.ic.kotlin.agent.OkHttpTransport(config.icHost, okhttp3.OkHttpClient())
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

/** Bundled canister reply: transport-encrypted VetKey plus the key that verifies it. */
internal data class GateKeys(
    val encryptedKey: ByteArray,
    val verificationKey: ByteArray,
)
