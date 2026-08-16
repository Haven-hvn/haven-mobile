package haven.mobile.core.arkiv

/**
 * Arkiv connection settings.
 *
 * [endpointUrl] may be blank — that is the state of a fresh clone with no `local.properties`. The
 * client checks [isConfigured] and returns a typed "not configured" failure rather than building a
 * request against an empty base URL, which previously threw `HavenError.Internal` from deep inside
 * `buildUrl` on every single query.
 */
data class ArkivConfig(
    val endpointUrl: String,
    val timeoutMillis: Long = 30_000,
    /** Server page size. 20 matches the web dApp so paging behaviour stays comparable. */
    val pageSize: Int = 20,
) {
    val isConfigured: Boolean get() = endpointUrl.isNotBlank()
}
