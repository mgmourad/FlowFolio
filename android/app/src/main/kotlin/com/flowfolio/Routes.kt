package com.flowfolio

import com.flowfolio.feature.library.AnnotationsViewModel
import com.flowfolio.feature.library.BookmarksViewModel
import com.flowfolio.feature.library.DetailsViewModel
import com.flowfolio.feature.library.ShelfPageViewModel

internal object Routes {
    const val READER = "reader/{bookId}?layout={layout}"
    const val DETAILS = "book/{${DetailsViewModel.ARG_BOOK_ID}}?from={from}"
    const val EDIT = "book/{${DetailsViewModel.ARG_BOOK_ID}}/edit"

    fun reader(bookId: String, layout: String? = null) = "reader/$bookId" + (layout?.let { "?layout=$it" } ?: "")

    fun details(bookId: String, origin: String) = "book/$bookId?from=$origin"

    fun edit(bookId: String) = "book/$bookId/edit"

    const val BOOK_BOOKMARKS = "book/{${BookmarksViewModel.ARG_BOOK_ID}}/bookmarks"
    const val BOOK_ANNOTATIONS = "book/{${AnnotationsViewModel.ARG_BOOK_ID}}/annotations"

    fun bookAnnotations(bookId: String) = "book/$bookId/annotations"

    fun bookBookmarks(bookId: String) = "book/$bookId/bookmarks"

    const val SHELF = "shelf/{${ShelfPageViewModel.ARG_SHELF_ID}}"

    fun isReader(route: String?) = route?.startsWith("reader/") == true

    /**
     * Over the details page's blurred cover the theme's colour would read as a band (owner, 2026-10-23): a lighter tint
     * there, and the blur does the rest.
     */
    fun topFadeOf(route: String?) = if (route == DETAILS) DETAILS_FADE else 1f

    private const val DETAILS_FADE = 0.45f

    fun shelf(id: Long) = "shelf/$id"
}
