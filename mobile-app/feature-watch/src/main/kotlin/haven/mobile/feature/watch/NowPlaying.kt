package haven.mobile.feature.watch

import haven.mobile.core.domain.MediaKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the mini player shows. Metadata only — playback position and play state stay with the
 * service player ([HavenPlaybackService]), which is the single owner of audio. The bar is a
 * remote view onto that player, not a second one.
 *
 * Entities carry no thumbnails (there is no `thumbnail_cid` in practice), so the bar renders
 * the kind glyph as its artwork rather than pretending a cover exists.
 */
data class NowPlayingTrack(
    val itemId: String,
    val title: String,
    val kind: MediaKind,
)

/**
 * Session-wide "what is playing", shared between the full viewer and the shell's mini bar.
 *
 * Published when audio/video content stages ([WatchViewModel] owns that moment); read by the
 * app shell to decide whether the mini bar shows. Never cleared on navigation — leaving the
 * viewer *is* the collapse gesture, and playback continues in the service behind it.
 */
@Singleton
class NowPlayingRepository @Inject constructor() {
    private val _track = MutableStateFlow<NowPlayingTrack?>(null)
    val track: StateFlow<NowPlayingTrack?> = _track.asStateFlow()

    fun open(track: NowPlayingTrack) {
        _track.value = track
    }

    fun clear() {
        _track.value = null
    }
}

/**
 * The bar shows on every route except the viewer itself, where the full player is already
 * visible and a second one would stack. Pure so it stays unit-testable without Robolectric.
 */
fun shouldShowMiniPlayer(track: NowPlayingTrack?, currentRoute: String?): Boolean {
    if (track == null) return false
    if (currentRoute?.startsWith(WATCH_ROUTE_PREFIX) == true) return false
    return true
}

private const val WATCH_ROUTE_PREFIX = "watch/"
