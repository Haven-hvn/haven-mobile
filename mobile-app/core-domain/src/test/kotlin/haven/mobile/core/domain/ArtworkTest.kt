package haven.mobile.core.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArtworkTest {

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
}
