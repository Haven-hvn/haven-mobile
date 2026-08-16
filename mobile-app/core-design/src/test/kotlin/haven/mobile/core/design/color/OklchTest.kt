package haven.mobile.core.design.color

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Every brand colour in the app is produced by [Oklch], so an error here is an error in all of
 * them. These assert the properties that matter rather than transcribed hex values — the point of
 * authoring in OKLCH is to *not* maintain a table of hexes by hand.
 */
class OklchTest {

    @Test
    fun `zero lightness is black`() {
        val color = Oklch.color(l = 0f, c = 0f, hueDegrees = 0f)
        assertEquals(0f, color.red, TOLERANCE)
        assertEquals(0f, color.green, TOLERANCE)
        assertEquals(0f, color.blue, TOLERANCE)
    }

    @Test
    fun `full lightness with no chroma is white`() {
        val color = Oklch.color(l = 1f, c = 0f, hueDegrees = 0f)
        assertEquals(1f, color.red, TOLERANCE)
        assertEquals(1f, color.green, TOLERANCE)
        assertEquals(1f, color.blue, TOLERANCE)
    }

    @Test
    fun `no chroma produces a neutral grey at any hue`() {
        val fromRedHue = Oklch.color(l = 0.5f, c = 0f, hueDegrees = 29f)
        val fromBlueHue = Oklch.color(l = 0.5f, c = 0f, hueDegrees = 264f)
        assertEquals(fromRedHue.red, fromBlueHue.red, TOLERANCE)
        assertEquals(fromRedHue.red, fromRedHue.green, TOLERANCE)
        assertEquals(fromRedHue.green, fromRedHue.blue, TOLERANCE)
    }

    @Test
    fun `lightness is monotonic`() {
        // The surface ramp depends on this: each step up in L must actually look lighter.
        var previous = -1f
        listOf(0.078f, 0.132f, 0.158f, 0.186f, 0.216f, 0.248f, 0.290f).forEach { l ->
            val luminance = luminanceOf(Oklch.color(l = l, c = 0.02f, hueDegrees = 266f))
            assertTrue(luminance > previous, "L=$l should be lighter than the previous step")
            previous = luminance
        }
    }

    @Test
    fun `out of gamut chroma is clamped into range`() {
        // 0.4 chroma at this hue is outside sRGB; the result must still be a valid colour.
        val color = Oklch.color(l = 0.7f, c = 0.4f, hueDegrees = 44f)
        listOf(color.red, color.green, color.blue).forEach { channel ->
            assertTrue(channel in 0f..1f, "channel $channel should be inside 0..1")
        }
    }

    @Test
    fun `alpha passes through`() {
        val color = Oklch.color(l = 0.5f, c = 0.1f, hueDegrees = 44f, alpha = 0.42f)
        assertEquals(0.42f, color.alpha, TOLERANCE)
    }

    @Test
    fun `ember is a warm orange`() {
        // The brand accent: red dominant, green mid, blue lowest. If this inverts, the seal has
        // turned blue and something is wrong with the matrix.
        val ember = HavenPalette.EmberBright
        assertTrue(ember.red > ember.green, "ember red should exceed green")
        assertTrue(ember.green > ember.blue, "ember green should exceed blue")
    }

    @Test
    fun `void surface is near black and paper is near white`() {
        assertTrue(
            luminanceOf(HavenPalette.VoidBase) < 0.05f,
            "void surface should be near black",
        )
        assertTrue(
            luminanceOf(HavenPalette.PaperBase) > 0.85f,
            "paper surface should be near white",
        )
    }

    @Test
    fun `paper stock is warm rather than pure white`() {
        // "Never pure white" is a brand rule, and a warm stock means red >= green > blue.
        val paper = HavenPalette.PaperBase
        assertTrue(paper.blue < paper.red, "paper should be warmer than neutral")
        assertTrue(paper.red < 1f, "paper should not be pure white")
    }

    @Test
    fun `lightnessOf round-trips the authored lightness`() {
        listOf(0.1f, 0.35f, 0.62f, 0.9f).forEach { l ->
            val measured = Oklch.lightnessOf(Oklch.color(l = l, c = 0.05f, hueDegrees = 200f))
            assertEquals(l, measured, ROUND_TRIP_TOLERANCE)
        }
    }

    private fun luminanceOf(color: Color): Float =
        0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

    private companion object {
        const val TOLERANCE = 0.004f

        /** Conversion goes through the sRGB transfer function and an 8-bit-ish clamp both ways. */
        const val ROUND_TRIP_TOLERANCE = 0.015f
    }
}
