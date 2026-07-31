package haven.mobile.core.arkiv

data class ArkivConfig(
    val endpointUrl: String,
    val timeoutMillis: Long = 30_000,
)