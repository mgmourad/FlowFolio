package com.flowfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.model.ThemePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

/** Activity-scoped state: app theme, and files shared into the app waiting for the library to import them. */
@HiltViewModel
class AppViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val theme: StateFlow<ThemePreference> =
        settings.settings.map { it.theme }.stateIn(viewModelScope, SharingStarted.Eagerly, ThemePreference.SYSTEM)

    private val shared = Channel<String>(Channel.BUFFERED)
    val sharedFiles: Flow<String> = shared.receiveAsFlow()

    fun onSharedFile(uri: String) {
        shared.trySend(uri)
    }
}
