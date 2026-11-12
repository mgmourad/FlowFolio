package com.flowfolio.core.data

import java.util.Calendar
import java.util.TimeZone

/** The reading streak the Reading goal widget shows (TM2): days in a row on which the reader read. */
object ReadingStreak {
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val LOOKBACK_DAYS = 400L

    /** A day counts with at least this much reading, so opening a book by mistake does not. */
    private const val MIN_DAY_MS = 60_000L

    /**
     * Counts the days in a row, back from today, with at least a minute read in [sessions] (start and end, in
     * milliseconds). Today not read yet does not break the streak: it then counts back from yesterday.
     */
    fun days(sessions: List<Pair<Long, Long>>, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val perDay = HashMap<Long, Long>()
        for ((start, end) in sessions) {
            var from = start
            while (from < end) {
                val day = dayStart(from, zone)
                val until = minOf(end, dayStart(day + DAY_MS + DAY_MS / 2, zone))
                perDay[day] = (perDay[day] ?: 0L) + (until - from)
                from = until
            }
        }
        val read = { day: Long -> (perDay[day] ?: 0L) >= MIN_DAY_MS }
        var day = dayStart(nowMillis, zone)
        if (!read(day)) day = previous(day, zone)
        var count = 0
        while (read(day)) {
            count++
            day = previous(day, zone)
        }
        return count
    }

    /** The start of the day before [day], allowing for days of 23 or 25 hours. */
    private fun previous(day: Long, zone: TimeZone): Long = dayStart(day - DAY_MS / 2, zone)

    private fun dayStart(millis: Long, zone: TimeZone): Long = Calendar.getInstance(zone).run {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }
}
