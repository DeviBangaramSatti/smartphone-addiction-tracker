package com.smarttracker.util

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * TimeUtils — centralised date/time helpers.
 *
 * Used by:
 *   UsageRepository    → today's date key
 *   AnalyticsViewModel → weekly date range
 *   DashboardFragment  → human-readable duration
 */
object TimeUtils {

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val DISPLAY_FORMAT = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    val today: String get() = DATE_FORMAT.format(Date())

    /** Returns a list of the last [days] date strings, newest last */
    fun lastNDates(days: Int): List<String> {
        val cal = Calendar.getInstance()
        return (days - 1 downTo 0).map { offset ->
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, -offset)
            DATE_FORMAT.format(cal.time)
        }
    }

    /** e.g. "Mon, Jan 6" */
    fun toDisplayDate(dateStr: String): String {
        return try {
            val date = DATE_FORMAT.parse(dateStr) ?: return dateStr
            DISPLAY_FORMAT.format(date)
        } catch (e: Exception) {
            dateStr
        }
    }

    /** Formats milliseconds → "2h 34m" or "45m" */
    fun formatDuration(ms: Long): String {
        val hours   = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            else      -> "${minutes}m"
        }
    }

    /** Milliseconds → hours as a Float, rounded to 2 dp */
    fun msToHours(ms: Long): Float =
        (ms / 3_600_000.0).toFloat()
            .let { Math.round(it * 100) / 100f }

    /**
     * True if the given epoch millis falls between 22:00 and 06:00.
     * Used by UsageRepository to calculate night-usage hours.
     */
    fun isNightTime(epochMs: Long): Boolean {
        val cal  = Calendar.getInstance().apply { timeInMillis = epochMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour >= 22 || hour < 6
    }
}
