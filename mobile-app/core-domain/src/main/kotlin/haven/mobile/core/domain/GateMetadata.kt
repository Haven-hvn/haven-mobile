package haven.mobile.core.domain

// Mirrors haven-dapp-main/src/lib/haven-aol-metadata.ts (v1 vs v3 shapes)
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
}