package haven.mobile.core.design.component

import kotlin.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * This string replaced the piece CID under every list row, so it is now the only thing telling a
 * reader how recent an item is. Rounding is deliberately coarse; these pin that it stays coarse in
 * the same direction each time.
 */
class RelativeTimeTest {

    private val now = Instant.parse("2026-08-15T12:00:00Z")

    @Test
    fun `moments ago reads as just now`() {
        assertEquals("Just now", format("2026-08-15T12:00:00Z"))
        assertEquals("Just now", format("2026-08-15T11:59:31Z"))
    }

    @Test
    fun `minutes are singular at one`() {
        assertEquals("1 minute ago", format("2026-08-15T11:59:00Z"))
        assertEquals("5 minutes ago", format("2026-08-15T11:55:00Z"))
    }

    @Test
    fun `hours take over after sixty minutes`() {
        assertEquals("59 minutes ago", format("2026-08-15T11:01:00Z"))
        assertEquals("1 hour ago", format("2026-08-15T11:00:00Z"))
        assertEquals("7 hours ago", format("2026-08-15T05:00:00Z"))
    }

    @Test
    fun `days take over after twenty four hours`() {
        assertEquals("23 hours ago", format("2026-08-14T13:00:00Z"))
        assertEquals("1 day ago", format("2026-08-14T12:00:00Z"))
        assertEquals("3 days ago", format("2026-08-12T12:00:00Z"))
    }

    @Test
    fun `weeks months and years`() {
        assertEquals("1 week ago", format("2026-08-08T12:00:00Z"))
        assertEquals("2 weeks ago", format("2026-08-01T12:00:00Z"))
        assertEquals("1 month ago", format("2026-07-10T12:00:00Z"))
        assertEquals("1 year ago", format("2025-06-01T12:00:00Z"))
    }

    @Test
    fun `a future timestamp does not read as in three days`() {
        // Chain timestamps and device clocks disagree; "in 3 days" on a library row is a bug report.
        assertEquals("Just now", format("2026-08-18T12:00:00Z"))
    }

    private fun format(instant: String): String =
        RelativeTime.format(Instant.parse(instant), now)
}
