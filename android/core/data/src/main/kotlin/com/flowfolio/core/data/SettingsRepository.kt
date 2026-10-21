package com.flowfolio.core.data

import com.flowfolio.core.datastore.SettingsDataSource
import com.flowfolio.core.model.ControlsAutoHide
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.PageTurn
import com.flowfolio.core.model.PaperStyle
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

    suspend fun setReaderSpreads(on: Boolean)

    suspend fun setSpreadCoverAlone(on: Boolean)

    suspend fun setPaperStyle(style: PaperStyle)

    suspend fun setPageTurn(turn: PageTurn)

    suspend fun setNeonGlow(on: Boolean)

    suspend fun setOnlineLookup(on: Boolean)

    suspend fun setTranslateTo(language: String)

    // The reader's controls (riffle scrubber, ADR-0022 and ADR-0023). Defaults do nothing, for test fakes.
    suspend fun setControlsAutoHide(value: ControlsAutoHide) = Unit

    suspend fun setPaperSound(on: Boolean) = Unit

    suspend fun setScrubHaptics(on: Boolean) = Unit
}

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

    override suspend fun setOnlineLookup(on: Boolean) {
        source.setOnlineLookup(on)
    }

    override suspend fun setControlsAutoHide(value: ControlsAutoHide) {
        source.setControlsAutoHide(value)
    }

    override suspend fun setPaperSound(on: Boolean) {
        source.setPaperSound(on)
    }

    override suspend fun setScrubHaptics(on: Boolean) {
        source.setScrubHaptics(on)
    }

    override suspend fun setTranslateTo(language: String) {
        source.setTranslateTo(language)
    }
}
