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
import com.flowfolio.feature.library.TOUR_SEEN
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
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
    private val settings: SettingsRepository,
    private val reader: ReaderRepository,
    private val flow: DynamicFlow,
    private val updates: Updates
) : ViewModel() {
    /** Dynamic Flow's notices for the app's banner (ADR-0045); the scan question is a dialog of its own. */
    val flowNotices: Flow<FlowNotice> = flow.notices.filter { it !is FlowNotice.ScanAsk }

    /** How far each converting book is, for the bar on its cover (TM1). */
    val conversionProgress: StateFlow<Map<String, Float>> = flow.statuses
        .map { all ->
            all.mapNotNull { (id, s) ->
                when (s) {
                    is com.flowfolio.core.model.FlowStatus.Converting -> id to s.fraction
                    com.flowfolio.core.model.FlowStatus.Queued -> id to 0f
                    else -> null
                }
            }.toMap()
        }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(STOP_MS), emptyMap())

    /** A scanned book waiting for the reader's go-ahead before OCR (TM1); null when nothing is asked. */
    private val _scanAsk = MutableStateFlow<FlowNotice.ScanAsk?>(null)
    val scanAsk: StateFlow<FlowNotice.ScanAsk?> = _scanAsk.asStateFlow()

    init {
        viewModelScope.launch {
            flow.notices.collect { if (it is FlowNotice.ScanAsk) _scanAsk.value = it }
        }
    }

    /** The scan question's answer: convert now or not, and whether to ask again for other books. */
    fun answerScan(convert: Boolean, dontAsk: Boolean) {
        val ask = _scanAsk.value ?: return
        _scanAsk.value = null
        viewModelScope.launch { flow.answerScan(ask.bookId, convert, dontAsk) }
    }

    /** A newer build found by the automatic check, shown as a banner; null once dismissed or when up to date. */
    private val _updateAvailable = MutableStateFlow<Release?>(null)
    val updateAvailable: StateFlow<Release?> = _updateAvailable.asStateFlow()

    /** A banner tap: open Settings at the update section. */
    private val goToUpdates = Channel<Unit>(Channel.BUFFERED)
    val updatesOpened: Flow<Unit> = goToUpdates.receiveAsFlow()

    /** Increments each time Settings should open scrolled to the update section (a banner or notification tap). */
    private val _updatesFocus = MutableStateFlow(0)
    val updatesFocus: StateFlow<Int> = _updatesFocus.asStateFlow()

    private var lastCheck = 0L

    /**
     * Checks for a newer build, throttled so an app opened often does not hammer the releases API (the unauthenticated
     * rate limit is generous but finite). Called when the app opens or comes back to the foreground.
     */
    fun checkForUpdates() {
        val now = System.currentTimeMillis()
        if (now - lastCheck < CHECK_INTERVAL_MS || installing?.isActive == true) return
        lastCheck = now
        installing = viewModelScope.launch {
            val release = runCatching { updates.latest() }.getOrNull() ?: return@launch
            if (!settings.settings.first().autoUpdate) {
                // Off: say so, and leave Install and What's new to Settings.
                _updateAvailable.value = release
                return@launch
            }
            // On: fetch it and install it. Android installs a self-update without asking when it allows that, and
            // otherwise shows its own prompt. The new build greets the reader with "what's new" and a banner.
            val apk = runCatching { updates.download(release) { } }.getOrNull()
            if (apk == null || !updates.installSession(apk, silent = true)) _updateAvailable.value = release
        }
    }

    /** The automatic check's fetch and install, so returning to the app mid-download does not start another. */
    private var installing: kotlinx.coroutines.Job? = null

    /** The build the app was just updated to, for the "updated" banner; null otherwise or once dismissed. */
    private val _updatedTo = MutableStateFlow<String?>(null)
    val updatedTo: StateFlow<String?> = _updatedTo.asStateFlow()

    fun dismissUpdated() {
        _updatedTo.value = null
    }

    /** The reader dismissed the banner; it stays down until the next check finds a build again. */
    fun dismissUpdate() {
        _updateAvailable.value = null
    }

    /** The release whose "what's new" notes are owed after an update; null once shown, or on a first install. */
    private val _whatsNew = MutableStateFlow<Release?>(null)
    val whatsNew: StateFlow<Release?> = _whatsNew.asStateFlow()

    /** Whether the app was just updated, and if so holds the new release's notes for the "what's new" popup. */
    fun checkWhatsNew() {
        viewModelScope.launch {
            val current = updates.installedCode()
            val seen = settings.settings.first().lastSeenVersionCode
            if (seen == 0L) {
                // First install: the "what's new" is owed too, but it waits for the tour (the dialog gates on
                // [tutorialSeen]); dismissing it remembers this build. Nothing is marked here.
                _whatsNew.value = updates.latestRelease()
                return@launch
            }
            if (current > seen) {
                _updatedTo.value = updates.installedName()
                updates.latestRelease()?.let { _whatsNew.value = it }
            }
        }
    }

    /** Whether the first-run tour is behind us, so the "what's new" popup can appear on a fresh install. */
    val tutorialSeen: StateFlow<Boolean> = settings.settings
        .map { TOUR_SEEN in it.seenGuides }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** The reader pressed "Ok": remember this build and put the popup away. */
    fun dismissWhatsNew() {
        viewModelScope.launch {
            settings.setLastSeenVersionCode(updates.installedCode())
            _whatsNew.value = null
        }
    }

    /** The banner was tapped: take the reader to Settings at the update section. */
    fun onUpdateOpen() {
        _updatesFocus.value++
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
        /**
         * At most one check a minute (owner, 2026-11-11: check on every opening and every return). GitHub allows 60
         * unauthenticated requests an hour, which this stays under.
         */
        const val CHECK_INTERVAL_MS = 60 * 1000L
    }
}

private const val STOP_MS = 5_000L
