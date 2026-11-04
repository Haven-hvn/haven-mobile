package haven.mobile.core.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * An unavailable item must be able to leave the viewer, tell the library
 * which row to suppress, and explain itself in one transient notice. These
 * tests pin the cross-screen contract so the two features cannot drift apart.
 */
class UnavailableReturnTest {

    @Test
    fun `return notice survives navigation state`() {
        val notice = unavailableReturnNotice("item-7", "Harbor Lights")
        val restored = roundTrip(notice)
        assertEquals("item-7", restored.itemId)
        assertEquals("Harbor Lights", restored.title)
    }

    @Test
    fun `blank titles fall back to the item id`() {
        assertEquals("item-7", unavailableReturnNotice("item-7", "  ").title)
    }

    @Test
    fun `return message names the title and its way back`() {
        val message = unavailableReturnMessage("Harbor Lights")
        assertTrue(message.contains("Harbor Lights"), message)
        assertTrue(message.contains("hidden", ignoreCase = true), message)
        assertTrue(message.contains("Show hidden", ignoreCase = true), message)
    }

    @Test
    fun `auto-return waits long enough to be read`() {
        assertEquals(4_000L, UNAVAILABLE_AUTO_RETURN_MILLIS)
    }

    @Test
    fun `suppression notice stays long enough to offer its way back`() {
        assertEquals(8_000L, UNAVAILABLE_NOTICE_MILLIS)
    }

    private fun roundTrip(notice: WatchUnavailableNotice): WatchUnavailableNotice {
        val bytes = ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { it.writeObject(notice) }
            output.toByteArray()
        }
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as WatchUnavailableNotice }
    }
}
