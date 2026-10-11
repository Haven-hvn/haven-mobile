package haven.mobile.core.design.component

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ArtworkImageTest {

    @Test
    fun `large art downsamples to a power of two`() {
        assertEquals(16, sampleSizeFor(3000, 144))
    }

    @Test
    fun `small art is never upsampled`() {
        assertEquals(1, sampleSizeFor(100, 144))
    }

    @Test
    fun `degenerate sizes fall back to full decode`() {
        assertEquals(1, sampleSizeFor(0, 144))
        assertEquals(1, sampleSizeFor(3000, 0))
    }

    @Test
    fun `solid cover reads its own color`() {
        val red = 0xFFFF2018.toInt()
        // Buckets for (255, 32, 24) are (7, 1, 0); centers are (240, 48, 16).
        assertEquals(Color(240, 48, 16), dominantColor(IntArray(100) { red }))
    }

    @Test
    fun `majority color wins over noise`() {
        val pixels = IntArray(90) { 0xFF2040C0.toInt() } + IntArray(10) { 0xFFE0E0E0.toInt() }
        // Buckets for (32, 64, 192) are (1, 2, 6); centers are (48, 80, 208).
        assertEquals(Color(48, 80, 208), dominantColor(pixels))
    }

    @Test
    fun `transparent pixels never win`() {
        assertNull(dominantColor(intArrayOf()))
        assertNull(dominantColor(IntArray(10) { 0x00123456 }))
        val mixed = IntArray(10) { 0x00000000 } + IntArray(5) { 0xFF00C000.toInt() }
        assertEquals(Color(16, 208, 16), mixed.let(::dominantColor))
    }

    @Test
    fun `scrim darkens the cover color and defaults to black`() {
        // Delta absorbs Color's internal packing, not the math: a wrong factor (say 0.5)
        // would still miss by 0.07, far outside it.
        val base = scrimBase(Color(240, 48, 16))
        assertEquals(0.42f * 240 / 255f, base.red, 2e-3f)
        assertEquals(0.42f * 48 / 255f, base.green, 2e-3f)
        assertEquals(0.42f * 16 / 255f, base.blue, 2e-3f)
        assertEquals(Color.Black, scrimBase(null))
    }
}
