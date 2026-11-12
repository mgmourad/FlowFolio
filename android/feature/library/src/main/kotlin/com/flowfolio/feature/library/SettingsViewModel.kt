package com.flowfolio.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.Ambience
import com.flowfolio.core.data.Maintenance
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.data.UpdateFailure
import com.flowfolio.core.data.UpdateState
import com.flowfolio.core.data.UpdateUnavailable
import com.flowfolio.core.data.Updates
import com.flowfolio.core.model.AppStyle
import com.flowfolio.core.model.Atmosphere
import com.flowfolio.core.model.ControlsAutoHide
import com.flowfolio.core.model.FlowCarry
import com.flowfolio.core.model.MaterialSeed
import com.flowfolio.core.model.NarratorSkip
import com.flowfolio.core.model.PageTurn
import com.flowfolio.core.model.ReaderText
import com.flowfolio.core.model.StatsBand
import com.flowfolio.core.model.StorageMode
import com.flowfolio.core.model.ThemePreference
import com.flowfolio.core.model.UserSettings
import com.flowfolio.core.model.allAtmospheres
import com.flowfolio.core.model.atmosphereInForce
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the settings screen can change. */
data class SettingsActions(
    val onTheme: (ThemePreference) -> Unit = {},
    val onAppStyle: (AppStyle) -> Unit = {},
    val onMaterialSeed: (MaterialSeed) -> Unit = {},
    /** The library's stats band: collapsed, expanded or hidden (ADR-0052). */
    val onStatsBand: (StatsBand) -> Unit = {},
    val onNarratorSkip: (NarratorSkip) -> Unit = {},
    val onOcrDownload: () -> Unit = {},
    val onOcrRemove: () -> Unit = {},
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
    val onFlowAsk: (Boolean) -> Unit = {},
    /** Reading Atmosphere (owner, M5). */
    val onAtmosphereOn: (Boolean) -> Unit = {},
    val onAtmosphere: (String?) -> Unit = {},
    val onAtmosphereSave: (Atmosphere, String?) -> Unit = { _, _ -> },
    val onAtmosphereVolume: (Float) -> Unit = {},
    val onAtmosphereDelete: (String) -> Unit = {},
    /** Housekeeping and updates (owner, M5 round 4). */
    val onClearCache: () -> Unit = {},
    val onResetApp: () -> Unit = {},
    val onCheckUpdates: () -> Unit = {},
    val onDownloadUpdate: () -> Unit = {},
    val onInstallUpdate: () -> Unit = {},
    val onDismissUpdate: () -> Unit = {},
    val onAutoUpdate: (Boolean) -> Unit = {},
    /** Listening to a stream address before keeping it (owner, M5 round). */
    val onAudition: (String) -> Unit = {},
    val onAuditionStop: () -> Unit = {},
    /** The tutorial, first-run windows and tips (owner, M5). */
    val onGuideSeen: (String) -> Unit = {},
    val onGuidesReset: () -> Unit = {},
    val onReplayTour: () -> Unit = {}
)

/** How trying a stream address went: nothing yet, fetching, playing, or it could not be played. */
enum class Audition {
    IDLE,
    TRYING,
    PLAYING,
    FAILED
}

/** Quiet enough not to startle whoever presses listen in a room at night. */
private const val AUDITION_VOLUME = 0.6f

