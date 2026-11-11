package haven.mobile.core.arkiv

import cloud.filecoin.foc.cache.FocChain

/**
 * Arkiv connection settings.
 *
 * [endpointUrl] is the chain's JSON-RPC URL (Tiramisu by default), queried directly with
 * `arkiv_query` — there is no REST gateway in front of Arkiv. Production DI always supplies
 * a value (see `ArkivDiModule`); a blank URL only ever means "explicitly unconfigured",
 * never "offline" — offline is no connectivity at call time.
 *
 * [focChain] is the Filecoin network pieces from this Arkiv endpoint live on. It is the
 * app-wide default for retrieval; a record's gate can override it per record (see
 * `ArkivClientImpl.resolveFocChain`). Left null, it is inferred from [endpointUrl]:
 * a testnet endpoint (Tiramisu) means calibration, anything else mainnet.
 */
data class ArkivConfig(
    val endpointUrl: String,
    val timeoutMillis: Long = 30_000,
    /** Server page size. 20 matches the web dApp so paging behaviour stays comparable. */
    val pageSize: Int = 20,
    val focChain: FocChain? = null,
) {
    val isConfigured: Boolean get() = endpointUrl.isNotBlank()

    /** The Filecoin network to scope retrieval to, inferred from the endpoint when unset. */
    val resolvedFocChain: FocChain
        get() = focChain
            ?: if (endpointUrl.contains("testnet", ignoreCase = true)) FocChain.CALIBRATION else FocChain.MAINNET
}
