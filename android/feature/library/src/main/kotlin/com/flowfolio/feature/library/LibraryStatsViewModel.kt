package com.flowfolio.feature.library

import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.AnnotationRepository
import com.flowfolio.core.data.BookmarkRepository
import com.flowfolio.core.data.ReadingRepository
import com.flowfolio.core.data.flow.ReadingViews
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The library header's totals beyond its books (owner, 2026-10-23): ribbons, annotations and time read. */
data class LibraryStats(
    val bookmarks: Int = 0,
    val annotations: Int = 0,
    val readMillis: Long = 0,
    /** Time read in the Continue reading book. */
    val heroReadMillis: Long = 0
)

@HiltViewModel
class LibraryStatsViewModel @Inject constructor(
    bookmarks: BookmarkRepository,
    annotations: AnnotationRepository,
    reading: ReadingRepository,
    views: ReadingViews
) : ViewModel() {
    // Each book's current view only (ADR-0045), as the Annotations and Bookmarks tabs count them.
    val stats: StateFlow<LibraryStats> = combine(
        views.bookmarks(bookmarks.observeAll()),
        views.annotations(annotations.observeAll()),
        reading.observeLibraryReadMillis()
    ) { ribbons, notes, millis -> LibraryStats(ribbons.size, notes.size, millis) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), LibraryStats())

    private val reading = reading

    /** Time read in [bookId], in milliseconds. */
    fun bookReadMillis(bookId: String): kotlinx.coroutines.flow.Flow<Long> = reading.observeReadMillis(bookId)

    private companion object {
        const val STOP_MS = 5_000L
    }
}

/** The library's totals, with the time read in the Continue reading book ([heroId]), kept up to date. */
@androidx.compose.runtime.Composable
internal fun rememberLibraryStats(stats: LibraryStatsViewModel, heroId: String?): LibraryStats {
    val totals by stats.stats.collectAsStateWithLifecycle()
    val hero by androidx.compose.runtime.remember(heroId) {
        heroId?.let(stats::bookReadMillis) ?: kotlinx.coroutines.flow.flowOf(0L)
    }.collectAsStateWithLifecycle(0L)
    return totals.copy(heroReadMillis = hero)
}
