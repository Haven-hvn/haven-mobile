package haven.mobile.feature.watch

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The pure geometry behind the designed audio player: the decorative waveform, scrub-to-time
 * mapping, and the ambient color wash. Everything the composable draws that can be reasoned
 * about without a device lives here, mirroring the mini bar's gesture/clock tests.
 */
class AudioPlayerTest {

    @Test
    fun `waveform has the requested bucket count`() {
        assertEquals(48, waveformBuckets("album-1").size)
        assertEquals(24, waveformBuckets("album-1", count = 24).size)
    }

    @Test
    fun `waveform is deterministic for a given seed`() {
        assertEquals(waveformBuckets("late-night-set"), waveformBuckets("late-night-set"))
    }

    @Test
    fun `different seeds give different waveforms`() {
        assertTrue(waveformBuckets("a") != waveformBuckets("b"))
    }

    @Test
    fun `every waveform bucket stays within the drawn range`() {
        waveformBuckets("any-seed", count = 64).forEach { value ->
            assertTrue(value >= 0.12f, "bucket $value fell below the floor")
            assertTrue(value <= 1.0f, "bucket $value exceeded the ceiling")
        }
    }

    @Test
    fun `non-positive count yields an empty waveform`() {
        assertTrue(waveformBuckets("x", count = 0).isEmpty())
        assertTrue(waveformBuckets("x", count = -3).isEmpty())
    }

    @Test
    fun `scrub fraction maps linearly onto duration`() {
        assertEquals(0L, seekFractionToMs(0f, 60_000L))
        assertEquals(30_000L, seekFractionToMs(0.5f, 60_000L))
        assertEquals(60_000L, seekFractionToMs(1f, 60_000L))
    }

    @Test
    fun `scrub fraction clamps out-of-range input`() {
        assertEquals(0L, seekFractionToMs(-0.5f, 60_000L))
        assertEquals(60_000L, seekFractionToMs(1.8f, 60_000L))
    }

    @Test
    fun `unknown duration seeks nowhere`() {
        assertEquals(0L, seekFractionToMs(0.7f, 0L))
        assertEquals(0L, seekFractionToMs(0.7f, -1L))
    }

    @Test
    fun `ambient tint darkens the cover color`() {
        val tint = ambientTint(Color(0.8f, 0.4f, 0.2f, 1f))
        assertTrue(tint.red < 0.8f)
        assertTrue(tint.green < 0.4f)
        assertTrue(tint.blue < 0.2f)
        assertEquals(0.55f, tint.alpha, 0.001f)
    }

    @Test
    fun `no cover color falls back to a fixed dark ground`() {
        val tint = ambientTint(null)
        assertEquals(Color(0xFF10141C), tint)
    }
}
