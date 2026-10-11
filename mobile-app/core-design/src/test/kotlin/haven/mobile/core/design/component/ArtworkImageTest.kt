package haven.mobile.core.design.component

import org.junit.jupiter.api.Assertions.assertEquals
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
}
