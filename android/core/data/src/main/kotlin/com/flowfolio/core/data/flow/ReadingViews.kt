package com.flowfolio.core.data.flow

import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.data.toBook
import com.flowfolio.core.database.BookDao
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.BookFormat
import com.flowfolio.core.model.Bookmark
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Which view each book is read in now (ADR-0045), for showing only that view's highlights, notes and bookmarks: the
 * books read as reflowed text are the EPUBs and the PDFs in Dynamic Flow that have been converted. Their items are the
 * ones with a locator (ADR-0040); every other book shows those without.
 */
interface ReadingViews {
    /** [items] keeping only each book's current view's highlights. */
    fun annotations(items: Flow<List<Annotation>>): Flow<List<Annotation>>

    /** [items] keeping only each book's current view's ribbons. */
    fun bookmarks(items: Flow<List<Bookmark>>): Flow<List<Bookmark>>

    /** Every item, whatever its view: for tests and fakes. */
    object ShowAll : ReadingViews {
        override fun annotations(items: Flow<List<Annotation>>) = items

        override fun bookmarks(items: Flow<List<Bookmark>>) = items
    }

    companion object {
        /** Whether an item with [locator] belongs to the view [bookId] is read in, given the [reflowed] books. */
        fun shows(bookId: String, locator: String?, reflowed: Set<String>): Boolean =
            (locator != null) == (bookId in reflowed)
    }
}

class DefaultReadingViews @Inject constructor(books: BookDao, settings: SettingsRepository, flow: DynamicFlow) :
    ReadingViews {
    /** The ids of the books read as reflowed text. */
    private val reflowed: Flow<Set<String>> = combine(books.observeByRecent(), settings.settings, flow.statuses) {
            all,
            s,
            _
        ->
        all.map { it.toBook() }.filter { b ->
            b.format == BookFormat.EPUB || (b.wantsFlow(s.dynamicFlowOnOpen) && flow.bundle(b.id) != null)
        }.map { it.id }.toSet()
    }.distinctUntilChanged()

    override fun annotations(items: Flow<List<Annotation>>): Flow<List<Annotation>> =
        combine(items, reflowed) { list, r -> list.filter { ReadingViews.shows(it.bookId, it.locator, r) } }

    override fun bookmarks(items: Flow<List<Bookmark>>): Flow<List<Bookmark>> =
        combine(items, reflowed) { list, r -> list.filter { ReadingViews.shows(it.bookId, it.locator, r) } }
}
