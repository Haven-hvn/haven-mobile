package haven.mobile.feature.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import haven.mobile.core.wallet.WalletSession
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Shell-scoped reader for the mini player. The repository itself is a `@Singleton`, so this
 * ViewModel is only a Hilt-friendly accessor the app shell can `hiltViewModel()` at the
 * activity scope — the viewer publishes through [WatchViewModel], the bar reads through this.
 *
 * Also the identity-switch guard for playback: staged plaintext and keys are
 * per-wallet, so a track published under one identity is cleared the moment
 * another identity becomes active instead of playing across the switch.
 */
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    nowPlaying: NowPlayingRepository,
    walletSession: WalletSession,
    playbackSession: PlaybackSession,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    val track: StateFlow<NowPlayingTrack?> = nowPlaying.track

    init {
        viewModelScope.launch {
            walletSession.address.collect { address ->
                val current = nowPlaying.track.value ?: return@collect
                val owner = current.walletAddress
                if (owner != null && !owner.equals(address, ignoreCase = true)) {
                    nowPlaying.clear()
                    playbackSession.clear()
                    stopServicePlayback(appContext)
                }
            }
        }
    }
}
