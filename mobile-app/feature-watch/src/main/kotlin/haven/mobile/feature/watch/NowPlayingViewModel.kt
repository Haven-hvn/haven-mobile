package haven.mobile.feature.watch

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Shell-scoped reader for the mini player. The repository itself is a `@Singleton`, so this
 * ViewModel is only a Hilt-friendly accessor the app shell can `hiltViewModel()` at the
 * activity scope — the viewer publishes through [WatchViewModel], the bar reads through this.
 */
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    nowPlaying: NowPlayingRepository,
) : ViewModel() {
    val track: StateFlow<NowPlayingTrack?> = nowPlaying.track
}
