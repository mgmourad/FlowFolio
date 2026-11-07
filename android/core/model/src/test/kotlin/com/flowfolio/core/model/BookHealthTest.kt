package com.flowfolio.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BookHealthTest {
    private fun health(fallbackPages: Int, pages: Int = 1000) =
        BookHealth(pages, fallbackPages, fallbackPages, 0, 1f, 1f)

    @Test
    fun nothingFallingBackIsGood() {
        assertEquals(HealthGrade.GOOD, health(fallbackPages = 0).grade)
    }

    @Test
    fun aFewFallingBackIsFair() {
        assertEquals(HealthGrade.FAIR, health(fallbackPages = 5).grade)
        // A small book: even two pages reads as "a few".
        assertEquals(HealthGrade.FAIR, health(fallbackPages = 2, pages = 10).grade)
    }

    @Test
    fun manyFallingBackIsPoor() {
        assertEquals(HealthGrade.POOR, health(fallbackPages = 50).grade)
    }
}
