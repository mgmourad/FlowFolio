package com.flowfolio.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.flowfolio.core.model.ControlsAutoHide
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.PageTurn
import com.flowfolio.core.model.PaperStyle
import com.flowfolio.core.model.StorageMode
import com.flowfolio.core.model.ThemePreference
import com.flowfolio.core.model.UserSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User settings persisted with DataStore. Unknown stored values fall back to defaults instead of crashing. */
@Suppress("TooManyFunctions") // one setter per setting
@Singleton
class SettingsDataSource @Inject constructor(private val store: DataStore<Preferences>) {
    val settings: Flow<UserSettings> = store.data.map { p ->
        val defaults = UserSettings()
        UserSettings(
            storageMode = p[STORAGE_MODE].toEnum(defaults.storageMode),
            askStorageEachImport = p[ASK_EACH_IMPORT] ?: defaults.askStorageEachImport,
            theme = p[THEME].toEnum(defaults.theme),
            storageNoticeSeen = p[NOTICE_SEEN] ?: defaults.storageNoticeSeen,
            librarySort = p[SORT].toEnum(defaults.librarySort),
            readerSpreads = p[READER_SPREADS] ?: defaults.readerSpreads,
            spreadCoverAlone = p[COVER_ALONE] ?: defaults.spreadCoverAlone,
            paperStyle = p[PAPER_STYLE].toEnum(defaults.paperStyle),
            pageTurn = p[PAGE_TURN].toEnum(defaults.pageTurn),
            neonGlow = p[NEON_GLOW] ?: defaults.neonGlow,
            onlineLookup = p[ONLINE_LOOKUP] ?: defaults.onlineLookup,
            onlineLookupAsked = p[ONLINE_LOOKUP_ASKED] ?: defaults.onlineLookupAsked,
            translateTo = p[TRANSLATE_TO],
            controlsAutoHide = p[AUTO_HIDE].toEnum(defaults.controlsAutoHide),
            paperSound = p[PAPER_SOUND] ?: defaults.paperSound,
            scrubHaptics = p[SCRUB_HAPTICS] ?: defaults.scrubHaptics
        )
    }

    suspend fun setStorageMode(mode: StorageMode) {
        store.edit { it[STORAGE_MODE] = mode.name }
    }

    suspend fun setAskStorageEachImport(ask: Boolean) {
        store.edit { it[ASK_EACH_IMPORT] = ask }
    }

    suspend fun setTheme(theme: ThemePreference) {
        store.edit { it[THEME] = theme.name }
    }

    suspend fun setStorageNoticeSeen() {
        store.edit { it[NOTICE_SEEN] = true }
    }

    suspend fun setLibrarySort(sort: LibrarySort) {
        store.edit { it[SORT] = sort.name }
    }

    suspend fun setReaderSpreads(on: Boolean) {
        store.edit { it[READER_SPREADS] = on }
    }

    suspend fun setSpreadCoverAlone(on: Boolean) {
        store.edit { it[COVER_ALONE] = on }
    }

    suspend fun setPaperStyle(style: PaperStyle) {
        store.edit { it[PAPER_STYLE] = style.name }
    }

    suspend fun setPageTurn(turn: PageTurn) {
        store.edit { it[PAGE_TURN] = turn.name }
    }

    suspend fun setNeonGlow(on: Boolean) {
        store.edit { it[NEON_GLOW] = on }
    }

    suspend fun setControlsAutoHide(value: ControlsAutoHide) {
        store.edit { it[AUTO_HIDE] = value.name }
    }

    suspend fun setPaperSound(on: Boolean) {
        store.edit { it[PAPER_SOUND] = on }
    }

    suspend fun setScrubHaptics(on: Boolean) {
        store.edit { it[SCRUB_HAPTICS] = on }
    }

    suspend fun setTranslateTo(language: String) {
        store.edit { it[TRANSLATE_TO] = language }
    }

    /** Turning the lookup on or off also counts as having been asked. */
    suspend fun setOnlineLookup(on: Boolean) {
        store.edit {
            it[ONLINE_LOOKUP] = on
            it[ONLINE_LOOKUP_ASKED] = true
        }
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    private companion object {
        val STORAGE_MODE = stringPreferencesKey("storage_mode")
        val ASK_EACH_IMPORT = booleanPreferencesKey("ask_storage_each_import")
        val THEME = stringPreferencesKey("theme")
        val NOTICE_SEEN = booleanPreferencesKey("storage_notice_seen")
        val SORT = stringPreferencesKey("library_sort")
        val READER_SPREADS = booleanPreferencesKey("reader_spreads")
        val COVER_ALONE = booleanPreferencesKey("spread_cover_alone")
        val PAPER_STYLE = stringPreferencesKey("reader_paper")
        val PAGE_TURN = stringPreferencesKey("reader_page_turn")
        val NEON_GLOW = booleanPreferencesKey("neon_glow")
        val ONLINE_LOOKUP = booleanPreferencesKey("online_lookup")
        val ONLINE_LOOKUP_ASKED = booleanPreferencesKey("online_lookup_asked")
        val TRANSLATE_TO = stringPreferencesKey("translate_to")
        val AUTO_HIDE = stringPreferencesKey("reader_auto_hide")
        val PAPER_SOUND = booleanPreferencesKey("paper_sound")
        val SCRUB_HAPTICS = booleanPreferencesKey("scrub_haptics")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}
