package com.flowfolio.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the reader's "Aa" sheet. The reader itself observes the same settings and re-lays the book out. */
@HiltViewModel
class ReaderSettingsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<UserSettings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UserSettings())

    fun setSpreads(on: Boolean) {
        viewModelScope.launch { repository.setReaderSpreads(on) }
    }

    fun setCoverAlone(on: Boolean) {
        viewModelScope.launch { repository.setSpreadCoverAlone(on) }
    }

    fun setNightPages(on: Boolean) {
        viewModelScope.launch { repository.setNightPages(on) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
