package com.flowfolio.core.data

import com.flowfolio.core.data.flow.ReadingViews
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each view shows only its own highlights and ribbons (ADR-0045): reflowed ones carry a locator, PDF ones don't. */
class ReadingViewsTest {
    private val reflowed = setOf("flow", "epub")

    @Test
    fun aBookReadAsReflowedTextShowsItemsWithALocator() {
        assertTrue(ReadingViews.shows("flow", "3:16092", reflowed))
        assertFalse(ReadingViews.shows("flow", null, reflowed))
    }

    @Test
    fun aPdfReadAsPagesShowsItemsWithout() {
        assertTrue(ReadingViews.shows("pdf", null, reflowed))
        assertFalse(ReadingViews.shows("pdf", "3:16092", reflowed))
    }
}
