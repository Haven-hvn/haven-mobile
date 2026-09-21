package haven.mobile.core.wallet

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A signing timeout means the wallet never answered — the copy must name the
 * fix (open the wallet app, approve on the right chain), never the raw
 * `Timed out waiting for 120000 ms` coroutine text.
 */
class SignFailureMessageTest {

    /** The constructor is internal — mint a real one the way production does. */
    private fun timeout(): TimeoutCancellationException = runBlocking {
        try {
            withTimeout(1) { delay(1_000) }
            error("should have timed out")
        } catch (e: TimeoutCancellationException) {
            e
        }
    }

    @Test
    fun `timeout names the chain and the fix`() {
        val message = signFailureMessage(timeout(), 1L)
        assertTrue(message.contains("eip155:1"), message)
        assertTrue(message.contains("wallet app", ignoreCase = true), message)
    }

    @Test
    fun `other failures keep their message`() {
        assertEquals(
            "Request expired",
            signFailureMessage(Exception("Request expired"), 1L),
        )
    }

    @Test
    fun `blank cause falls back instead of printing null`() {
        assertEquals(
            "Unknown error",
            signFailureMessage(Exception(null as String?), 1L),
        )
    }
}
