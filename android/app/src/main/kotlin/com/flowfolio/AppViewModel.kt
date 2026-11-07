package com.flowfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.ReaderRepository
import com.flowfolio.core.data.Release
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.data.Updates
import com.flowfolio.core.data.flow.DynamicFlow
import com.flowfolio.core.model.AppStyle
import com.flowfolio.core.model.FlowNotice
import com.flowfolio.core.model.MaterialSeed
import com.flowfolio.core.model.ThemePreference
import com.flowfolio.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
class AppViewModel @Inject constructor(
    settings: SettingsRepository,
    private val reader: ReaderRepository,
    private val flow: DynamicFlow,
    private val updates: Updates
) : ViewModel() {
    /** Dynamic Flow's notices for the app's banner (ADR-0045). */
    val flowNotices: Flow<FlowNotice> = flow.notices

    /** A newer build found by the automatic check, shown as a banner; null once dismissed or when up to date. */
    private val _updateAvailable = MutableStateFlow<Release?>(null)
    val updateAvailable: StateFlow<Release?> = _updateAvailable.asStateFlow()

    /** A banner tap: open Settings at the update section. */
    private val goToUpdates = Channel<Unit>(Channel.BUFFERED)
    val updatesOpened: Flow<Unit> = goToUpdates.receiveAsFlow()

    private var lastCheck = 0L

    /**
     * Checks for a newer build, throttled so an app opened often does not hammer the releases API (the unauthenticated
     * rate limit is generous but finite). Called when the app opens or comes back to the foreground.
     */
    fun checkForUpdates() {
        val now = System.currentTimeMillis()
        if (now - lastCheck < CHECK_INTERVAL_MS) return
        lastCheck = now
        viewModelScope.launch {
            runCatching { updates.latest() }.getOrNull()?.let { _updateAvailable.value = it }
        }
    }

    /** The reader dismissed the banner; it stays down until the next check finds a build again. */
    fun dismissUpdate() {
        _updateAvailable.value = null
    }

    /** The banner was tapped: take the reader to Settings. */
    fun onUpdateOpen() {
        goToUpdates.trySend(Unit)
        dismissUpdate()
    }

    /** Whether to ask for Android 13's notification permission now (the first conversion). */
    val askNotifications: StateFlow<Boolean> = flow.wantsNotificationPermission

    fun notificationsAsked() = flow.notificationPermissionAsked()

    /** A Dynamic Flow notification was tapped: open its book. */
    fun onNotificationOpen(bookId: String) {
        directOpen.trySend(bookId to null)
    }

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

    private companion object {
        /** How often the automatic update check may reach the releases API (ten minutes). */
        const val CHECK_INTERVAL_MS = 10 * 60 * 1000L
    }
}
