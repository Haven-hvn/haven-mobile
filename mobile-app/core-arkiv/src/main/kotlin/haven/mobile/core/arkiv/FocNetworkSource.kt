package haven.mobile.core.arkiv

import cloud.filecoin.foc.cache.FocChain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The user's chosen Filecoin Onchain Cloud network, as the arkiv client sees it.
 *
 * Declared here (not in `:core-cache-mirror`) because that module already depends on this one —
 * the same inversion `:core-cache` uses for `CacheSettingsSource`. `:core-cache-mirror` supplies
 * the implementation; a manual callsite can pass [Automatic].
 *
 * `null` means automatic: keep the writer-independent inference (gate chain per record, then the
 * Arkiv endpoint — testnet means calibration, anything else mainnet). A non-null value forces that
 * network for every record, which is what a user pointing the app at a different network expects.
 */
interface FocNetworkSource {
    val focNetworkOverride: Flow<FocChain?>

    companion object {
        /** No override — infer per record, the historical behaviour. */
        val Automatic: FocNetworkSource = object : FocNetworkSource {
            override val focNetworkOverride: Flow<FocChain?> = flowOf(null)
        }
    }
}
