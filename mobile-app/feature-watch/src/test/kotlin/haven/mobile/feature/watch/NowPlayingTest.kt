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
    fun `short slow drag springs back`() {
        assertFalse(shouldCollapseOnRelease(offsetPx = 40f, thresholdPx = 100f, velocityPxPerSec = 0f))
    }

    @Test
    fun `drag past the threshold collapses`() {
        assertTrue(shouldCollapseOnRelease(offsetPx = 120f, thresholdPx = 100f, velocityPxPerSec = 0f))
    }

    @Test
    fun `fast downward fling collapses from a short drag`() {
        assertTrue(shouldCollapseOnRelease(offsetPx = 20f, thresholdPx = 100f, velocityPxPerSec = 2_500f))
    }

    @Test
    fun `upward fling from a short drag springs back`() {
        // Past-threshold offset still collapses (the drag already committed); velocity only
        // rescues short drags, so an upward fling on a short drag must not dismiss.
        assertFalse(shouldCollapseOnRelease(offsetPx = 20f, thresholdPx = 100f, velocityPxPerSec = -2_500f))
    }

    @Test
    fun `zero offset never collapses even when moving`() {
        assertFalse(shouldCollapseOnRelease(offsetPx = 0f, thresholdPx = 100f, velocityPxPerSec = 5_000f))
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
    fun `artwork files live under the artwork corner`() {
        val file = artworkFileFor(java.io.File("/cache"), "abc123")
        assertEquals(java.io.File("/cache/haven-artwork/abc123.jpg"), file)
    }

    @Test
    fun `hostile ids cannot escape the artwork corner`() {
        val file = artworkFileFor(java.io.File("/cache"), "../../etc/passwd")
        assertEquals("haven-artwork", file.parentFile?.name)
        assertTrue(file.name.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' })
    }

    @Test
    fun `tracks carry no artwork unless staging found some`() {
        assertNull(track.artworkPath)
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
