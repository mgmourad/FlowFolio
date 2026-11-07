package com.flowfolio.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
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
            autoUpdate = p[AUTO_UPDATE] ?: defaults.autoUpdate,
            onlineLookup = p[ONLINE_LOOKUP] ?: defaults.onlineLookup,
            onlineLookupAsked = p[ONLINE_LOOKUP_ASKED] ?: defaults.onlineLookupAsked,
            translateTo = p[TRANSLATE_TO],
            controlsAutoHide = p[AUTO_HIDE].toEnum(defaults.controlsAutoHide),
            riffleSound = p[RIFFLE_SOUND] ?: defaults.riffleSound,
            riffleVolume = p[RIFFLE_VOLUME]?.coerceIn(0f, 1f) ?: defaults.riffleVolume,
            turnSound = p[TURN_SOUND] ?: defaults.turnSound,
            turnVolume = p[TURN_VOLUME]?.coerceIn(0f, 1f) ?: defaults.turnVolume,
            scrubHaptics = p[SCRUB_HAPTICS] ?: defaults.scrubHaptics,
            readerBrightness = p[READER_BRIGHTNESS]?.let { if (it < 0f) -1f else it.coerceIn(0f, 1f) }
                ?: defaults.readerBrightness,
            keepScreenOn = p[KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            text = readerText(p),
            appStyle = p[APP_STYLE].toEnum(defaults.appStyle),
            dynamicFlowOnOpen = p[FLOW_ON_OPEN] ?: defaults.dynamicFlowOnOpen,
            flowCarry = p[FLOW_CARRY].toEnum(defaults.flowCarry),
            atmosphereOn = p[ATMOSPHERE_ON] ?: defaults.atmosphereOn,
            atmosphereName = p[ATMOSPHERE_NAME],
            atmospheres = AtmosphereCodec.decode(p[ATMOSPHERES]),
            seenGuides = p[SEEN_GUIDES].orEmpty(),
            materialSeed = p[MATERIAL_SEED].toEnum(defaults.materialSeed),
            lastSeenVersionCode = p[LAST_SEEN_VERSION_CODE] ?: defaults.lastSeenVersionCode,
            tipsEnabled = p[TIPS_ENABLED] ?: defaults.tipsEnabled
        )
    }

    /** The EPUB text settings; unknown or out-of-range values fall back to the defaults. */
    private fun readerText(p: Preferences): ReaderText {
        val d = ReaderText()
        return ReaderText(
            font = p[TEXT_FONT].toEnum(d.font),
            sizeSp = p[TEXT_SIZE]?.coerceIn(ReaderText.MIN_SIZE_SP, ReaderText.MAX_SIZE_SP) ?: d.sizeSp,
            lineSpacing = p[TEXT_SPACING].toEnum(d.lineSpacing),
            margins = p[TEXT_MARGINS].toEnum(d.margins),
            justify = p[TEXT_JUSTIFY] ?: d.justify,
            hyphenate = p[TEXT_HYPHENATE] ?: d.hyphenate
        )
    }

    suspend fun setAppStyle(style: AppStyle) {
        store.edit { it[APP_STYLE] = style.name }
    }

    suspend fun setMaterialSeed(seed: MaterialSeed) {
        store.edit { it[MATERIAL_SEED] = seed.name }
    }

    suspend fun setReaderText(text: ReaderText) {
        store.edit {
            it[TEXT_FONT] = text.font.name
            it[TEXT_SIZE] = text.sizeSp.coerceIn(ReaderText.MIN_SIZE_SP, ReaderText.MAX_SIZE_SP)
            it[TEXT_SPACING] = text.lineSpacing.name
            it[TEXT_MARGINS] = text.margins.name
            it[TEXT_JUSTIFY] = text.justify
            it[TEXT_HYPHENATE] = text.hyphenate
        }
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

    suspend fun setRiffleSound(on: Boolean) {
        store.edit { it[RIFFLE_SOUND] = on }
    }

    suspend fun setRiffleVolume(volume: Float) {
        store.edit { it[RIFFLE_VOLUME] = volume.coerceIn(0f, 1f) }
    }

    suspend fun setTurnSound(on: Boolean) {
        store.edit { it[TURN_SOUND] = on }
    }

    suspend fun setTurnVolume(volume: Float) {
        store.edit { it[TURN_VOLUME] = volume.coerceIn(0f, 1f) }
    }

    suspend fun setScrubHaptics(on: Boolean) {
        store.edit { it[SCRUB_HAPTICS] = on }
    }

    suspend fun setReaderBrightness(brightness: Float) {
        store.edit { it[READER_BRIGHTNESS] = if (brightness < 0f) -1f else brightness.coerceIn(0f, 1f) }
    }

    suspend fun setKeepScreenOn(on: Boolean) {
        store.edit { it[KEEP_SCREEN_ON] = on }
    }

    suspend fun setTranslateTo(language: String) {
        store.edit { it[TRANSLATE_TO] = language }
    }

    suspend fun setDynamicFlowOnOpen(on: Boolean) {
        store.edit { it[FLOW_ON_OPEN] = on }
    }

    suspend fun setFlowCarry(carry: FlowCarry) {
        store.edit { it[FLOW_CARRY] = carry.name }
    }

    /** Puts the chosen Reading Atmosphere on, or takes it off, for every book (owner, M5). */
    suspend fun setAtmosphereOn(on: Boolean) {
        store.edit { it[ATMOSPHERE_ON] = on }
    }

    /** Chooses the atmosphere by name; null forgets the choice. */
    suspend fun setAtmosphereName(name: String?) {
        store.edit { prefs ->
            if (name == null) prefs.remove(ATMOSPHERE_NAME) else prefs[ATMOSPHERE_NAME] = name
        }
    }

    /** Replaces the reader's own saved atmospheres. */
    suspend fun setAtmospheres(list: List<Atmosphere>) {
        store.edit { it[ATMOSPHERES] = AtmosphereCodec.encode(list) }
    }

    /** Remembers that a first-run splash or tip has been seen (owner, M5). */
    suspend fun markGuideSeen(id: String) {
        store.edit { it[SEEN_GUIDES] = it[SEEN_GUIDES].orEmpty() + id }
    }

    /** Forgets every guide, so the app introduces itself again. */
    suspend fun clearGuidesSeen() {
        store.edit { it.remove(SEEN_GUIDES) }
    }

    /** Remembers the build whose "what's new" was shown, so the popup comes once per update. */
    suspend fun setLastSeenVersionCode(code: Long) {
        store.edit { it[LAST_SEEN_VERSION_CODE] = code }
    }

    /** Turns first-use tips and the reader demo on or off ("never show tips again"). */
    suspend fun setTipsEnabled(enabled: Boolean) {
        store.edit { it[TIPS_ENABLED] = enabled }
    }

    /** Turning the lookup on or off also counts as having been asked. */
    suspend fun setAutoUpdate(on: Boolean) {
        store.edit { it[AUTO_UPDATE] = on }
    }

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
        val AUTO_UPDATE = booleanPreferencesKey("auto_update")
        val ONLINE_LOOKUP = booleanPreferencesKey("online_lookup")
        val ONLINE_LOOKUP_ASKED = booleanPreferencesKey("online_lookup_asked")
        val TRANSLATE_TO = stringPreferencesKey("translate_to")
        val AUTO_HIDE = stringPreferencesKey("reader_auto_hide")

        // The riffle keeps the old "paper sound" key, so a reader who turned that off keeps it off.
        val RIFFLE_SOUND = booleanPreferencesKey("paper_sound")
        val RIFFLE_VOLUME = floatPreferencesKey("riffle_volume")
        val TURN_SOUND = booleanPreferencesKey("turn_sound")
        val TURN_VOLUME = floatPreferencesKey("turn_volume")
        val SCRUB_HAPTICS = booleanPreferencesKey("scrub_haptics")
        val READER_BRIGHTNESS = floatPreferencesKey("reader_brightness")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val APP_STYLE = stringPreferencesKey("app_style")
        val FLOW_ON_OPEN = booleanPreferencesKey("dynamic_flow_on_open")
        val FLOW_CARRY = stringPreferencesKey("flow_carry")
        val MATERIAL_SEED = stringPreferencesKey("material_seed")
        val TEXT_FONT = stringPreferencesKey("text_font")
        val TEXT_SIZE = intPreferencesKey("text_size_sp")
        val TEXT_SPACING = stringPreferencesKey("text_line_spacing")
        val TEXT_MARGINS = stringPreferencesKey("text_margins")
        val TEXT_JUSTIFY = booleanPreferencesKey("text_justify")
        val TEXT_HYPHENATE = booleanPreferencesKey("text_hyphenate")
        val ATMOSPHERE_ON = booleanPreferencesKey("atmosphere_on")
        val ATMOSPHERE_NAME = stringPreferencesKey("atmosphere_name")
        val ATMOSPHERES = stringPreferencesKey("atmospheres")
        val SEEN_GUIDES = stringSetPreferencesKey("seen_guides")
        val LAST_SEEN_VERSION_CODE = longPreferencesKey("last_seen_version_code")
        val TIPS_ENABLED = booleanPreferencesKey("tips_enabled")
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
