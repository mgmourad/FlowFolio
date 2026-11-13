package com.flowfolio.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenPlacesTest {
    private val page =
        "CHAPTER 4  The Running Head\nThe system keeps the infor-\nmation it needs, e.g. logs.\nIt is live."

    private fun on(range: IntRange?) = range?.let { page.substring(it.first, it.last + 1) }

    @Test
    fun `finds each spoken sentence on the page`() {
        val spoken = listOf("The system keeps the information it needs, for example logs.", "It is live.")
        val places = SpokenPlaces(page, spoken)
        assertEquals("The system keeps the infor-\nmation it needs, e.g. logs", on(places.sentence(0)))
        assertEquals("It is live", on(places.sentence(1)))
    }

    @Test
    fun `a spoken word maps to its place, a word broken across lines included`() {
        val spoken = "The system keeps the information it needs, for example logs."
        val places = SpokenPlaces(page, listOf(spoken))
        val at = spoken.indexOf("information")
        assertEquals("infor-\nmation", on(places.word(0, at, at + "information".length)))
        val logs = spoken.indexOf("logs")
        assertEquals("logs", on(places.word(0, logs, logs + 4)))
    }

    @Test
    fun `a word the page does not have is not placed`() {
        val spoken = "The system keeps the information it needs, for example logs."
        val places = SpokenPlaces(page, listOf(spoken))
        val at = spoken.indexOf("example")
        assertNull(places.word(0, at, at + "example".length))
    }

    @Test
    fun `a title said in full is found in its printed form`() {
        val text = "Mr. Bennet was so odd a mixture."
        val places = SpokenPlaces(text, listOf("Mister Bennet was so odd a mixture."))
        assertEquals(
            "Mr. Bennet was so odd a mixture",
            places.sentence(0)?.let {
                text.substring(it.first, it.last + 1)
            }
        )
    }
}
