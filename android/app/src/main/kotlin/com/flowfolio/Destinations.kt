package com.flowfolio

import com.flowfolio.core.data.MarkKind
import com.flowfolio.feature.library.NotebookSection

/**
 * Where the destinations retired by ADR-0052 now live. The four tabs replaced seven: Favorites became a Library
 * filter, and Bookmarks, Annotations and Dictionary became sections of the Notebook. Their old routes stay in the
 * graph and forward here, so anything still holding one (a saved back stack, an older notification, a test) lands on
 * the new home instead of nowhere.
 */
internal object Destinations {
    const val FAVORITES = "favorites"
    const val BOOKMARKS = "bookmarks"
    const val ANNOTATIONS = "annotations"
    const val DICTIONARY = "dictionary"

    /** The Notebook, optionally opened at one [NotebookSection] (`notebook?section=WORDS`). */
    const val NOTEBOOK = "notebook?section={section}"

    /** One book's notebook, optionally on one kind of mark. */
    const val NOTEBOOK_BOOK = "notebook/book/{bookId}?kind={kind}"

    fun notebookBook(bookId: String, kind: MarkKind? = null) =
        "notebook/book/$bookId" + (kind?.let { "?kind=${it.name}" } ?: "")

    fun notebook(section: NotebookSection? = null) = "notebook" + (section?.let { "?section=${it.name}" } ?: "")

    /** The retired routes and where each one goes now. */
    sealed interface Redirect {
        /** The Library with one of its chips chosen. */
        data object LibraryFavorites : Redirect

        data class Notebook(val section: NotebookSection) : Redirect
    }

    fun redirectFor(route: String?): Redirect? = when (route) {
        FAVORITES -> Redirect.LibraryFavorites
        BOOKMARKS -> Redirect.Notebook(NotebookSection.BOOKMARKS)
        ANNOTATIONS -> Redirect.Notebook(NotebookSection.HIGHLIGHTS)
        DICTIONARY -> Redirect.Notebook(NotebookSection.WORDS)
        else -> null
    }

    /** The section a `notebook?section=` argument names; anything unknown opens on the first. */
    fun sectionOf(argument: String?): NotebookSection =
        NotebookSection.entries.firstOrNull { it.name == argument } ?: NotebookSection.entries.first()

    /** The tab a route belongs to, ignoring its arguments; the retired routes count as their new homes' tabs. */
    fun tabOf(route: String?): TopLevel? {
        val base = route?.substringBefore('?')
        return TopLevel.entries.firstOrNull { it.route == base } ?: when (redirectFor(base)) {
            Redirect.LibraryFavorites -> TopLevel.Library
            is Redirect.Notebook -> TopLevel.Notebook
            null -> null
        }
    }
}
