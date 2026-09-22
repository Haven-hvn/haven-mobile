package haven.mobile.feature.watch

import haven.mobile.core.domain.MediaKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The mini player's contract: when it appears, what it carries, and that it survives
 * navigation (leaving the viewer is the collapse gesture, not a stop).
 */
class NowPlayingTest {

    private val track = NowPlayingTrack(
        itemId = "abc",
        title = "Late Night Set",
        kind = MediaKind.AUDIO,
    )

    @Test
    fun `no track means no bar`() {
        assertFalse(shouldShowMiniPlayer(null, "library"))
        assertFalse(shouldShowMiniPlayer(null, null))
    }

    @Test
    fun `bar shows on tab routes while something plays`() {
        assertTrue(shouldShowMiniPlayer(track, "library"))
        assertTrue(shouldShowMiniPlayer(track, "community"))
        assertTrue(shouldShowMiniPlayer(track, "settings"))
        assertTrue(shouldShowMiniPlayer(track, null))
    }

    @Test
    fun `bar hides on the viewer where the full player is visible`() {
        assertFalse(shouldShowMiniPlayer(track, "watch/abc"))
        assertFalse(shouldShowMiniPlayer(track, "watch/other-id"))
    }

    @Test
    fun `repository publishes the opened track`() {
        val repository = NowPlayingRepository()
        assertNull(repository.track.value)
        repository.open(track)
        assertEquals(track, repository.track.value)
    }

    @Test
    fun `repository keeps the track across navigation`() {
        // Leaving the viewer collapses into the bar; nothing clears the track on the way out.
        val repository = NowPlayingRepository()
        repository.open(track)
        assertTrue(shouldShowMiniPlayer(repository.track.value, "library"))
    }

    @Test
    fun `reopening replaces the track rather than stacking`() {
        val repository = NowPlayingRepository()
        repository.open(track)
        val next = track.copy(itemId = "def", title = "Another Set")
        repository.open(next)
        assertEquals(next, repository.track.value)
    }

    @Test
    fun `short pulls do not collapse`() {
        var collapses = 0
        val tracker = OverscrollCollapse(thresholdPx = 100f) { collapses++ }
        tracker.onOverscroll(40f)
        tracker.onOverscroll(40f)
        assertEquals(0, collapses)
    }

    @Test
    fun `one long pull collapses exactly once`() {
        var collapses = 0
        val tracker = OverscrollCollapse(thresholdPx = 100f) { collapses++ }
        tracker.onOverscroll(60f)
        tracker.onOverscroll(60f)
        // The accumulator resets on fire, so the leftover does not chain into a second collapse.
        tracker.onOverscroll(60f)
        assertEquals(1, collapses)
    }

    @Test
    fun `upward motion resets the pull`() {
        var collapses = 0
        val tracker = OverscrollCollapse(thresholdPx = 100f) { collapses++ }
        tracker.onOverscroll(80f)
        tracker.onOverscroll(-10f)
        tracker.onOverscroll(80f)
        assertEquals(0, collapses)
    }

    @Test
    fun `halfway progress is one half`() {
        assertEquals(0.5f, progressFraction(30_000L, 60_000L), 0.001f)
    }

    @Test
    fun `unknown duration renders an empty edge`() {
        assertEquals(0f, progressFraction(30_000L, 0L), 0.001f)
        assertEquals(0f, progressFraction(30_000L, -1L), 0.001f)
    }

    @Test
    fun `overrun clamps to full rather than overflowing`() {
        assertEquals(1f, progressFraction(70_000L, 60_000L), 0.001f)
    }

    @Test
    fun `zero position renders empty`() {
        assertEquals(0f, progressFraction(0L, 60_000L), 0.001f)
    }

    @Test
    fun `short clock is minutes and seconds`() {
        assertEquals("0:00", formatPlaybackTime(0L))
        assertEquals("1:05", formatPlaybackTime(65_000L))
        assertEquals("59:59", formatPlaybackTime(3_599_000L))
    }

    @Test
    fun `long clock grows an hour field`() {
        assertEquals("1:00:00", formatPlaybackTime(3_600_000L))
        assertEquals("1:06:29", formatPlaybackTime(3_989_000L))
    }

    @Test
    fun `unknown time never shows a bogus zero`() {
        assertEquals("--:--", formatPlaybackTime(-1L))
    }

    @Test
    fun `clear hides the bar`() {
        val repository = NowPlayingRepository()
        repository.open(track)
        repository.clear()
        assertNull(repository.track.value)
        assertFalse(shouldShowMiniPlayer(repository.track.value, "library"))
    }
}
