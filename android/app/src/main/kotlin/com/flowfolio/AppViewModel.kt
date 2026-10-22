package com.flowfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.ReaderRepository
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.model.AppStyle
import com.flowfolio.core.model.MaterialSeed
import com.flowfolio.core.model.ThemePreference
import com.flowfolio.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the app looks like (the theme reveal plays when any of it changes). */
data class AppLook(
    val mode: ThemePreference = ThemePreference.SYSTEM,
    val style: AppStyle = AppStyle.FLOWFOLIO,
    val seed: MaterialSeed = MaterialSeed.WALLPAPER
)

/** Activity-scoped state: app theme, and files shared into the app waiting for the library to import them. */
@HiltViewModel
class AppViewModel @Inject constructor(settings: SettingsRepository, private val reader: ReaderRepository) :
    ViewModel() {
    /** The app's look: light or dark, the theme (ADR-0028) and Material You's colours. */
    val theme: StateFlow<AppLook> =
        settings.settings.map { AppLook(it.theme, it.appStyle, it.materialSeed) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, AppLook())

    /** The reader's settings, so the book-opening animation opens onto the page the reader will show. */
    val readerSettings: StateFlow<UserSettings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    private val shared = Channel<String>(Channel.BUFFERED)
    val sharedFiles: Flow<String> = shared.receiveAsFlow()

    fun onSharedFile(uri: String) {
        shared.trySend(uri)
    }

    private val directOpen = Channel<Pair<String, String?>>(Channel.BUFFERED)

    /** Debug builds only: open a book straight from an adb intent (measurement scripts). */
    val directOpens: Flow<Pair<String, String?>> = directOpen.receiveAsFlow()

    /** [startPage] (0-based), when given, becomes the saved reading position first, so runs start in a known place. */
    fun onDirectOpen(bookId: String, layout: String?, startPage: Int? = null) {
        viewModelScope.launch {
            if (startPage != null) reader.saveProgress(bookId, startPage)
            directOpen.send(bookId to layout)
        }
    }
}
