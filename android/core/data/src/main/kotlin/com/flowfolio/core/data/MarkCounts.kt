package com.flowfolio.core.data

import com.flowfolio.core.data.flow.ReadingViews
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.Bookmark
import com.flowfolio.core.model.DictionaryWord
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** The kinds of mark a reader leaves in a book (ADR-0052), as the Notebook shows them. */
enum class MarkKind { HIGHLIGHT, NOTE, BOOKMARK, VOICE, WORD }

/** A book's marks by kind. */
data class BookMarkCounts(
    val highlights: Int = 0,
    val notes: Int = 0,
    val bookmarks: Int = 0,
    val voiceMarks: Int = 0,
    val words: Int = 0
) {
    /** Highlights, notes and VoiceMarks together: what the library calls annotations. */
    val annotations: Int get() = highlights + notes + voiceMarks

    val total: Int get() = annotations + bookmarks + words

    operator fun get(kind: MarkKind): Int = when (kind) {
        MarkKind.HIGHLIGHT -> highlights
        MarkKind.NOTE -> notes
        MarkKind.BOOKMARK -> bookmarks
        MarkKind.VOICE -> voiceMarks
        MarkKind.WORD -> words
    }

    operator fun plus(other: BookMarkCounts) = BookMarkCounts(
        highlights + other.highlights,
        notes + other.notes,
        bookmarks + other.bookmarks,
        voiceMarks + other.voiceMarks,
        words + other.words
    )
}

/** Which kind an annotation is: a VoiceMark when it has a recording, a note when it has words, else a highlight. */
val Annotation.kind: MarkKind
    get() = when {
        voice != null -> MarkKind.VOICE
        !note.isNullOrBlank() -> MarkKind.NOTE
        else -> MarkKind.HIGHLIGHT
    }

/** Everything marked in every book, each book's highlights and ribbons in its current view only (ADR-0045). */
data class AllMarks(
    val annotations: List<Annotation> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val words: List<DictionaryWord> = emptyList()
) {
    /** Each book's marks by kind: the one count the Library, Notebook, Shelves and Details all show (ADR-0052). */
    val byBook: Map<String, BookMarkCounts> by lazy { countByBook(annotations, bookmarks, words) }
}

/**
 * The one source of a book's mark counts (ADR-0052). The review found the library card saying 5 ribbons, the
 * Bookmarks page 2 and Details 2 for the same book: some counted every ribbon, some only the current view's. Every
 * screen now counts through here, after [ReadingViews], so they cannot disagree.
 */
class MarkCounts @Inject constructor(
    views: ReadingViews,
    annotations: AnnotationRepository,
    bookmarks: BookmarkRepository,
    dictionary: DictionaryRepository
) {
    val all: Flow<AllMarks> = combine(
        views.annotations(annotations.observeAll()),
        views.bookmarks(bookmarks.observeAll()),
        dictionary.observeWords()
    ) { a, b, w -> AllMarks(a, b, w) }
}

/** Counts [annotations], [bookmarks] and [words] book by book. Words saved outside any book are not counted here. */
fun countByBook(
    annotations: List<Annotation>,
    bookmarks: List<Bookmark>,
    words: List<DictionaryWord>
): Map<String, BookMarkCounts> {
    val counts = HashMap<String, BookMarkCounts>()
    fun add(bookId: String, c: BookMarkCounts) {
        counts[bookId] = (counts[bookId] ?: BookMarkCounts()) + c
    }
    annotations.forEach { a ->
        add(
            a.bookId,
            when (a.kind) {
                MarkKind.VOICE -> BookMarkCounts(voiceMarks = 1)
                MarkKind.NOTE -> BookMarkCounts(notes = 1)
                else -> BookMarkCounts(highlights = 1)
            }
        )
    }
    bookmarks.forEach { add(it.bookId, BookMarkCounts(bookmarks = 1)) }
    words.forEach { w -> w.bookId?.let { add(it, BookMarkCounts(words = 1)) } }
    return counts
}
