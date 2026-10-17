package com.flowfolio.core.data

import com.flowfolio.core.datastore.SettingsDataSource
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.StorageMode
import com.flowfolio.core.model.ThemePreference
import com.flowfolio.core.model.UserSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setStorageMode(mode: StorageMode)

    suspend fun setAskStorageEachImport(ask: Boolean)

    suspend fun setTheme(theme: ThemePreference)

    suspend fun setStorageNoticeSeen()

    suspend fun setLibrarySort(sort: LibrarySort)
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
}
