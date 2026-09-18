package haven.mobile.core.attestation

import haven.mobile.core.domain.Attestation
import haven.mobile.core.domain.MediaItem

interface AttestationVerifier {
    /**
     * Dapp `verifyFeed` for one item: signature (+ TTL, + Merkle walk for batch proofs) and
     * entity binding, offline after the one-time attestation-key fetch.
     */
    suspend fun verify(attestation: Attestation, item: MediaItem, nowSeconds: Long = System.currentTimeMillis() / 1000): Result<Unit>
}
