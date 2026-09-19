package haven.mobile.core.cache.mirror

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * "Empty" and "unreachable" must never render the same screen: a wallet that holds nothing gets
 * the empty library, but nothing fetched *because every call errored* must surface the cause —
 * otherwise an outage looks exactly like holding nothing and there is nothing to debug.
 */
class RefreshOutcomeTest {

    @Test
    fun `empty with failures reports the first cause`() {
        val first = RuntimeException("no route to host")
        val cause = unreachableCauseOrNull(0, 0, listOf(first, RuntimeException("timeout")))
        assertSame(first, cause)
    }

    @Test
    fun `empty without failures stays success`() {
        assertNull(unreachableCauseOrNull(0, 0, emptyList()))
    }

    @Test
    fun `partial results stay success despite failures`() {
        val failures = listOf(RuntimeException("one community down"))
        assertNull(unreachableCauseOrNull(2, 0, failures))
        assertNull(unreachableCauseOrNull(0, 1, failures))
    }
}
