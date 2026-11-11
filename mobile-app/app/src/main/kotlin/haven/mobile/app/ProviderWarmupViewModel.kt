package haven.mobile.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cloud.filecoin.foc.cache.FocChain
import dagger.hilt.android.lifecycle.HiltViewModel
import haven.mobile.core.arkiv.ArkivConfig
import haven.mobile.core.arkiv.FocNetworkSource
import haven.mobile.core.cache.HavenCache
import haven.mobile.core.wallet.WalletSession
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Pre-fills the on-chain SP registry for the configured Filecoin network at session start, so the
 * first piece miss (a piece opened in the library before it's cached) is not the call that pays for
 * the registry read. Warms only the network the app would actually reach — when the user picked one
 * in Settings it's that one, otherwise the Arkiv-endpoint inference [ArkivConfig.resolvedFocChain].
 *
 * Registry contents are cached in-memory for the process lifetime (haven-mobile passes an effectively
 * infinite `registryCacheTtl`), so one warm per chain per app run is all this pays for.
 */
@HiltViewModel
class ProviderWarmupViewModel @Inject constructor(
    cache: HavenCache,
    walletSession: WalletSession,
    focNetworkSource: FocNetworkSource,
    arkivConfig: ArkivConfig,
) : ViewModel() {
    init {
        viewModelScope.launch {
            combine(
                focNetworkSource.focNetworkOverride,
                walletSession.address.filterNotNull(),
            ) { override, _ -> override ?: arkivConfig.resolvedFocChain }
                .distinctUntilChanged()
                .collect { chain: FocChain -> cache.warmupProviders(listOf(chain)) }
        }
    }
}
