package com.flowfolio.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.model.AppStyle
import com.flowfolio.core.model.ControlsAutoHide
import com.flowfolio.core.model.FlowCarry
import com.flowfolio.core.model.MaterialSeed
import com.flowfolio.core.model.PageTurn
import com.flowfolio.core.model.ReaderText
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
    val onAppStyle: (AppStyle) -> Unit = {},
    val onMaterialSeed: (MaterialSeed) -> Unit = {},
    val onGlow: (Boolean) -> Unit = {},
    val onPageTurn: (PageTurn) -> Unit = {},
    val onSpreads: (Boolean) -> Unit = {},
    val onCoverAlone: (Boolean) -> Unit = {},
    val onStorageMode: (StorageMode) -> Unit = {},
    val onAskEachImport: (Boolean) -> Unit = {},
    val onLookup: (Boolean) -> Unit = {},
    val onAutoHide: (ControlsAutoHide) -> Unit = {},
    val onTurnSound: (Boolean) -> Unit = {},
    val onTurnVolume: (Float) -> Unit = {},
    val onRiffleSound: (Boolean) -> Unit = {},
    val onRiffleVolume: (Float) -> Unit = {},
    val onScrubHaptics: (Boolean) -> Unit = {},
    val onReaderText: (ReaderText) -> Unit = {},
    val onFlowOnOpen: (Boolean) -> Unit = {},
    val onFlowAsk: (Boolean) -> Unit = {}
)

// One setter per setting, each a line: splitting the class would only scatter them.
@Suppress("TooManyFunctions")
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

    fun setAppStyle(style: AppStyle) = viewModelScope.launch { repo.setAppStyle(style) }

    fun setMaterialSeed(seed: MaterialSeed) = viewModelScope.launch { repo.setMaterialSeed(seed) }

    fun setGlow(on: Boolean) = viewModelScope.launch { repo.setNeonGlow(on) }

    fun setLookup(on: Boolean) = viewModelScope.launch { repo.setOnlineLookup(on) }

    fun setFlowOnOpen(on: Boolean) = viewModelScope.launch { repo.setDynamicFlowOnOpen(on) }

    /** Ask again when switching views; off keeps converting the changes without asking (the safe answer). */
    fun setFlowAsk(on: Boolean) = viewModelScope.launch {
        repo.setFlowCarry(if (on) FlowCarry.ASK else FlowCarry.CONVERT)
    }

    fun setPageTurn(turn: PageTurn) = viewModelScope.launch { repo.setPageTurn(turn) }

    fun setSpreads(on: Boolean) = viewModelScope.launch { repo.setReaderSpreads(on) }

    fun setCoverAlone(on: Boolean) = viewModelScope.launch { repo.setSpreadCoverAlone(on) }

    fun setAutoHide(value: ControlsAutoHide) = viewModelScope.launch { repo.setControlsAutoHide(value) }

    fun setTurnSound(on: Boolean) = viewModelScope.launch { repo.setTurnSound(on) }

    fun setTurnVolume(volume: Float) = viewModelScope.launch { repo.setTurnVolume(volume) }

    fun setRiffleSound(on: Boolean) = viewModelScope.launch { repo.setRiffleSound(on) }

    fun setRiffleVolume(volume: Float) = viewModelScope.launch { repo.setRiffleVolume(volume) }

    fun setScrubHaptics(on: Boolean) = viewModelScope.launch { repo.setScrubHaptics(on) }

    fun setReaderText(text: ReaderText) = viewModelScope.launch { repo.setReaderText(text) }

    private companion object {
        const val STOP_MS = 5_000L
    }
}
