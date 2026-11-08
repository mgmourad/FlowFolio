@file:Suppress("TooManyFunctions") // one group or row per setting, and the menu and page screens

package com.flowfolio.feature.library

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.data.UpdateState
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioSegmented
import com.flowfolio.core.designsystem.FlowFolioToggle
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.LocalChromePadding
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.ScrollToTopOnTab
import com.flowfolio.core.designsystem.chromeStart
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.designsystem.softShadow
import com.flowfolio.core.model.AppStyle
import com.flowfolio.core.model.ControlsAutoHide
import com.flowfolio.core.model.FlowCarry
import com.flowfolio.core.model.PageTurn
import com.flowfolio.core.model.StorageMode
import com.flowfolio.core.model.ThemePreference
import com.flowfolio.core.model.UserSettings
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onOpenLicenses: () -> Unit = {},
    scrollToUpdates: Int = 0,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val audition by viewModel.audition.collectAsStateWithLifecycle()
    val cacheBytes by viewModel.cacheBytes.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    SettingsContent(
        settings,
        SettingsActions(
            onTheme = viewModel::setTheme,
            onAppStyle = viewModel::setAppStyle,
            onMaterialSeed = viewModel::setMaterialSeed,
            onGlow = viewModel::setGlow,
            onPageTurn = viewModel::setPageTurn,
            onSpreads = viewModel::setSpreads,
            onCoverAlone = viewModel::setCoverAlone,
            onStorageMode = viewModel::setStorageMode,
            onAskEachImport = viewModel::setAskEachImport,
            onLookup = viewModel::setLookup,
            onAutoHide = viewModel::setAutoHide,
            onTurnSound = viewModel::setTurnSound,
            onTurnVolume = viewModel::setTurnVolume,
            onRiffleSound = viewModel::setRiffleSound,
            onRiffleVolume = viewModel::setRiffleVolume,
            onScrubHaptics = viewModel::setScrubHaptics,
            onReaderText = viewModel::setReaderText,
            onFlowOnOpen = viewModel::setFlowOnOpen,
            onFlowAsk = viewModel::setFlowAsk,
            onAtmosphereOn = viewModel::setAtmosphereOn,
            onAtmosphere = viewModel::setAtmosphere,
            onAtmosphereSave = viewModel::saveAtmosphere,
            onAtmosphereVolume = viewModel::setAtmosphereVolume,
            onAtmosphereDelete = viewModel::deleteAtmosphere,
            onAudition = viewModel::audition,
            onAuditionStop = viewModel::stopAudition,
            onGuideSeen = viewModel::markGuideSeen,
            onGuidesReset = viewModel::resetGuides,
            onClearCache = viewModel::clearCache,
            onResetApp = viewModel::resetApp,
            onCheckUpdates = viewModel::checkForUpdates,
            onDownloadUpdate = viewModel::downloadUpdate,
            onInstallUpdate = viewModel::installUpdate,
            onDismissUpdate = viewModel::dismissUpdate,
            onAutoUpdate = viewModel::setAutoUpdate
        ),
        audition,
        Housekeeping(viewModel.installedVersion, update, cacheBytes),
        onOpenLicenses,
        scrollToUpdates
    )
}

/** What Settings needs beyond the stored settings: the build it is running, and what the cache weighs. */
data class Housekeeping(val version: String = "", val update: UpdateState = UpdateState.Idle, val cacheBytes: Long = 0L)

/** One settings page behind the main menu (M3): its icon and name. */
private enum class SettingsPage(val glyph: Glyph, @StringRes val title: Int) {
    Appearance(Glyph.Paper, R.string.settings_appearance),
    Text(Glyph.TextSize, R.string.settings_text),
    Reading(Glyph.OpenBook, R.string.settings_reading),
    Atmosphere(Glyph.Smile, R.string.settings_atmosphere),
    Library(Glyph.Library, R.string.settings_library),
    Flow(Glyph.DynamicFlow, R.string.settings_conversion),
    Updates(Glyph.Refresh, R.string.settings_updates),
    Storage(Glyph.Storage, R.string.settings_housekeeping)
}

