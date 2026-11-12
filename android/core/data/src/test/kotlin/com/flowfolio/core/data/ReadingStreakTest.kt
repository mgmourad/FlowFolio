package com.flowfolio.core.data

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingStreakTest {
    private val zone = TimeZone.getTimeZone("Europe/London")
    private val minute = 60_000L

    /** Noon on the 2026-11-[day], London time. */
    private fun noon(day: Int): Long = Calendar.getInstance(zone).run {
        clear()
        set(2026, Calendar.NOVEMBER, day, 12, 0)
        timeInMillis
    }

    private fun read(day: Int, minutes: Long) = noon(day) to noon(day) + minutes * minute

    @Test
    fun countsDaysInARowUpToToday() {
        val sessions = listOf(read(9, 20), read(10, 5), read(11, 30), read(12, 2))
        assertEquals(4, ReadingStreak.days(sessions, noon(12) + 10 * minute, zone))
    }

    @Test
    fun todayNotReadYetCountsFromYesterday() {
        val sessions = listOf(read(10, 5), read(11, 30))
        assertEquals(2, ReadingStreak.days(sessions, noon(12), zone))
    }

    @Test
    fun aMissedDayEndsTheStreak() {
        val sessions = listOf(read(8, 30), read(10, 5), read(11, 30))
        assertEquals(2, ReadingStreak.days(sessions, noon(11) + 31 * minute, zone))
        assertEquals(0, ReadingStreak.days(sessions, noon(13), zone))
    }

    @Test
    fun underAMinuteDoesNotCount() {
        val sessions = listOf(read(11, 30), noon(12) to noon(12) + 30_000L)
        assertEquals(1, ReadingStreak.days(sessions, noon(12) + minute, zone))
    }

    @Test
    fun aSessionPastMidnightCountsForBothDays() {
        val lateNight = Calendar.getInstance(zone).run {
            clear()
            set(2026, Calendar.NOVEMBER, 10, 23, 50)
            timeInMillis
        }
        val sessions = listOf(lateNight to lateNight + 20 * minute)
        assertEquals(2, ReadingStreak.days(sessions, noon(11), zone))
    }

    @Test
    fun crossesTheClockChange() {
        // The clocks went back on 2026-10-25 in London: that day had 25 hours.
        val sessions = (20..31).map { day ->
            Calendar.getInstance(zone).run {
                clear()
                set(2026, Calendar.OCTOBER, day, 12, 0)
                timeInMillis to timeInMillis + 10 * minute
            }
        }
        val now = Calendar.getInstance(zone).run {
            clear()
            set(2026, Calendar.OCTOBER, 31, 18, 0)
            timeInMillis
        }
        assertEquals(12, ReadingStreak.days(sessions, now, zone))
    }
}
