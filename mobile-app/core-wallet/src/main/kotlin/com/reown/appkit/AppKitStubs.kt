package com.reown.appkit
import com.reown.appkit.models.AppKitMetadata
import com.reown.appkit.models.Wallet
class AppKit(private val projectId: String, private val metadata: AppKitMetadata) {
    suspend fun connect(): Wallet = Wallet(address = null, connectorName = "reown")
    suspend fun disconnect() {}
    suspend fun signTypedDataV4(json: String): String = "0x" + "00".repeat(65)
}
