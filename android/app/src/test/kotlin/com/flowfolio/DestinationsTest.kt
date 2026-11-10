package com.flowfolio

import com.flowfolio.feature.library.NotebookSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The four tabs of ADR-0052 and where the retired routes go. */
class DestinationsTest {
    @Test
    fun theShellHasFourTabsWithSettingsLast() {
        assertEquals(
            listOf(TopLevel.Library, TopLevel.Shelves, TopLevel.Notebook, TopLevel.Settings),
            TopLevel.entries.toList()
        )
    }

    @Test
    fun tabsAreFoundWhateverTheirArguments() {
        assertEquals(TopLevel.Library, TopLevel.of("library"))
        assertEquals(TopLevel.Notebook, TopLevel.of(Destinations.NOTEBOOK))
        assertEquals(TopLevel.Notebook, TopLevel.of("notebook?section=WORDS"))
        assertEquals(TopLevel.Settings, TopLevel.of("settings"))
        assertNull(TopLevel.of(Routes.READER))
        assertNull(TopLevel.of(null))
    }

    @Test
    fun retiredRoutesLightTheirNewHomesTab() {
        assertEquals(TopLevel.Library, TopLevel.of("favorites"))
        assertEquals(TopLevel.Notebook, TopLevel.of("bookmarks"))
        assertEquals(TopLevel.Notebook, TopLevel.of("annotations"))
        assertEquals(TopLevel.Notebook, TopLevel.of("dictionary"))
    }

    @Test
    fun retiredRoutesRedirect() {
        assertEquals(Destinations.Redirect.LibraryFavorites, Destinations.redirectFor("favorites"))
        assertEquals(
            Destinations.Redirect.Notebook(NotebookSection.BOOKMARKS),
            Destinations.redirectFor("bookmarks")
        )
        assertEquals(
            Destinations.Redirect.Notebook(NotebookSection.HIGHLIGHTS),
            Destinations.redirectFor("annotations")
        )
        assertEquals(Destinations.Redirect.Notebook(NotebookSection.WORDS), Destinations.redirectFor("dictionary"))
        assertNull(Destinations.redirectFor("library"))
    }

    @Test
    fun notebookRoutesNameTheirSection() {
        assertEquals("notebook", Destinations.notebook())
        assertEquals("notebook?section=WORDS", Destinations.notebook(NotebookSection.WORDS))
        assertEquals(NotebookSection.WORDS, Destinations.sectionOf("WORDS"))
        assertEquals(NotebookSection.HIGHLIGHTS, Destinations.sectionOf(null))
        assertEquals(NotebookSection.HIGHLIGHTS, Destinations.sectionOf("nonsense"))
    }
}
