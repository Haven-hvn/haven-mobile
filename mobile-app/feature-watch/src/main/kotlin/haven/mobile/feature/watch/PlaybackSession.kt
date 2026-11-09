package haven.mobile.feature.watch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session-wide playback queue state, shared across every [WatchViewModel] instance.
 *
 * Shuffle mode and the play queue are properties of the *listening session*, not of one screen:
 * the service player keeps playing when the viewer is collapsed, but the nav entry (and its
 * ViewModel) is destroyed and rebuilt when the mini bar re-expands it. A per-ViewModel queue was
 * reset on that rebuild — the album's chapter list came back unshuffled and, worse, was
 * re-applied to the player from track one. Keeping the queue here means a re-expand restores the
 * exact same queue (including a random shuffle order) that is already loaded in the player, so
 * reconnecting is a genuine no-op and the current track and position survive.
 *
 * The [itemId] tags which library item the state belongs to: opening a genuinely different item
 * starts a fresh session, while re-opening the same one restores it.
 */
@Singleton
class PlaybackSession @Inject constructor() {

    data class State(
        val itemId: String? = null,
        val shuffleMode: WatchViewModel.ShuffleMode = WatchViewModel.ShuffleMode.OFF,
        val playQueue: List<QueueTrack> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Restore the state for [itemId], or start a fresh session if it belongs to another item. */
    fun stateFor(itemId: String): State =
        _state.value.takeIf { it.itemId == itemId } ?: State(itemId = itemId)

    /** Begin (or restore the identity of) a session owned by [itemId]. */
    fun attach(itemId: String, shuffleMode: WatchViewModel.ShuffleMode, queue: List<QueueTrack>) {
        _state.value = State(itemId = itemId, shuffleMode = shuffleMode, playQueue = queue)
    }

    /** Drops the session entirely (identity switch: the queue belongs to the previous wallet). */
    fun clear() {
        _state.value = State()
    }

    /** Mirror a queue/shuffle change made in a viewer into the session. */
    fun update(shuffleMode: WatchViewModel.ShuffleMode, queue: List<QueueTrack>) {
        _state.value = _state.value.copy(shuffleMode = shuffleMode, playQueue = queue)
    }
}
