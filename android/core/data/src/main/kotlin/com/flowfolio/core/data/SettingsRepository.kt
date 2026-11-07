package com.flowfolio.core.data

import com.flowfolio.core.datastore.SettingsDataSource
import com.flowfolio.core.model.AppStyle
import com.flowfolio.core.model.Atmosphere
import com.flowfolio.core.model.ControlsAutoHide
import com.flowfolio.core.model.FlowCarry
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.MaterialSeed
import com.flowfolio.core.model.PageTurn
import com.flowfolio.core.model.PaperStyle
import com.flowfolio.core.model.ReaderText
import com.flowfolio.core.model.StorageMode
import com.flowfolio.core.model.ThemePreference
import com.flowfolio.core.model.UserSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions") // one setter per setting
interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setStorageMode(mode: StorageMode)

    suspend fun setAskStorageEachImport(ask: Boolean)

    suspend fun setTheme(theme: ThemePreference)

    suspend fun setStorageNoticeSeen()

    suspend fun setLibrarySort(sort: LibrarySort)

    /** Reading Atmosphere (owner, M5). */
    suspend fun setAtmosphereOn(on: Boolean) = Unit

    suspend fun setAtmosphereName(name: String?) = Unit

    suspend fun setAtmospheres(list: List<Atmosphere>) = Unit

    /** First-run splashes and tips (owner, M5). */
    suspend fun markGuideSeen(id: String) = Unit

    suspend fun clearGuidesSeen() = Unit

    /** Remembers the build whose "what's new" was shown, so the popup comes once per update. */
    suspend fun setLastSeenVersionCode(code: Long) = Unit

    /** Turns first-use tips and the reader demo on or off ("never show tips again"). */
    suspend fun setTipsEnabled(enabled: Boolean) = Unit

    suspend fun setReaderSpreads(on: Boolean)

    suspend fun setSpreadCoverAlone(on: Boolean)

    suspend fun setPaperStyle(style: PaperStyle)

    suspend fun setPageTurn(turn: PageTurn)

    suspend fun setNeonGlow(on: Boolean)

    suspend fun setAutoUpdate(on: Boolean)

    suspend fun setOnlineLookup(on: Boolean)

    suspend fun setTranslateTo(language: String)

    // The reader's controls (riffle scrubber, ADR-0022 and ADR-0023). Defaults do nothing, for test fakes.
    suspend fun setControlsAutoHide(value: ControlsAutoHide) = Unit

    suspend fun setRiffleSound(on: Boolean) = Unit

    suspend fun setRiffleVolume(volume: Float) = Unit

    suspend fun setTurnSound(on: Boolean) = Unit

    suspend fun setTurnVolume(volume: Float) = Unit

    suspend fun setScrubHaptics(on: Boolean) = Unit

    suspend fun setReaderBrightness(brightness: Float) = Unit

    suspend fun setKeepScreenOn(on: Boolean) = Unit

    /** The EPUB text settings (M3). */
    suspend fun setReaderText(text: ReaderText) = Unit

    /** The app's theme and Material You's colours (ADR-0028). */
    suspend fun setAppStyle(style: AppStyle) = Unit

    suspend fun setMaterialSeed(seed: MaterialSeed) = Unit

    /** Dynamic Flow (ADR-0045): open PDFs in it, and what switching views does with annotations. */
    suspend fun setDynamicFlowOnOpen(on: Boolean) = Unit

    suspend fun setFlowCarry(carry: FlowCarry) = Unit
}

@Suppress("TooManyFunctions") // one setter per setting
class DefaultSettingsRepository @Inject constructor(private val source: SettingsDataSource) : SettingsRepository {
    override val settings = source.settings

    override suspend fun setStorageMode(mode: StorageMode) {
        source.setStorageMode(mode)
    }

    override suspend fun setAskStorageEachImport(ask: Boolean) {
        source.setAskStorageEachImport(ask)
    }

    override suspend fun setTheme(theme: ThemePreference) {
        source.setTheme(theme)
    }

    override suspend fun setStorageNoticeSeen() {
        source.setStorageNoticeSeen()
    }

    override suspend fun setLibrarySort(sort: LibrarySort) {
        source.setLibrarySort(sort)
    }

    override suspend fun setAtmosphereOn(on: Boolean) {
        source.setAtmosphereOn(on)
    }

    override suspend fun setAtmosphereName(name: String?) {
        source.setAtmosphereName(name)
    }

    override suspend fun setAtmospheres(list: List<Atmosphere>) {
        source.setAtmospheres(list)
    }

    override suspend fun markGuideSeen(id: String) {
        source.markGuideSeen(id)
    }

    override suspend fun clearGuidesSeen() {
        source.clearGuidesSeen()
    }

    override suspend fun setLastSeenVersionCode(code: Long) {
        source.setLastSeenVersionCode(code)
    }

    override suspend fun setTipsEnabled(enabled: Boolean) {
        source.setTipsEnabled(enabled)
    }

    override suspend fun setReaderSpreads(on: Boolean) {
        source.setReaderSpreads(on)
    }

    override suspend fun setSpreadCoverAlone(on: Boolean) {
        source.setSpreadCoverAlone(on)
    }

    override suspend fun setPaperStyle(style: PaperStyle) {
        source.setPaperStyle(style)
    }

    override suspend fun setPageTurn(turn: PageTurn) {
        source.setPageTurn(turn)
    }

    override suspend fun setNeonGlow(on: Boolean) {
        source.setNeonGlow(on)
    }

    override suspend fun setAutoUpdate(on: Boolean) {
        source.setAutoUpdate(on)
    }

    override suspend fun setOnlineLookup(on: Boolean) {
        source.setOnlineLookup(on)
    }

    override suspend fun setControlsAutoHide(value: ControlsAutoHide) {
        source.setControlsAutoHide(value)
    }

    override suspend fun setRiffleSound(on: Boolean) {
        source.setRiffleSound(on)
    }

    override suspend fun setRiffleVolume(volume: Float) {
        source.setRiffleVolume(volume)
    }

    override suspend fun setTurnSound(on: Boolean) {
        source.setTurnSound(on)
    }

    override suspend fun setTurnVolume(volume: Float) {
        source.setTurnVolume(volume)
    }

    override suspend fun setAppStyle(style: AppStyle) {
        source.setAppStyle(style)
    }

    override suspend fun setMaterialSeed(seed: MaterialSeed) {
        source.setMaterialSeed(seed)
    }

    override suspend fun setReaderText(text: ReaderText) {
        source.setReaderText(text)
    }

    override suspend fun setScrubHaptics(on: Boolean) {
        source.setScrubHaptics(on)
    }

    override suspend fun setReaderBrightness(brightness: Float) {
        source.setReaderBrightness(brightness)
    }

    override suspend fun setKeepScreenOn(on: Boolean) {
        source.setKeepScreenOn(on)
    }

    override suspend fun setTranslateTo(language: String) {
        source.setTranslateTo(language)
    }

    override suspend fun setDynamicFlowOnOpen(on: Boolean) {
        source.setDynamicFlowOnOpen(on)
    }

    override suspend fun setFlowCarry(carry: FlowCarry) {
        source.setFlowCarry(carry)
    }
}
