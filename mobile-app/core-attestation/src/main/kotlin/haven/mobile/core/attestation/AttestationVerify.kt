package haven.mobile.core.attestation

import haven.mobile.core.domain.Attestation
import haven.mobile.core.domain.HavenChain
import haven.mobile.core.domain.MediaItem
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Offline attestation checks, mirroring `haven-dapp` `lib/attestation`.
 *
 * Pure CPU once the caller holds the canister's Ed25519 attestation key: no network here.
 * Two shapes verify here — [Attestation.Single] over the leaf preimage and
 * [Attestation.Merkle] by walking the proof to the signed root — and both then bind to the
 * entity, exactly like the dapp's `verifyAttestation` / `verifyMerkleAttestation` /
 * `attestationMatchesEntity` trio.
 */

/** 30 days in seconds — same TTL policy for single + Merkle (dapp `ATTESTATION_TTL_SECONDS`). */
internal const val ATTESTATION_TTL_SECONDS = 30L * 24 * 60 * 60

/** RFC 6962 domain separation: leaf hashes are prefixed with 0x00. */
private val LEAF_PREFIX = byteArrayOf(0x00)

/** RFC 6962 domain separation: internal-node hashes are prefixed with 0x01. */
private val NODE_PREFIX = byteArrayOf(0x01)

/**
 * Whole doubles format as plain integers — the canister signs `Nat.toText`, never `75.0`
 * or `1e+21`. Matches the dapp's interpolation for every value under 2^53 and the canister
 * beyond it (where JS number formatting would diverge).
 */
