package com.flowfolio.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingEstimateTest {
    @Test
    fun usesTheMockPaceUntilThereIsEnoughReading() {
        assertEquals(100 * ReadingEstimate.DEFAULT_MS, ReadingEstimate.millisLeft(100, 3, 120_000L))
    }

    @Test
    fun usesTheReadersOwnPaceOnceThereIsEnough() {
        // 20 pages in 20 minutes: a minute a page.
        assertEquals(50 * 60_000L, ReadingEstimate.millisLeft(50, 20, 20 * 60_000L))
    }

    @Test
    fun keepsThePaceSensible() {
        // 10 pages in 10 hours (left open): capped at five minutes a page.
        assertEquals(10 * 300_000L, ReadingEstimate.millisLeft(10, 10, 36_000_000L))
    }

    @Test
    fun showsHoursFromAnHourOnElseMinutes() {
        assertEquals(27L to true, ReadingEstimate.parts(27 * 3_600_000L))
        assertEquals(45L to false, ReadingEstimate.parts(45 * 60_000L))
        assertEquals(1L to false, ReadingEstimate.parts(5_000L))
    }
}
