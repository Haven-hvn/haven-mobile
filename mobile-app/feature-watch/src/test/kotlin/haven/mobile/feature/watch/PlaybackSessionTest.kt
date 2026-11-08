package haven.mobile.feature.watch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Session state must survive a viewer rebuild (mini-bar re-expand) but not a genuine item switch. */
class PlaybackSessionTest {

    private val shuffledQueue = listOf(
        QueueTrack("album-1", "Album", "/files/album.mp3", 2, "C", 300_000L, null),
        QueueTrack("album-1", "Album", "/files/album.mp3", 0, "A", 0L, 120_000L),
    )

    @Test
    fun `re-expanding the same item restores the exact shuffled queue`() {
        val session = PlaybackSession()
        session.attach("album-1", WatchViewModel.ShuffleMode.ALBUM, shuffledQueue)

        val restored = session.stateFor("album-1")
        assertEquals(WatchViewModel.ShuffleMode.ALBUM, restored.shuffleMode)
        assertEquals(shuffledQueue, restored.playQueue)
    }

    @Test
    fun `opening a different item starts a fresh session`() {
        val session = PlaybackSession()
        session.attach("album-1", WatchViewModel.ShuffleMode.GLOBAL, shuffledQueue)

        val fresh = session.stateFor("album-2")
        assertEquals(WatchViewModel.ShuffleMode.OFF, fresh.shuffleMode)
        assertEquals(emptyList<QueueTrack>(), fresh.playQueue)
        assertEquals("album-2", fresh.itemId)
    }

    @Test
    fun `update keeps the owning item but changes the queue`() {
        val session = PlaybackSession()
        session.attach("album-1", WatchViewModel.ShuffleMode.OFF, emptyList())
        val natural = listOf(
            QueueTrack("album-1", "Album", "/files/album.mp3", 0, "A", 0L, null),
        )
        session.update(WatchViewModel.ShuffleMode.OFF, natural)

        assertEquals("album-1", session.state.value.itemId)
        assertEquals(natural, session.stateFor("album-1").playQueue)
    }
}
