package haven.mobile.core.haven.aol

import haven.mobile.core.domain.GateMetadata
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.TokenGate
import haven.mobile.core.wallet.WalletSession

class GateRequestBuilder {
    fun buildV1Request(
        item: MediaItem,
        nonce: String,
        walletAddress: String
    ): String {
        val gate = item.gate!!
        return """
            {
                "types": {
                    "EIP712Domain": [
                        {"name": "name", "type": "string"},
                        {"name": "version", "type": "string"},
                        {"name": "chainId", "type": "uint256"},
                        {"name": "verifyingContract", "type": "address"}
                    ],
                    "GateRequest": [
                        {"name": "itemId", "type": "string"},
                        {"name": "gate", "type": "Gate"},
                        {"name": "nonce", "type": "uint256"}
                    ],
                    "Gate": [
                        {"name": "chain", "type": "string"},
                        {"name": "tokenAddress", "type": "address"},
                        {"name": "threshold", "type": "uint256"},
                        {"name": "tokenStandard", "type": "string"}
                    ]
                },
                "primaryType": "GateRequest",
                "domain": {
                    "name": "Haven-AOL",
                    "version": "1",
                    "chainId": 1,
                    "verifyingContract": "0x0000000000000000000000000000000000000001"
                },
                "message": {
                    "itemId": "${item.id}",
                    "gate": {
                        "chain": "${gate.chain}",
                        "tokenAddress": "${gate.tokenAddress}",
                        "threshold": ${gate.threshold},
                        "tokenStandard": "${gate.tokenStandard.name}"
                    },
                    "nonce": $nonce
                }
            }
        """.trimIndent()
    }

    fun buildV3Request(
        item: MediaItem,
        nonce: String,
        walletAddress: String
    ): String {
        val gate = item.gate!!
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
                    "chainId": 1,
                    "verifyingContract": "0x0000000000000000000000000000000000000001"
                },
                "message": {
                    "itemId": "${item.id}",
                    "gate": {
                        "chain": "${gate.chain}",
                        "tokenAddress": "${gate.tokenAddress}",
                        "threshold": ${gate.threshold},
                        "tokenStandard": "${gate.tokenStandard.name}"
                    },
                    "nonce": $nonce,
                    "epoch": ${item.createdAtBlock ?: 0}
                }
            }
        """.trimIndent()
    }
}