package haven.mobile.core.domain

// Mirrors haven-dapp-main/src/lib/haven-aol-metadata.ts (v1 vs v3 vs v4 shapes)
// Arkiv marker for these: gate_type ATTR_UINT (1=per-file, 3=per-epoch, 4=per-marketcap).
sealed interface GateMetadata {
    data class V1(
        val wrappedKey: ByteArray,
        val nonce: String
    ) : GateMetadata

    data class V3(
        val epochId: Long,
        val wrappedKey: ByteArray,
        val gateReference: String
    ) : GateMetadata

    data class V4(
        val epochId: Long,
        val marketCapTargetUsd: Long,
        val wrappedKey: ByteArray,
        val gateReference: String,
        /** ERC-20 gate token from the v4 gate JSON (`tokenAddress`) — the token to pump. */
        val tokenAddress: String = "",
        /** Chain carrying the gate token (`chain` in the v4 gate JSON, any spelling). */
        val chain: String = "",
    ) : GateMetadata

    /**
     * A VetKD-sealed gate record (`{version, encryptedAesKey, …}`) as real writers emit it —
     * see dapp `isGateMetadata` / `GateMetadataJson`. The sealed key unwraps through the
     * canister plus a device VetKD derivation, which needs the record's own gate fields (the
     * derivation input and the canister request both bind them). Recognized — never mistaken
     * for open content — with the version kept for routing (0 when unparsable).
     */
    data class Sealed(
        val version: Long,
        val encryptedAesKey: String,
        /** Gate record `cid`: derivation input and canister request binding. */
        val cid: String = "",
        /** Gate record `chain` spelling, normalized to the AOL variant at unwrap time. */
        val chain: String = "",
        /** Gate record `tokenAddress`. */
        val tokenAddress: String = "",
        /** Gate record `threshold` verbatim; normalized to a positive integer at unwrap time. */
        val threshold: String = "",
    ) : GateMetadata
}
