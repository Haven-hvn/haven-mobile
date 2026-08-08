package com.reown.appkit.models
data class AppKitMetadata(val name: String, val description: String, val url: String, val icons: List<String>)
data class Wallet(val address: String?, val connectorName: String)
