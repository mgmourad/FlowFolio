package com.flowfolio.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.AnnotationRepository
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.model.ControlsAutoHide
import com.flowfolio.core.model.PageTurn
import com.flowfolio.core.model.PaperStyle
import com.flowfolio.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the reader's "Aa" sheet. The reader itself observes the same settings and re-lays the book out. */
@HiltViewModel
class ReaderSettingsViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val repository: SettingsRepository,
    library: LibraryRepository,
    private val annotations: AnnotationRepository
) : ViewModel() {
    private val bookId: String = checkNotNull(saved[ReaderViewModel.ARG_BOOK_ID]) { "reader needs a bookId" }

    val settings: StateFlow<UserSettings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UserSettings())

    /** This book's pages run right to left (a per-book choice, owner 2026-10-18). */
    val rightToLeft: StateFlow<Boolean> = library.observeBook(bookId).map { it?.rightToLeft == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    fun setRightToLeft(on: Boolean) {
        viewModelScope.launch { annotations.setRightToLeft(bookId, on) }
    }

    fun setSpreads(on: Boolean) {
        viewModelScope.launch { repository.setReaderSpreads(on) }
    }

    fun setCoverAlone(on: Boolean) {
        viewModelScope.launch { repository.setSpreadCoverAlone(on) }
    }

    fun setPaperStyle(style: PaperStyle) {
        viewModelScope.launch { repository.setPaperStyle(style) }
    }

    fun setPageTurn(turn: PageTurn) {
        viewModelScope.launch { repository.setPageTurn(turn) }
    }

    fun setControlsAutoHide(value: ControlsAutoHide) {
        viewModelScope.launch { repository.setControlsAutoHide(value) }
    }

    fun setTurnSound(on: Boolean) {
        viewModelScope.launch { repository.setTurnSound(on) }
    }

    fun setTurnVolume(volume: Float) {
        viewModelScope.launch { repository.setTurnVolume(volume) }
    }

    fun setRiffleSound(on: Boolean) {
        viewModelScope.launch { repository.setRiffleSound(on) }
    }

    fun setRiffleVolume(volume: Float) {
        viewModelScope.launch { repository.setRiffleVolume(volume) }
    }

    fun setScrubHaptics(on: Boolean) {
        viewModelScope.launch { repository.setScrubHaptics(on) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
