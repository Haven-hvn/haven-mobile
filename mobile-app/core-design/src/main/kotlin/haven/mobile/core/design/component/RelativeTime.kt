package haven.mobile.core.design.component

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Dates as a person reads them.
 *
 * List rows previously carried a piece CID as their subtitle. A content-addressed hash is the
 * right identifier for a storage layer and the wrong one for a library: it tells a reader nothing
 * they can act on, and it makes a media app look like a debugging console. "3 days ago" is the
 * fact they actually wanted.
 *
 * Deliberately coarse. Nobody scanning an archive needs "2 hours, 14 minutes"; precision here
 * would only make the column jitter as it updates.
 */
object RelativeTime {

    fun format(instant: Instant, now: Instant = Clock.System.now()): String {
        val seconds = (now - instant).inWholeSeconds

        // A clock skew between the publisher's chain timestamp and this device should not print
        // "in 3 days" — future stamps are reported as just-published.
        if (seconds < MINUTE) return "Just now"

        return when {
            seconds < HOUR -> plural(seconds / MINUTE, "minute")
            seconds < DAY -> plural(seconds / HOUR, "hour")
            seconds < WEEK -> plural(seconds / DAY, "day")
            seconds < MONTH -> plural(seconds / WEEK, "week")
            seconds < YEAR -> plural(seconds / MONTH, "month")
            else -> plural(seconds / YEAR, "year")
        }
    }

    private fun plural(count: Long, unit: String): String =
        if (count == 1L) "1 $unit ago" else "$count ${unit}s ago"

    private const val MINUTE = 60L
    private const val HOUR = 60L * MINUTE
    private const val DAY = 24L * HOUR
    private const val WEEK = 7L * DAY

    /** Calendar-agnostic averages: this is a label, not an accounting period. */
    private const val MONTH = 30L * DAY
    private const val YEAR = 365L * DAY
}