/** The Settings menu (M3): one row per page, with About and "Show me around" kept here. */
@Composable
private fun SettingsMenu(
    settings: UserSettings,
    actions: SettingsActions,
    onOpenLicenses: () -> Unit,
    onPage: (SettingsPage) -> Unit
) {
    val chrome = LocalChromePadding.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    var tutorial by rememberSaveable { mutableStateOf(false) }
    if (tutorial) TutorialSheet { tutorial = false }
    Column(
        Modifier
            .fillMaxSize()
            .chromeStart()
            .verticalScroll(rememberScrollState())
            .testTag(LibraryTestTags.SETTINGS)
            .padding(top = insets.calculateTopPadding())
            .padding(horizontal = 18.dp)
    ) {
        ScreenHeader(
            title = stringResource(R.string.settings_title),
            slogan = stringResource(R.string.settings_sub),
            actions = { HelpButton { tutorial = true } },
            modifier = Modifier.reveal(0)
        )
        SettingsPage.entries.forEachIndexed { i, page ->
            SettingsMenuItem(page, Modifier.reveal(1 + i)) { onPage(page) }
        }
        Spacer(Modifier.height(16.dp))
        AboutGroup(onOpenLicenses)
        GuideTip(Guide.Atmosphere, settings, actions.onGuideSeen, Modifier.padding(top = 8.dp))
        GuidesResetRow(actions.onGuidesReset)
        Spacer(
            Modifier.height(insets.calculateBottomPadding() + chrome.calculateBottomPadding() + 24.dp)
        )
    }
}

/** The "?" help button, at the top right of the Settings menu only (owner, 2026-11-07). */
@Composable
private fun HelpButton(onClick: () -> Unit) {
    FlowFolioIconButton(onClick = onClick, modifier = Modifier.testTag(LibraryTestTags.HELP_BUTTON)) {
        FlowFolioGlyph(Glyph.Help, stringResource(R.string.action_help))
    }
}

/** One menu row: a glowing icon plate, the page's name, and a chevron. */
@Composable
private fun SettingsMenuItem(page: SettingsPage, modifier: Modifier, onClick: () -> Unit) {
    val p = FlowFolio.palette
    FlowFolioCard(
        modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(onClick = onClick),
        radius = 20.dp,
        padding = PaddingValues(0.dp)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(
                    48.dp
                ).clip(
                    RoundedCornerShape(16.dp)
                ).background(Brush.verticalGradient(listOf(p.buttonTop, p.buttonBottom))),
                contentAlignment = Alignment.Center
            ) {
                FlowFolioGlyph(page.glyph, contentDescription = null, color = p.buttonInk, size = 28.dp)
            }
            Text(
                stringResource(page.title),
                style = FlowFolioType.rowLabel.merge(FlowFolio.item),
                modifier = Modifier.weight(1f).padding(start = 14.dp)
            )
            FlowFolioGlyph(
                Glyph.Back,
                contentDescription = null,
                color = p.hint,
                size = 16.dp,
                glow = false,
                modifier = Modifier.graphicsLayer { rotationZ = 180f }
            )
        }
    }
}

/** A dedicated settings page: back, a piece of art (the page's own icon), then its settings. */
@Composable
private fun SettingsPageScreen(
    page: SettingsPage,
    settings: UserSettings,
    actions: SettingsActions,
    audition: Audition,
    housekeeping: Housekeeping,
    onBack: () -> Unit
) {
    val chrome = LocalChromePadding.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Column(
        Modifier
            .fillMaxSize()
            .chromeStart()
            .verticalScroll(rememberScrollState())
            .padding(top = insets.calculateTopPadding())
            .padding(horizontal = 18.dp)
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            FlowFolioIconButton(onClick = onBack, glass = true, modifier = Modifier.padding(end = 12.dp)) {
                FlowFolioGlyph(Glyph.Back, stringResource(R.string.action_back), color = FlowFolio.palette.text)
            }
            Text(
                stringResource(page.title),
                style = FlowFolioType.screenTitle.merge(FlowFolio.title),
                modifier = Modifier.align(Alignment.Center)
            )
        }
        // The art: the page's icon on a glowing, gradient plate, centred (M3).
        Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
            val p = FlowFolio.palette
            Box(
                Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .softShadow(RoundedCornerShape(32.dp), p.title.copy(alpha = 0.35f), blur = 28.dp)
                    .background(Brush.linearGradient(listOf(p.buttonTop, p.buttonBottom, p.buttonTop)))
                    .reveal(0),
                contentAlignment = Alignment.Center
            ) {
                FlowFolioGlyph(page.glyph, contentDescription = null, color = p.buttonInk, size = 52.dp)
            }
        }
        when (page) {
            SettingsPage.Appearance -> AppearanceGroup(settings, actions)
            SettingsPage.Text -> TextGroup(settings.text, 2, actions.onReaderText)
            SettingsPage.Reading -> ReadingGroup(settings, actions)
            SettingsPage.Atmosphere -> AtmosphereGroup(settings, actions, audition)
            SettingsPage.Library -> LibraryGroup(settings, actions)
            SettingsPage.Flow -> ConversionGroup(settings, actions)
            SettingsPage.Updates -> UpdatesGroup(settings, housekeeping.version, housekeeping.update, actions)
            SettingsPage.Storage -> HousekeepingGroup(housekeeping.cacheBytes, actions)
        }
        Spacer(Modifier.height(insets.calculateBottomPadding() + chrome.calculateBottomPadding() + 24.dp))
    }
}

