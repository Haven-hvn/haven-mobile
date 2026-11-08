package haven.mobile.feature.watch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The queue-idempotency logic that keeps a collapse/expand from restarting the album. Both
 * helpers are plain data (no MediaController, no Uri), so the reconnect decision is testable on
 * the JVM without a device.
 */
class PlaybackQueueTest {

    private fun albumQueue() = listOf(
        QueueTrack("album-1", "Album", "/files/album.mp3", 0, "Intro", 0L, 120_000L),
        QueueTrack("album-1", "Album", "/files/album.mp3", 1, "Second", 120_000L, 300_000L),
        QueueTrack("album-1", "Album", "/files/album.mp3", 2, "Outro", 300_000L, null),
    )

    @Test
    fun `fingerprint is file path plus clip start, in order`() {
        assertEquals(
            listOf(
                "/files/album.mp3" to 0L,
                "/files/album.mp3" to 120_000L,
                "/files/album.mp3" to 300_000L,
            ),
            queueFingerprint(albumQueue()),
        )
    }

    @Test
    fun `a matching player timeline is recognised as already loaded`() {
        val queue = albumQueue()
        val timeline = queueFingerprint(queue)
        // Reconnect: the service still holds exactly this queue, so re-applying is skipped.
        assertTrue(queueAlreadyLoaded(timeline, queue))
    }

    @Test
    fun `a cold-start single whole-file item does not match a chaptered queue`() {
        val queue = albumQueue()
        // rememberPlaybackController installs one unclipped whole-file item on a cold start.
        val coldStartTimeline = listOf("/files/album.mp3" to 0L)
        assertFalse(queueAlreadyLoaded(coldStartTimeline, queue))
    }

    @Test
    fun `a shuffled reorder is a different queue and is re-applied`() {
        val queue = albumQueue()
        val shuffled = listOf(queue[2], queue[0], queue[1])
        assertFalse(queueAlreadyLoaded(queueFingerprint(queue), shuffled))
        assertTrue(queueAlreadyLoaded(queueFingerprint(shuffled), shuffled))
    }

    @Test
    fun `a different album is a different queue`() {
        val queue = albumQueue()
        val other = queue.map { it.copy(filePath = "/files/other.mp3") }
        assertFalse(queueAlreadyLoaded(queueFingerprint(queue), other))
    }

    @Test
    fun `chapterless single-track album matches its cold-start item`() {
        val wholeFile = listOf(
            QueueTrack("album-1", "Album", "/files/album.mp3", -1, "Album", 0L, null),
        )
        // Both are [(path, 0)] so nothing needs re-applying — functionally identical media.
        assertTrue(queueAlreadyLoaded(listOf("/files/album.mp3" to 0L), wholeFile))
    }
}
