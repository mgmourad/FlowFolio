package com.flowfolio.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.model.PageTurn
import com.flowfolio.core.model.StorageMode
import com.flowfolio.core.model.ThemePreference
import com.flowfolio.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the settings screen can change. */
data class SettingsActions(
    val onTheme: (ThemePreference) -> Unit = {},
    val onGlow: (Boolean) -> Unit = {},
    val onPageTurn: (PageTurn) -> Unit = {},
    val onSpreads: (Boolean) -> Unit = {},
    val onCoverAlone: (Boolean) -> Unit = {},
    val onStorageMode: (StorageMode) -> Unit = {},
    val onAskEachImport: (Boolean) -> Unit = {},
    val onLookup: (Boolean) -> Unit = {}
)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<UserSettings> = repo.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_MS),
        UserSettings()
    )

    fun setStorageMode(mode: StorageMode) = viewModelScope.launch {
        repo.setStorageMode(mode)
        repo.setStorageNoticeSeen() // choosing here counts as having seen the explanation
    }

    fun setAskEachImport(ask: Boolean) = viewModelScope.launch { repo.setAskStorageEachImport(ask) }

    fun setTheme(theme: ThemePreference) = viewModelScope.launch { repo.setTheme(theme) }

    fun setGlow(on: Boolean) = viewModelScope.launch { repo.setNeonGlow(on) }

    fun setLookup(on: Boolean) = viewModelScope.launch { repo.setOnlineLookup(on) }

    fun setPageTurn(turn: PageTurn) = viewModelScope.launch { repo.setPageTurn(turn) }

    fun setSpreads(on: Boolean) = viewModelScope.launch { repo.setReaderSpreads(on) }

    fun setCoverAlone(on: Boolean) = viewModelScope.launch { repo.setSpreadCoverAlone(on) }

    private companion object {
        const val STOP_MS = 5_000L
    }
}