// One setter per setting, each a line: splitting the class would only scatter them.
@Suppress("TooManyFunctions")
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val ambience: Ambience,
    private val maintenance: Maintenance,
    private val updates: Updates,
    private val ocrPack: com.flowfolio.core.data.ocr.OcrPack
) : ViewModel() {
    // ---- Scanned books (Phase 7, ADR-0054) ----

    /** Whether the scanned-books pack is installed, downloading or missing. */
    val ocrPackState: StateFlow<com.flowfolio.core.data.ocr.OcrPackState> = ocrPack.state

    /** The pack's download size, for its row. */
    val ocrPackBytes: Long = ocrPack.sizeBytes

    fun downloadOcrPack() {
        viewModelScope.launch { ocrPack.download() }
    }

    fun removeOcrPack() {
        viewModelScope.launch { ocrPack.remove() }
    }

    // ---- Housekeeping and updates (owner, M5 round 4) ----

    private val _cacheBytes = MutableStateFlow(0L)

    /** What the cache is holding, so the button that empties it can say how much that is worth. */
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)

    /** How looking for a newer build is getting on. */
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    /** The build running now, for the line above the update button. */
    val installedVersion: String = updates.installedName()

    private var updating: Job? = null

    init {
        measureCache()
        // Opening Settings shows where things stand (the app checks on every opening; installing is the app's).
        check(download = false)
    }

    fun measureCache() {
        viewModelScope.launch { _cacheBytes.value = maintenance.cacheBytes() }
    }

    fun clearCache() {
        viewModelScope.launch {
            maintenance.clearCache()
            _cacheBytes.value = maintenance.cacheBytes()
        }
    }

    /** Empties the library and everything made in it; settings stay. */
    fun resetApp() {
        viewModelScope.launch {
            maintenance.reset()
            _cacheBytes.value = maintenance.cacheBytes()
        }
    }

    fun setAutoUpdate(on: Boolean) {
        viewModelScope.launch { repo.setAutoUpdate(on) }
    }

    /** Asks the releases page whether anything newer is published. */
    fun checkForUpdates() = check(download = false)

    /** Install: fetches the APK the last check found and hands it to Android, which asks before installing. */
    fun downloadUpdate() {
        val release = (_update.value as? UpdateState.Available)?.release ?: return
        updating?.cancel()
        updating = viewModelScope.launch {
            _update.value = UpdateState.Downloading(release, 0f)
            val file = runCatching {
                updates.download(release) { _update.value = UpdateState.Downloading(release, it) }
            }
            _update.value = file.getOrNull()
                ?.let { UpdateState.Ready(release, it) }
                ?: UpdateState.Failed(UpdateFailure.DOWNLOAD)
            installUpdate()
        }
    }

    /** Hands the downloaded build to Android, which asks before installing it. */
    fun installUpdate() {
        val ready = _update.value as? UpdateState.Ready ?: return
        if (!updates.installSession(ready.file, silent = false)) updates.openReleasesPage()
    }

    fun dismissUpdate() {
        updating?.cancel()
        _update.value = UpdateState.Idle
    }

    private fun check(download: Boolean) {
        if (updating?.isActive == true) return
        _update.value = UpdateState.Checking
        updating = viewModelScope.launch {
            val found = runCatching { updates.latest() }
            val release = found.getOrNull()
            _update.value = when {
                found.isFailure -> UpdateState.Failed(reasonOf(found.exceptionOrNull()))
                release == null -> UpdateState.UpToDate
                else -> UpdateState.Available(release)
            }
            if (download && release != null) downloadUpdate()
        }
    }

    /** A check that failed keeps the reason it was given; anything else was the body, not the connection. */
    private fun reasonOf(cause: Throwable?): UpdateFailure =
        (cause as? UpdateUnavailable)?.reason ?: UpdateFailure.UNEXPECTED

    private val _audition = MutableStateFlow(Audition.IDLE)

    /** How the editor's listen button is getting on. */
    val audition: StateFlow<Audition> = _audition.asStateFlow()

    /**
     * Tries [address] and leaves it playing if it works (owner, M5 round); [Ambience] does the trying.
     */
    fun audition(address: String) {
        val typed = address.trim()
        if (typed.isEmpty()) return
        _audition.value = Audition.TRYING
        viewModelScope.launch {
            val playing = ambience.audition(typed, AUDITION_VOLUME)
            _audition.value = if (playing) Audition.PLAYING else Audition.FAILED
        }
    }

    /** Stops the trial and forgets how it went. */
    fun stopAudition() {
        ambience.stop()
        _audition.value = Audition.IDLE
    }

    /** Remembers that a first-run window or tip has been seen. */
    fun markGuideSeen(id: String) {
        viewModelScope.launch { repo.markGuideSeen(id) }
    }

    /** Shows the 11-slide tour again: the app shell's [OnboardingHost] draws it once the tour is forgotten. */
    fun replayTour() {
        viewModelScope.launch { repo.forgetGuide(TOUR_SEEN) }
    }

    /** Lets every feature introduce itself again, and turns tips back on after "never show tips again". */
    fun resetGuides() {
        viewModelScope.launch {
            repo.clearGuidesSeen()
            repo.setTipsEnabled(true)
        }
    }

    /** Turns the chosen atmosphere on or off for every book. */
    fun setAtmosphereOn(on: Boolean) {
        viewModelScope.launch { repo.setAtmosphereOn(on) }
    }

    /**
     * Chooses an atmosphere and puts its page settings on at once (owner, M5), so applying one is the same as
     * having moved every slider in the "Aa" sheet by hand; the ambience follows from the reader's own settings.
     */
    fun setAtmosphere(name: String?) {
        viewModelScope.launch {
            if (name == null) {
                // "None": silence, without disturbing the page the reader has set up.
                repo.setAtmosphereName(null)
                return@launch
            }
            val settings = repo.settings.first()
            val chosen = settings.allAtmospheres().firstOrNull { it.name == name } ?: return@launch
            repo.setAtmosphereName(name)
            if (!settings.atmosphereOn) repo.setAtmosphereOn(true)
            repo.setReaderText(chosen.text)
            repo.setPaperStyle(chosen.paper)
        }
    }

    /** Keeps an atmosphere the reader made, replacing one of the same name, and puts it on. */
    fun saveAtmosphere(atmosphere: Atmosphere, replacing: String? = null) {
        viewModelScope.launch {
            val settings = repo.settings.first()
            // Renaming one that was edited drops the old name too, so an edit never leaves a twin behind.
            val gone = setOfNotNull(atmosphere.name, replacing)
            val others = settings.atmospheres.filterNot { it.name in gone }
            repo.setAtmospheres(others + atmosphere)
            repo.setAtmosphereName(atmosphere.name)
            repo.setAtmosphereOn(true)
            repo.setReaderText(atmosphere.text)
            repo.setPaperStyle(atmosphere.paper)
        }
    }

    /**
     * Throws away an atmosphere of the reader's own (owner, M5 round 3). One in force is let go of first, so the
     * page settings it put on stay where they are rather than snapping back to something else.
     */
    fun deleteAtmosphere(name: String) {
        viewModelScope.launch {
            val settings = repo.settings.first()
            if (settings.atmosphereName == name) {
                repo.setAtmosphereName(null)
                ambience.stop()
            }
            repo.setAtmospheres(settings.atmospheres.filterNot { it.name == name })
        }
    }

    /** Moves the ambience's loudness, keeping it on whichever atmosphere is in force. */
    fun setAtmosphereVolume(volume: Float) {
        viewModelScope.launch {
            val settings = repo.settings.first()
            val chosen = settings.atmosphereInForce() ?: return@launch
            val updated = chosen.copy(volume = volume, builtIn = false)
            val others = settings.atmospheres.filterNot { it.name == updated.name }
            repo.setAtmospheres(others + updated)
        }
    }

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

    fun setStatsBand(band: StatsBand) = viewModelScope.launch { repo.setStatsBand(band) }

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

    fun setNarratorSkip(skip: NarratorSkip) = viewModelScope.launch { repo.setNarratorSkip(skip) }

    fun setReaderText(text: ReaderText) = viewModelScope.launch { repo.setReaderText(text) }

    private companion object {
        const val STOP_MS = 5_000L
    }
}
