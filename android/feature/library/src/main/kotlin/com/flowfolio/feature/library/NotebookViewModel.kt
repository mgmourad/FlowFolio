package com.flowfolio.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.AllMarks
import com.flowfolio.core.data.AnnotationRepository
import com.flowfolio.core.data.BookMarkCounts
import com.flowfolio.core.data.BookmarkRepository
import com.flowfolio.core.data.DictionaryRepository
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.MarkCounts
import com.flowfolio.core.data.MarkKind
import com.flowfolio.core.data.ReaderRepository
import com.flowfolio.core.data.VoicePlayer
import com.flowfolio.core.data.kind
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.Bookmark
import com.flowfolio.core.model.DictionaryWord
import com.flowfolio.core.model.LibrarySort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How the Notebook orders its books (ADR-0052). */
enum class NotebookSort { RECENT, TITLE, MOST }

/** How a book's notebook page orders its marks (ADR-0052). */
enum class EntrySort { PAGE, NEWEST, TYPE }

/** One mark in a book's notebook: a highlight, note or VoiceMark ([annotation]), a ribbon, or a word. */
data class NotebookEntry(
    val key: String,
    val kind: MarkKind,
    val bookId: String,
    val page: Int?,
    val createdAtMillis: Long,
    val annotation: Annotation? = null,
    val bookmark: Bookmark? = null,
    val word: DictionaryWord? = null
) {
    /** The words a search or a share reads: the quote and note, or the word and what it means. */
    val text: String
        get() = annotation?.let { listOfNotNull(it.text, it.note).joinToString(" — ") }
            ?: word?.let { listOfNotNull(it.word, it.definition, it.translation).joinToString(" — ") }
            ?: ""
}

/** A book in the Notebook: its counts by kind and its latest mark's words. */
data class NotebookBook(val book: Book, val counts: BookMarkCounts, val latest: String?, val lastMarked: Long)

data class NotebookUiState(
    val loading: Boolean = true,
    /** The books with marks of the [filter]'s kind (or any), in [sort] order, matching [query]. */
    val books: List<NotebookBook> = emptyList(),
    /** Every kind's total across the library, for the chips. */
    val totals: BookMarkCounts = BookMarkCounts(),
    val filter: MarkKind? = null,
    val sort: NotebookSort = NotebookSort.RECENT,
    val query: String = ""
)

/**
 * The Notebook (ADR-0052): everything marked, book by book, counted through [MarkCounts] so every screen agrees.
 * It also serves a book's own notebook page ([entriesOf]), opens a mark where it is, removes marks with Undo, and
 * writes the whole notebook out as Markdown or CSV.
 */
