package haven.mobile.core.domain

import kotlinx.datetime.Instant

// Mirrors haven-dapp-main/src/lib/attestation.ts
data class Attestation(
    val subject: String,
    val signature: ByteArray,
    val signerKeyId: String,
    val merkleProof: List<ByteArray>?,
    val issuedAt: Instant
)