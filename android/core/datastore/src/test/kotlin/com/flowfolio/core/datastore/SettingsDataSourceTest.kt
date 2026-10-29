package com.flowfolio.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flowfolio.core.model.FlowCarry
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.StorageMode
import com.flowfolio.core.model.ThemePreference
import com.flowfolio.core.model.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Each case writes once to a fresh store: on a Windows dev machine DataStore's atomic rename fails when the target
 * file already exists (not an issue on Android or the Linux CI runners), so tests avoid a second write per file.
 */
class SettingsDataSourceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.fresh(): Pair<SettingsDataSource, DataStore<Preferences>> {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            java.io.File(tmp.newFolder(), "settings.preferences_pb")
        }
        return SettingsDataSource(store) to store
    }

    private fun check(write: suspend SettingsDataSource.() -> Unit, expected: UserSettings) = runTest {
        val (source, _) = fresh()
        source.write()
        assertEquals(expected, source.settings.first())
    }

    @Test
    fun defaultsAreCopyModeSystemThemeRecentSort() = runTest {
        assertEquals(UserSettings(), fresh().first.settings.first())
    }

    @Test
    fun storageMode() = check({ setStorageMode(StorageMode.LINK) }, UserSettings(storageMode = StorageMode.LINK))

    @Test
    fun askEachImport() = check({ setAskStorageEachImport(true) }, UserSettings(askStorageEachImport = true))

    @Test
    fun theme() = check({ setTheme(ThemePreference.DARK) }, UserSettings(theme = ThemePreference.DARK))

    @Test
    fun noticeSeen() = check({ setStorageNoticeSeen() }, UserSettings(storageNoticeSeen = true))

    @Test
    fun sort() = check({ setLibrarySort(LibrarySort.TITLE) }, UserSettings(librarySort = LibrarySort.TITLE))

    @Test
    fun dynamicFlowOnOpen() = check({ setDynamicFlowOnOpen(true) }, UserSettings(dynamicFlowOnOpen = true))

    @Test
    fun flowCarry() = check({ setFlowCarry(FlowCarry.SKIP) }, UserSettings(flowCarry = FlowCarry.SKIP))

    @Test
    fun dynamicFlowIsOffAndAsksByDefault() = runTest {
        val settings = fresh().first.settings.first()
        assertEquals(false, settings.dynamicFlowOnOpen)
        assertEquals(FlowCarry.ASK, settings.flowCarry)
    }

    @Test
    fun unknownStoredValueFallsBackToDefault() = runTest {
        val (source, store) = fresh()
        store.edit { it[stringPreferencesKey("storage_mode")] = "TELEPORT" }
        assertEquals(StorageMode.COPY, source.settings.first().storageMode)
    }
}