/**
 * The mock's Settings (`#s-settings`): a menu of pages (M3). About and "Show me around" stay on the menu; the rest
 * each open a page of their own, behind a shared art header. The help button sits at the menu's top right.
 */
@Suppress("LongMethod")
@Composable
fun SettingsContent(
    settings: UserSettings,
    actions: SettingsActions,
    audition: Audition = Audition.IDLE,
    housekeeping: Housekeeping = Housekeeping(),
    onOpenLicenses: () -> Unit = {},
    scrollToUpdates: Int = 0
) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    // A banner tap opens the Updates page directly (ADR-0048).
    LaunchedEffect(scrollToUpdates) {
        if (scrollToUpdates > 0) page = SettingsPage.Updates
    }
    // System Back on a dedicated page returns to the menu, not out to the Library (owner, 2026-11-07).
    BackHandler(enabled = page != null) { page = null }
    Box(Modifier.fillMaxSize().testTag(LibraryTestTags.SETTINGS)) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val way = if (targetState == null) -1 else 1
                (
                    slideInHorizontally(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)) { it * way } +
                        fadeIn(tween(Motion.MEDIUM_MS))
                    )
                    .togetherWith(
                        slideOutHorizontally(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)) { -it * way } +
                            fadeOut(tween(Motion.MEDIUM_MS))
                    )
            },
            label = "settingsPage"
        ) { p ->
            if (p == null) {
                SettingsMenu(settings, actions, onOpenLicenses) { page = it }
            } else {
                SettingsPageScreen(p, settings, actions, audition, housekeeping) { page = null }
            }
        }
    }
}

/** "Introduce features again": empties what the app remembers having shown (owner, M5). */
@Composable
private fun GuidesResetRow(onReset: () -> Unit) {
    Group(R.string.settings_tutorial, 1) {
        ActionRow(R.string.settings_guides_reset, R.string.settings_guides_reset_detail, onReset)
    }
}

/** The open-source licences for what ships in the app, one row opening the full list (known issue 8). */
@Composable
private fun AboutGroup(onOpenLicenses: () -> Unit) {
    Group(R.string.settings_about, 6) {
        ActionRow(R.string.settings_licenses, R.string.settings_licenses_detail, onOpenLicenses)
    }
}

@Composable
private fun AppearanceGroup(settings: UserSettings, actions: SettingsActions) {
    Group(R.string.settings_appearance, 1) {
        // The theme (ADR-0028) as previews, then light or dark, then Material You's colours when it is chosen.
        Label(
            R.string.settings_app_theme,
            R.string.settings_app_theme_detail,
            Modifier.padding(start = 16.dp, end = 16.dp, top = 15.dp)
        )
        ThemeCards(settings.appStyle, settings.materialSeed, actions.onAppStyle)
        if (settings.appStyle == AppStyle.MATERIAL) SeedSwatches(settings.materialSeed, actions.onMaterialSeed)
        Divider()
        val themes = listOf(ThemePreference.SYSTEM, ThemePreference.DARK, ThemePreference.LIGHT)
        ChoiceRow(
            R.string.settings_theme,
            R.string.settings_theme_detail,
            listOf(R.string.theme_system_short, R.string.theme_dark, R.string.theme_light),
            themes.indexOf(settings.theme)
        ) { actions.onTheme(themes[it]) }
        // Material You has no lights to turn off.
        if (settings.appStyle != AppStyle.MATERIAL) {
            Divider()
            SwitchRow(R.string.settings_glow, R.string.settings_glow_detail, settings.neonGlow, actions.onGlow)
        }
        Divider()
        ChoiceRow(
            R.string.settings_turn,
            R.string.settings_turn_detail,
            listOf(R.string.turn_curl, R.string.turn_slide, R.string.turn_scroll),
            PageTurn.entries.indexOf(settings.pageTurn)
        ) { actions.onPageTurn(PageTurn.entries[it]) }
    }
}

