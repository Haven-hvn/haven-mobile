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
}
