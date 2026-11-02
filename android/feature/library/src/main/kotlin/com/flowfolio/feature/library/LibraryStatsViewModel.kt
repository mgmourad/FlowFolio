package com.flowfolio.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.AnnotationRepository
import com.flowfolio.core.data.BookmarkRepository
import com.flowfolio.core.data.ReadingRepository
import com.flowfolio.core.data.TranslationRepository
import com.flowfolio.core.data.flow.ReadingViews
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** The library header's totals beyond its books (owner, 2026-10-23): ribbons, annotations and time read. */
data class LibraryStats(
    val bookmarks: Int = 0,
    val annotations: Int = 0,
    val readMillis: Long = 0,
    /** Time read in the Continue reading book. */
    val heroReadMillis: Long = 0,
    val heroBookmarks: Int = 0,
    val heroAnnotations: Int = 0,
    val heroDictionary: Int = 0,
    /** Words in the dictionary: translated so far (and defined, from M3T3). */
    val dictionary: Int = 0
)

@HiltViewModel
class LibraryStatsViewModel @Inject constructor(
    private val bookmarks: BookmarkRepository,
    private val annotations: AnnotationRepository,
    private val reading: ReadingRepository,
    views: ReadingViews,
    private val translations: TranslationRepository
) : ViewModel() {
    // Each book's current view only (ADR-0045), as the Annotations and Bookmarks tabs count them.
    val stats: StateFlow<LibraryStats> = combine(
        views.bookmarks(bookmarks.observeAll()),
        views.annotations(annotations.observeAll()),
        reading.observeLibraryReadMillis(),
        translations.wordCount()
    ) { ribbons, notes, millis, words -> LibraryStats(ribbons.size, notes.size, millis, dictionary = words) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), LibraryStats())

    fun bookBookmarks(bookId: String): Flow<Int> = bookmarks.observeBookmarkCount(bookId)

    fun bookAnnotations(bookId: String): Flow<Int> = annotations.observeAnnotationCount(bookId)

    fun bookDictionary(bookId: String): Flow<Int> = translations.observeDictionaryCount(bookId)

    /** Time read in [bookId], in milliseconds. */
    fun bookReadMillis(bookId: String): Flow<Long> = reading.observeReadMillis(bookId)

    fun observeBookReadMillis(bookId: String): Flow<Long> = reading.observeReadMillis(bookId)

    private companion object {
        const val STOP_MS = 5_000L
    }
}

/** The library's totals, with the time read in the Continue reading book ([heroId]), kept up to date. */
@Composable
internal fun rememberLibraryStats(stats: LibraryStatsViewModel, heroId: String?): LibraryStats {
    val totals by stats.stats.collectAsStateWithLifecycle()
    val heroRead by remember(heroId) {
        heroId?.let(stats::observeBookReadMillis) ?: flowOf(0L)
    }.collectAsStateWithLifecycle(0L)
    val heroBookmarks by remember(heroId) {
        heroId?.let(stats::bookBookmarks) ?: flowOf(0)
    }.collectAsStateWithLifecycle(0)
    val heroAnnotations by remember(heroId) {
        heroId?.let(stats::bookAnnotations) ?: flowOf(0)
    }.collectAsStateWithLifecycle(0)
    val heroDictionary by remember(heroId) {
        heroId?.let(stats::bookDictionary) ?: flowOf(0)
    }.collectAsStateWithLifecycle(0)

    return totals.copy(
        heroReadMillis = heroRead,
        heroBookmarks = heroBookmarks,
        heroAnnotations = heroAnnotations,
        heroDictionary = heroDictionary
    )
}
