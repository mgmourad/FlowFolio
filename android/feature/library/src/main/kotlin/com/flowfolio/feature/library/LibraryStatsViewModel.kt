package com.flowfolio.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.AnnotationRepository
import com.flowfolio.core.data.BookmarkRepository
import com.flowfolio.core.data.ReadingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The library header's totals beyond its books (owner, 2026-10-23): ribbons, annotations and time read. */
data class LibraryStats(val bookmarks: Int = 0, val annotations: Int = 0, val readMillis: Long = 0)

@HiltViewModel
class LibraryStatsViewModel @Inject constructor(
    bookmarks: BookmarkRepository,
    annotations: AnnotationRepository,
    reading: ReadingRepository
) : ViewModel() {
    val stats: StateFlow<LibraryStats> = combine(
        bookmarks.observeAll(),
        annotations.observeAll(),
        reading.observeLibraryReadMillis()
    ) { ribbons, notes, millis -> LibraryStats(ribbons.size, notes.size, millis) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), LibraryStats())

    private companion object {
        const val STOP_MS = 5_000L
    }
}
