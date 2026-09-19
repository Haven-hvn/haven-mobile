package haven.mobile.core.haven.aol

import haven.mobile.core.domain.GateMetadata
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.TokenGate
import haven.mobile.core.wallet.WalletSession

class GateRequestBuilder {
    /**
     * Canonical v1 `GateRequest` typed data — byte-equivalent to the dapp's
     * `buildGateRequestTypedData`, which the canister verifies against its pinned typehashes
     * (`EIP712Domain(string name,uint256 chainId,address verifyingContract)` — no `version`
     * field — and `GateRequest(address evmAddress,bytes transportPublicKey,uint256 nonce)`).
     * Any drift here signs a digest the canister rejects, so the shape pins in tests.
     *
     * The nonce travels as a quoted decimal string: a 256-bit value loses precision as a JSON
     * number, and wallets encode `uint256` from decimal strings exactly.
     */
    fun buildV1Request(
        evmAddress: String,
        transportPublicKeyHex: String,
        nonceDecimal: String,
    ): String {
        return """
            {
                "types": {
                    "EIP712Domain": [
                        {"name": "name", "type": "string"},
                        {"name": "chainId", "type": "uint256"},
                        {"name": "verifyingContract", "type": "address"}
                    ],
                    "GateRequest": [
                        {"name": "evmAddress", "type": "address"},
                        {"name": "transportPublicKey", "type": "bytes"},
                        {"name": "nonce", "type": "uint256"}
                    ]
                },
                "primaryType": "GateRequest",
                "domain": {
                    "name": "HavenAOL",
                    "chainId": $EIP712_CHAIN_ID,
                    "verifyingContract": "$EIP712_VERIFYING_CONTRACT"
                },
                "message": {
                    "evmAddress": "$evmAddress",
                    "transportPublicKey": "$transportPublicKeyHex",
                    "nonce": "$nonceDecimal"
                }
            }
        """.trimIndent()
    }

    companion object {
        /**
         * EIP-712 domain, dapp defaults (`NEXT_PUBLIC_EIP712_CHAIN_ID=1`, zero verifier). The
         * canister rebuilds the domain separator from the request's values, so these just have
         * to match between the signed data and the Candid call — and matching the dapp keeps
         * one tested path. Shared constants so the two can never drift apart.
         */
        const val EIP712_CHAIN_ID = 1L
        const val EIP712_VERIFYING_CONTRACT = "0x0000000000000000000000000000000000000000"
    }

    fun buildV3Request(
        item: MediaItem,
        nonce: String,
        walletAddress: String,
        chainId: Long
    ): String {
        val gate = item.gate!!
        val threshold = gate.threshold.toLong().toString()
        return """
            {
                "types": {
                    "EIP712Domain": [
                        {"name": "name", "type": "string"},
                        {"name": "version", "type": "string"},
                        {"name": "chainId", "type": "uint256"},
                        {"name": "verifyingContract", "type": "address"}
                    ],
                    "GateRequestV3": [
                        {"name": "itemId", "type": "string"},
                        {"name": "gate", "type": "Gate"},
                        {"name": "nonce", "type": "uint256"},
                        {"name": "epoch", "type": "uint256"}
                    ],
                    "Gate": [
                        {"name": "chain", "type": "string"},
                        {"name": "tokenAddress", "type": "address"},
                        {"name": "threshold", "type": "uint256"},
                        {"name": "tokenStandard", "type": "string"}
                    ]
                },
                "primaryType": "GateRequestV3",
                "domain": {
                    "name": "Haven-AOL",
                    "version": "3",
                    "chainId": $chainId,
                    "verifyingContract": "0x0000000000000000000000000000000000000001"
                },
                "message": {
                    "itemId": "${item.id}",
                    "gate": {
                        "chain": "${gate.chain}",
                        "tokenAddress": "${gate.tokenAddress}",
                        "threshold": $threshold,
                        "tokenStandard": "${gate.tokenStandard.name}"
                    },
                    "nonce": $nonce,
                    "epoch": ${item.createdAtBlock ?: 0}
                }
            }
        """.trimIndent()
    }
}