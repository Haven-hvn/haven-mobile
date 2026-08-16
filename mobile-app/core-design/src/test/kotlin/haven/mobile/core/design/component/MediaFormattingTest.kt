package haven.mobile.core.design.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The two pure helpers behind every row, card and settings figure. Both are user-visible strings
 * that appear next to a cache quota, so "close enough" is not good enough.
 */
class MediaFormattingTest {

    @Test
    fun `bytes below a kibibyte are exact`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1023 B", formatBytes(1023))
    }

    @Test
    fun `binary units are used, not decimal`() {
        // 1024 B is 1 KiB. If this ever reads "1.02 KB" the quota maths and the label disagree.
        assertEquals("1.0 KiB", formatBytes(1024))
        assertEquals("1.0 MiB", formatBytes(1024L * 1024))
        assertEquals("1.0 GiB", formatBytes(1024L * 1024 * 1024))
    }

    @Test
    fun `the default quota reads as two gibibytes`() {
        assertEquals("2.0 GiB", formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `large values drop the decimal`() {
        // Three significant figures is plenty at this size, and "512 MiB" beats "512.0 MiB".
        assertEquals("512 MiB", formatBytes(512L * 1024 * 1024))
    }

    @Test
    fun `fractional sizes keep one decimal`() {
        assertEquals("1.5 KiB", formatBytes(1536))
    }

    @Test
    fun `negative sizes degrade instead of throwing`() {
        assertEquals("—", formatBytes(-1))
    }

    @Test
    fun `terabytes are the ceiling unit`() {
        val result = formatBytes(5L * 1024 * 1024 * 1024 * 1024)
        assertTrue(result.endsWith("TiB"), "expected TiB, got $result")
    }

    @Test
    fun `identifiers are truncated in the middle`() {
        // The tail matters: it is what a user compares against a block explorer.
        val cid = "bafkzcibabcdefghijklmnopqrstuvwxyz0123456789"
        assertEquals("bafkzc\u20266789", truncateMiddle(cid, head = 6, tail = 4))
    }

    @Test
    fun `durations pad every unit except the leading one`() {
        // The convention every player uses, and the reason 12:05 cannot be read as twelve hours.
        assertEquals("0:05", formatDuration(5))
        assertEquals("0:59", formatDuration(59))
        assertEquals("1:00", formatDuration(60))
        assertEquals("4:07", formatDuration(247))
        assertEquals("59:59", formatDuration(3599))
    }

    @Test
    fun `durations over an hour gain an hours field`() {
        assertEquals("1:00:00", formatDuration(3600))
        assertEquals("1:02:33", formatDuration(3753))
        assertEquals("12:00:01", formatDuration(43201))
    }

    @Test
    fun `a negative duration degrades instead of printing nonsense`() {
        assertEquals("—", formatDuration(-1))
    }

    @Test
    fun `short identifiers are left alone`() {
        assertEquals("0x1234", truncateMiddle("0x1234", head = 6, tail = 4))
    }

    @Test
    fun `truncation never lengthens the string`() {
        val address = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F"
        val truncated = truncateMiddle(address, head = 10, tail = 8)
        assertTrue(truncated.length < address.length)
        assertTrue(truncated.startsWith("0x71C7656E"))
        assertTrue(truncated.endsWith("f6d8976F"))
    }
}