internal fun natToText(value: Double): String =
    if (value.isFinite() && value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/**
 * `HAVEN_ATTEST_V1:…` leaf preimage, byte-identical to the dapp/canister form:
 * `HAVEN_ATTEST_V1:{chain}:{tokenAddress}:{threshold}:{evmAddress}:{cidHash}:{timestamp}:{balanceAtCheck}`.
 */
internal fun encodeSinglePreimage(a: Attestation.Single): ByteArray =
    "HAVEN_ATTEST_V1:${a.chain}:${a.tokenAddress}:${natToText(a.threshold)}:${a.evmAddress}:${a.cidHash}:${a.timestamp}:${natToText(a.balanceAtCheck)}"
        .toByteArray(Charsets.UTF_8)

/** Same string form for one CID inside a Merkle batch; the leaf prefix is applied at hashing. */
internal fun encodeLeafPreimage(a: Attestation.Merkle): ByteArray =
    "HAVEN_ATTEST_V1:${a.chain}:${a.tokenAddress}:${natToText(a.threshold)}:${a.evmAddress}:${a.cidHash}:${a.timestamp}:${natToText(a.balanceAtCheck)}"
        .toByteArray(Charsets.UTF_8)

/**
 * `HAVEN_BATCH_ATTEST_V1:…` commitment preimage — the message the canister actually signs.
 * `merkleRoot` is used verbatim (lowercase 64-char hex, no `0x`).
 */
internal fun encodeBatchPreimage(a: Attestation.Merkle): ByteArray =
    "HAVEN_BATCH_ATTEST_V1:${a.chain}:${a.tokenAddress}:${natToText(a.threshold)}:${a.evmAddress}:${a.merkleRoot}:${a.cidCount}:${a.timestamp}:${natToText(a.balanceAtCheck)}"
        .toByteArray(Charsets.UTF_8)

/** TTL + Ed25519 over the leaf preimage. Malformed input fails closed to false. */
internal fun verifySingleOffline(
    attestation: Attestation.Single,
    publicKey: PublicKey,
    nowSeconds: Long,
): Boolean {
    if (nowSeconds - attestation.timestamp > ATTESTATION_TTL_SECONDS) return false
    val sig = hexToBytesOrNull(attestation.signature.removePrefix("0x")) ?: return false
    return ed25519Verifies(publicKey, sig, encodeSinglePreimage(attestation))
}

/**
 * TTL + proof walk to the signed root + Ed25519 over the batch preimage.
 * `side='left'` → `sha256(0x01 ‖ sibling ‖ h)`; `'right'` → `sha256(0x01 ‖ h ‖ sibling)`.
 */
internal fun verifyMerkleOffline(
    attestation: Attestation.Merkle,
    publicKey: PublicKey,
    nowSeconds: Long,
): Boolean {
    if (nowSeconds - attestation.timestamp > ATTESTATION_TTL_SECONDS) return false
    var h = sha256(LEAF_PREFIX + encodeLeafPreimage(attestation))
    for (step in attestation.merkleProof) {
        if (step.side != "left" && step.side != "right") return false
        if (step.hash.length != 64) return false
        val sibling = hexToBytesOrNull(step.hash) ?: return false
        if (sibling.size != 32) return false
        h = if (step.side == "left") sha256(NODE_PREFIX + sibling + h) else sha256(NODE_PREFIX + h + sibling)
    }
    val root = hexToBytesOrNull(attestation.merkleRoot) ?: return false
    if (root.size != 32 || !h.contentEquals(root)) return false
    val sig = hexToBytesOrNull(attestation.rootSignature.removePrefix("0x")) ?: return false
    return ed25519Verifies(publicKey, sig, encodeBatchPreimage(attestation))
}

/**
 * Anti-replay binding, dapp `attestationMatchesEntity` exactly: every field is required and
 * missing values fail closed (optional presence checks would let an attacker elide bindings
 * and still render verified).
 */
internal fun attestationMatchesEntity(attestation: Attestation, item: MediaItem): Boolean {
    val creator = item.creatorAddress
    if (attestation.evmAddress.isBlank() || creator.isNullOrBlank() ||
        !attestation.evmAddress.equals(creator, ignoreCase = true)
    ) {
        return false
    }
    val gate = item.gate ?: return false
    if (gate.tokenAddress.isBlank() ||
        !attestation.tokenAddress.equals(gate.tokenAddress, ignoreCase = true)
    ) {
        return false
    }
    // The canister records the variant name; entities store the EIP id — compare via the table.
    val variant = HavenChain.parse(gate.chain)?.aolVariant ?: return false
    if (!attestation.chain.equals(variant, ignoreCase = true)) return false
    if (attestation.threshold != gate.threshold) return false
    val cidHash = item.cidHash
    if (cidHash.isNullOrBlank() || attestation.cidHash.isBlank() ||
        !attestation.cidHash.equals(cidHash, ignoreCase = true)
    ) {
        return false
    }
    return true
}

/** Strict hex decode — odd length or non-hex fails closed to null. */
internal fun hexToBytesOrNull(hex: String): ByteArray? {
    if (hex.length % 2 != 0) return null
    val out = ByteArray(hex.length / 2)
    for (i in out.indices) {
        val hi = hex[i * 2].digitToIntOrNull(16) ?: return null
        val lo = hex[i * 2 + 1].digitToIntOrNull(16) ?: return null
        out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
}

internal fun sha256(data: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(data)

/**
 * Raw 32-byte Ed25519 key -> JCA [PublicKey], wrapping in X.509 SubjectPublicKeyInfo
 * (OID 1.3.101.112) unless the bytes already look like X.509 (start with 0x30).
 */
internal fun decodeEd25519PublicKey(bytes: ByteArray): PublicKey {
    if (bytes.isNotEmpty() && bytes[0] == 0x30.toByte()) {
        return KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(bytes))
    }
    val header = byteArrayOf(
        0x30.toByte(), 0x2A.toByte(), 0x30.toByte(), 0x05.toByte(), 0x06.toByte(), 0x03.toByte(), 0x2B.toByte(), 0x65.toByte(), 0x70.toByte(), 0x03.toByte(), 0x21.toByte(), 0x00.toByte()
    )
    return KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(header + bytes))
}

internal fun ed25519Verifies(publicKey: PublicKey, signature: ByteArray, message: ByteArray): Boolean =
    try {
        Signature.getInstance("Ed25519").run {
            initVerify(publicKey)
            update(message)
            verify(signature)
        }
    } catch (_: Exception) {
        false
    }