@Suppress("LongParameterList") // the repositories a notebook reads and changes
@HiltViewModel
class NotebookViewModel @Inject constructor(
    saved: SavedStateHandle,
    marks: MarkCounts,
    library: LibraryRepository,
    private val annotations: AnnotationRepository,
    private val bookmarks: BookmarkRepository,
    private val dictionary: DictionaryRepository,
    private val reader: ReaderRepository,
    private val voice: VoicePlayer
) : ViewModel() {
    private val view = MutableStateFlow(
        View(filter = sectionFilter(saved.get<String>(ARG_SECTION)))
    )

    /** Marks removed but still undoable: hidden at once, deleted for good when the snackbar goes (ADR-0052). */
    private val pending = MutableStateFlow<Set<String>>(emptySet())

    val all: StateFlow<AllMarks> = combine(marks.all, pending) { m, hidden ->
        if (hidden.isEmpty()) {
            m
        } else {
            AllMarks(
                m.annotations.filterNot { "a${it.id}" in hidden },
                m.bookmarks.filterNot { "b${it.id}" in hidden },
                m.words.filterNot { "w${it.key}" in hidden }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AllMarks())

    val books: StateFlow<List<Book>> = library.observeBooks(LibrarySort.TITLE)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uiState: StateFlow<NotebookUiState> = combine(all, books, view) { m, list, v ->
        build(m, list, v.filter, v.sort, v.query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), NotebookUiState())

    fun onFilter(kind: MarkKind?) = view.update { it.copy(filter = kind) }

    fun onSort(sort: NotebookSort) = view.update { it.copy(sort = sort) }

    fun onQuery(q: String) = view.update { it.copy(query = q) }

    /** A book's marks, of [kind] or all, in [sort] order. */
    fun entriesOf(bookId: String, m: AllMarks, kind: MarkKind?, sort: EntrySort): List<NotebookEntry> =
        entries(bookId, m).filter { kind == null || it.kind == kind }.sortedWith(sort.comparator)

    /** Makes the mark's page the reading position, then [then] opens the reader there. */
    fun open(entry: NotebookEntry, then: () -> Unit) = viewModelScope.launch {
        val locator = entry.annotation?.locator ?: entry.bookmark?.locator ?: entry.word?.locator
        val page = entry.page
        when {
            locator != null && page != null -> reader.saveEpubPosition(entry.bookId, locator, page)
            page != null && page >= 0 -> reader.saveProgress(entry.bookId, page)
        }
        then()
    }

    /** Hides [entry] now; [commit] deletes it for good, [undo] brings it back. */
    fun remove(entry: NotebookEntry): Pair<() -> Unit, () -> Unit> {
        pending.update { it + entry.key }
        val commit = {
            viewModelScope.launch {
                entry.annotation?.let { annotations.remove(it.id) }
                entry.bookmark?.let { bookmarks.remove(it.id) }
                entry.word?.let { dictionary.removeWord(it) }
                pending.update { it - entry.key }
            }
            Unit
        }
        val undo = { pending.update { it - entry.key } }
        return commit to undo
    }

    fun play(entry: NotebookEntry, onEnd: () -> Unit) {
        entry.annotation?.voice?.let { voice.play(it.path, onEnd) }
    }

    fun stopVoice() = voice.stop()

    override fun onCleared() {
        voice.stop()
    }

    private data class View(
        val filter: MarkKind? = null,
        val sort: NotebookSort = NotebookSort.RECENT,
        val query: String = ""
    )

    companion object {
        const val ARG_SECTION = "section"
        private const val STOP_MS = 5_000L

        /** The kind a `notebook?section=` route opens on: a [NotebookSection] or a [MarkKind] name. */
        fun sectionFilter(section: String?): MarkKind? = when (section) {
            null -> null
            NotebookSection.HIGHLIGHTS.name -> MarkKind.HIGHLIGHT
            NotebookSection.BOOKMARKS.name -> MarkKind.BOOKMARK
            NotebookSection.WORDS.name -> MarkKind.WORD
            else -> MarkKind.entries.firstOrNull { it.name == section }
        }

        /** Every mark of [bookId] as a [NotebookEntry]. */
        fun entries(bookId: String, m: AllMarks): List<NotebookEntry> =
            m.annotations.filter { it.bookId == bookId }.map {
                NotebookEntry("a${it.id}", it.kind, bookId, it.page, it.createdAtMillis, annotation = it)
            } + m.bookmarks.filter { it.bookId == bookId }.map {
                NotebookEntry("b${it.id}", MarkKind.BOOKMARK, bookId, it.page, it.createdAtMillis, bookmark = it)
            } + m.words.filter { it.bookId == bookId }.map {
                NotebookEntry("w${it.key}", MarkKind.WORD, bookId, it.page, it.updatedAtMillis, word = it)
            }

        /** The landing's state: the books that have marks (of [filter]'s kind), sorted and searched. */
        fun build(
            m: AllMarks,
            books: List<Book>,
            filter: MarkKind?,
            sort: NotebookSort,
            query: String
        ): NotebookUiState {
            val counts = m.byBook
            val q = query.trim()
            val rows = books.mapNotNull { book ->
                val c = counts[book.id] ?: return@mapNotNull null
                if (c.total == 0 || (filter != null && c[filter] == 0)) return@mapNotNull null
                val own = entries(book.id, m)
                if (q.isNotEmpty() && !book.title.contains(q, true) && own.none { it.text.contains(q, true) }) {
                    return@mapNotNull null
                }
                val newest = own.maxByOrNull { it.createdAtMillis }
                NotebookBook(book, c, newest?.text?.takeIf { it.isNotBlank() }, newest?.createdAtMillis ?: 0L)
            }
            val sorted = when (sort) {
                NotebookSort.RECENT -> rows.sortedByDescending { it.lastMarked }
                NotebookSort.TITLE -> rows.sortedBy { it.book.title.lowercase() }
                NotebookSort.MOST -> rows.sortedByDescending { it.counts.total }
            }
            val totals = counts.filterKeys { id -> books.any { it.id == id } }.values
                .fold(BookMarkCounts()) { acc, c -> acc + c }
            return NotebookUiState(false, sorted, totals, filter, sort, query)
        }
    }
}

private val EntrySort.comparator: Comparator<NotebookEntry>
    get() = when (this) {
        EntrySort.PAGE -> compareBy<NotebookEntry> { it.page ?: Int.MAX_VALUE }.thenBy { it.createdAtMillis }
        EntrySort.NEWEST -> compareByDescending { it.createdAtMillis }
        EntrySort.TYPE -> compareBy<NotebookEntry> { it.kind.ordinal }.thenBy { it.page ?: Int.MAX_VALUE }
    }

/** The whole notebook as Markdown: a heading per book, a line per mark. */
internal fun notebookMarkdown(books: List<Book>, m: AllMarks): String = buildString {
    append("# FlowFolio Notebook\n")
    books.forEach { book ->
        val own = NotebookViewModel.entries(book.id, m).sortedWith(EntrySort.PAGE.comparator)
        if (own.isEmpty()) return@forEach
        append("\n## ").append(book.title).append('\n')
        book.author?.let { append('*').append(it).append("*\n") }
        append('\n')
        own.forEach { e ->
            val page = e.page?.let { "p. ${it + 1}" } ?: ""
            when (e.kind) {
                MarkKind.BOOKMARK -> append("- Ribbon · ").append(page).append('\n')

                MarkKind.WORD -> append("- **").append(e.word?.word).append("** — ")
                    .append(e.word?.definition ?: e.word?.translation ?: "").append(" (").append(page).append(")\n")

                else -> {
                    append("> ").append(e.annotation?.text?.replace("\n", " ")).append('\n')
                    e.annotation?.note?.takeIf { it.isNotBlank() }?.let { append(">\n> — ").append(it).append('\n') }
                    append("\n").append(page).append('\n')
                }
            }
            append('\n')
        }
    }
}

/** The whole notebook as CSV: book, kind, page, text, note, created. */
internal fun notebookCsv(books: List<Book>, m: AllMarks): String = buildString {
    append("book,kind,page,text,note,created\n")
    fun cell(s: String?) = "\"" + (s ?: "").replace("\"", "\"\"") + "\""
    books.forEach { book ->
        NotebookViewModel.entries(book.id, m).sortedWith(EntrySort.PAGE.comparator).forEach { e ->
            val text = e.annotation?.text ?: e.word?.word ?: ""
            val note = e.annotation?.note ?: e.word?.definition ?: e.word?.translation
            append(cell(book.title)).append(',').append(e.kind.name.lowercase()).append(',')
                .append(e.page?.plus(1) ?: "").append(',').append(cell(text)).append(',').append(cell(note))
                .append(',').append(e.createdAtMillis).append('\n')
        }
    }
}
