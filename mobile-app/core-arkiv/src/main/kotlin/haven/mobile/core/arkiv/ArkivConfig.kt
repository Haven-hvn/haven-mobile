package haven.mobile.core.arkiv

/**
 * Arkiv connection settings.
 *
 * [endpointUrl] is the chain's JSON-RPC URL (e.g. Tiramisu), queried directly with `arkiv_query`
 * — there is no REST gateway in front of Arkiv. It may be blank: a fresh clone with no
 * `local.properties` renders from the local Room mirror only, which is a genuinely useful
 * offline mode.
 */
data class ArkivConfig(
    val endpointUrl: String,
    val timeoutMillis: Long = 30_000,
    /** Server page size. 20 matches the web dApp so paging behaviour stays comparable. */
    val pageSize: Int = 20,
) {
    val isConfigured: Boolean get() = endpointUrl.isNotBlank()
}