@Composable
private fun ReadingGroup(settings: UserSettings, actions: SettingsActions) {
    Group(R.string.settings_reading, 2) {
        val scroll = settings.pageTurn == PageTurn.SCROLL
        SwitchRow(
            R.string.settings_spreads,
            R.string.settings_spreads_detail,
            settings.readerSpreads,
            actions.onSpreads,
            !scroll
        )
        Divider()
        SwitchRow(
            R.string.settings_cover,
            R.string.settings_cover_detail,
            settings.spreadCoverAlone,
            actions.onCoverAlone,
            enabled = settings.readerSpreads && !scroll
        )
        Divider()
        // The reader's controls and the riffle scrubber (riffle spec 5.6, 5.7); also in the reader's "Aa" panel.
        ChoiceRow(
            R.string.settings_auto_hide,
            R.string.settings_auto_hide_detail,
            listOf(
                R.string.settings_auto_hide_3,
                R.string.settings_auto_hide_5,
                R.string.settings_auto_hide_8,
                R.string.settings_auto_hide_15,
                R.string.settings_auto_hide_never
            ),
            ControlsAutoHide.entries.indexOf(settings.controlsAutoHide),
            stacked = true
        ) { actions.onAutoHide(ControlsAutoHide.entries[it]) }
        Divider()
        SoundRow(
            R.string.settings_turn_sound,
            R.string.settings_turn_sound_detail,
            settings.turnSound,
            settings.turnVolume,
            actions.onTurnSound to actions.onTurnVolume
        )
        Divider()
        SoundRow(
            R.string.settings_riffle_sound,
            R.string.settings_riffle_sound_detail,
            settings.riffleSound,
            settings.riffleVolume,
            actions.onRiffleSound to actions.onRiffleVolume
        )
        Divider()
        SwitchRow(
            R.string.settings_scrub_haptics,
            R.string.settings_scrub_haptics_detail,
            settings.scrubHaptics,
            actions.onScrubHaptics
        )
    }
}

@Composable
private fun LibraryGroup(settings: UserSettings, actions: SettingsActions) {
    Group(R.string.settings_library, 3) {
        Text(
            stringResource(R.string.settings_import_section),
            style = FlowFolioType.rowLabel.merge(FlowFolio.item),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 15.dp)
        )
        Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            StorageOptions(settings.storageMode, actions.onStorageMode)
        }
        Divider()
        SwitchRow(R.string.settings_ask_each, null, settings.askStorageEachImport, actions.onAskEachImport)
        Divider()
        SwitchRow(R.string.settings_lookup, R.string.settings_lookup_detail, settings.onlineLookup, actions.onLookup)
    }
}

/**
 * Dynamic Flow (ADR-0045): whether PDFs open in it, with a hint saying what that does; and whether switching views asks
 * about the highlights, notes and bookmarks made in the other view.
 */
@Composable
private fun ConversionGroup(settings: UserSettings, actions: SettingsActions) {
    Group(R.string.settings_conversion, 4) {
        SwitchRow(
            R.string.settings_flow_on_open,
            R.string.settings_flow_on_open_detail,
            settings.dynamicFlowOnOpen,
            actions.onFlowOnOpen
        )
        Divider()
        SwitchRow(
            R.string.settings_flow_ask,
            // Off has two meanings: the reader chose "Don't show this again" with either answer.
            when (settings.flowCarry) {
                FlowCarry.ASK -> R.string.settings_flow_ask_detail
                FlowCarry.CONVERT -> R.string.settings_flow_ask_detail_convert
                FlowCarry.SKIP -> R.string.settings_flow_ask_detail_skip
            },
            settings.flowCarry == FlowCarry.ASK,
            actions.onFlowAsk
        )
    }
}

/** `.set-h` + `.set-group`: a heading in the sub-item colour over a card of rows. */
@Composable
internal fun Group(title: Int, order: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.reveal(order)) {
        Text(
            stringResource(title).uppercase(),
            style = FlowFolioType.eyebrow.merge(FlowFolio.subItem),
            modifier = Modifier.padding(start = 10.dp, top = 22.dp, bottom = 10.dp)
        )
        FlowFolioCard(Modifier.fillMaxWidth(), radius = 24.dp, padding = PaddingValues(0.dp)) {
            Column(content = content)
        }
    }
}
