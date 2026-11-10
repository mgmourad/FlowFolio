@file:Suppress("TooManyFunctions") // one group or row per setting, and the menu and page screens

package com.flowfolio.feature.library

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.data.UpdateState
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioSegmented
import com.flowfolio.core.designsystem.FlowFolioSheet
import com.flowfolio.core.designsystem.FlowFolioToggle
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.LocalChromePadding
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.ScrollToTopOnTab
import com.flowfolio.core.designsystem.SettingsTile
import com.flowfolio.core.designsystem.TileSize
import com.flowfolio.core.designsystem.card
import com.flowfolio.core.designsystem.chromeStart
import com.flowfolio.core.designsystem.displayName
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.designsystem.softShadow
import com.flowfolio.core.model.AppStyle
import com.flowfolio.core.model.ControlsAutoHide
import com.flowfolio.core.model.FlowCarry
import com.flowfolio.core.model.PageTurn
import com.flowfolio.core.model.StatsBand
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
            onStatsBand = viewModel::setStatsBand,
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

/** The Settings sections (ADR-0052), in the mock's order: each a hub row and a page of its own. */
private enum class SettingsPage(val glyph: Glyph, @StringRes val title: Int) {
    Appearance(Glyph.Paper, R.string.settings_appearance),
    Text(Glyph.TextSize, R.string.settings_text),
    Reading(Glyph.OpenBook, R.string.settings_reading),
    Sound(Glyph.Sound, R.string.settings_sound),
    Atmosphere(Glyph.Ambience, R.string.settings_atmosphere),
    Library(Glyph.Library, R.string.settings_library),
    Flow(Glyph.DynamicFlow, R.string.settings_conversion),
    Updates(Glyph.Refresh, R.string.settings_updates),
    Storage(Glyph.Storage, R.string.settings_housekeeping),
    Help(Glyph.Help, R.string.settings_help)
}

/** A section's current values in one line, for its hub row (ADR-0052). */
@Suppress("CyclomaticComplexMethod") // one line per section
@Composable
private fun SettingsPage.summary(settings: UserSettings, housekeeping: Housekeeping): String = when (this) {
    SettingsPage.Appearance -> listOf(
        stringResource(settings.appStyle.styleNameRes()),
        stringResource(
            when (settings.theme) {
                ThemePreference.SYSTEM -> R.string.theme_system_short
                ThemePreference.DARK -> R.string.theme_dark
                ThemePreference.LIGHT -> R.string.theme_light
            }
        )
    ).joinToString(" · ")

    SettingsPage.Text -> stringResource(
        R.string.settings_text_summary,
        settings.text.font.displayName.ifEmpty { stringResource(R.string.settings_book_font) },
        settings.text.sizeSp
    )

    SettingsPage.Reading -> stringResource(
        if (settings.readerSpreads) {
            R.string.settings_reading_summary_spread
        } else {
            R.string.settings_reading_summary_single
        },
        stringResource(
            when (settings.pageTurn) {
                PageTurn.CURL -> R.string.turn_curl
                PageTurn.SLIDE -> R.string.turn_slide
                PageTurn.SCROLL -> R.string.turn_scroll
            }
        )
    )

    SettingsPage.Sound -> stringResource(
        R.string.settings_sound_summary,
        stringResource(if (settings.turnSound) R.string.state_on else R.string.state_off),
        stringResource(if (settings.riffleSound) R.string.state_on else R.string.state_off)
    )

    SettingsPage.Atmosphere -> settings.atmosphereName?.takeIf { settings.atmosphereOn }
        ?: stringResource(R.string.state_off)

    SettingsPage.Library -> stringResource(
        if (settings.storageMode == StorageMode.COPY) {
            R.string.settings_library_summary_copy
        } else {
            R.string.settings_library_summary_link
        }
    )

    SettingsPage.Flow -> stringResource(
        if (settings.dynamicFlowOnOpen) R.string.settings_flow_summary_on else R.string.settings_flow_summary_off
    )

    SettingsPage.Updates -> housekeeping.version

    SettingsPage.Storage -> stringResource(R.string.settings_storage_summary, readableBytes(housekeeping.cacheBytes))

    SettingsPage.Help -> stringResource(R.string.settings_help_summary)
}

/**
 * The Settings hub (ADR-0052): one row per section, its orange tile, its name and its current values, opening the
 * section's own page. Unfolded the same list stands, compact, beside the chosen page ([compact], [selected]).
 */
@Composable
private fun SettingsMenu(
    settings: UserSettings,
    housekeeping: Housekeeping,
    onPage: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    selected: SettingsPage? = null
) {
    val chrome = LocalChromePadding.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    var tutorial by rememberSaveable { mutableStateOf(false) }
    if (tutorial) TutorialSheet { tutorial = false }
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(top = insets.calculateTopPadding())
            .padding(horizontal = if (compact) 8.dp else libraryGutter())
    ) {
        ScreenHeader(
            title = stringResource(R.string.settings_title),
            slogan = stringResource(R.string.settings_sub),
            actions = if (compact) null else ({ HelpButton { tutorial = true } }),
            modifier = Modifier.reveal(0)
        )
        Column(
            Modifier.padding(top = if (compact) 12.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp)
        ) {
            SettingsPage.entries.forEachIndexed { i, page ->
                SettingsHubRow(
                    page,
                    page.summary(settings, housekeeping),
                    compact,
                    page == selected,
                    Modifier.reveal(1 + i)
                ) { onPage(page) }
            }
        }
        Spacer(Modifier.height(insets.calculateBottomPadding() + chrome.calculateBottomPadding() + 24.dp))
    }
}

/** The "?" help button, at the top right of the Settings hub (owner, 2026-11-07). */
@Composable
private fun HelpButton(onClick: () -> Unit) {
    FlowFolioIconButton(
        onClick = onClick,
        tooltip = stringResource(R.string.action_help),
        modifier = Modifier.testTag(LibraryTestTags.HELP_BUTTON)
    ) {
        FlowFolioGlyph(Glyph.Help, null, size = 20.dp)
    }
}

/**
 * A hub row (`.hubrow`): the section's tile, its name, its values in one line and a chevron, on a card. Compact, it
 * drops the card and the chevron, and the chosen one sits on the soft accent.
 */
@Composable
private fun SettingsHubRow(
    page: SettingsPage,
    summary: String,
    compact: Boolean,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(if (compact) 14.dp else 20.dp)
    val surface = when {
        compact && selected -> Modifier.background(p.accentSoft, shape)
        compact -> Modifier
        else -> Modifier.card(shape)
    }
    Row(
        modifier
            .fillMaxWidth()
            .then(surface)
            .clip(shape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 8.dp else 12.dp)
            .testTag(LibraryTestTags.settingsPage(page.name)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsTile(page.glyph, size = if (compact) TileSize.COMPACT else TileSize.ROW)
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(page.title),
                style = FlowFolioType.rowLabel.copy(fontSize = if (compact) 14.sp else 15.5.sp, color = p.ink)
            )
            Text(
                summary,
                style = FlowFolioType.body.copy(fontSize = if (compact) 11.5.sp else 12.5.sp, color = p.ink3),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!compact) {
            FlowFolioGlyph(
                Glyph.Back,
                contentDescription = null,
                color = p.ink3,
                size = 18.dp,
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
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)?
) {
    val chrome = LocalChromePadding.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(top = insets.calculateTopPadding())
            .padding(horizontal = 18.dp)
    ) {
        // A dedicated page (ADR-0052): Back (folded), then the section's large tile and its name.
        if (onBack != null) {
            FlowFolioIconButton(
                onClick = onBack,
                tooltip = stringResource(R.string.action_back),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                FlowFolioGlyph(Glyph.Back, null, size = 20.dp)
            }
        }
        Row(
            Modifier.padding(top = 16.dp, bottom = 8.dp).reveal(0),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsTile(page.glyph, size = TileSize.BIG)
            Text(
                stringResource(page.title),
                style = FlowFolioType.screenTitle.copy(
                    fontSize = 30.sp,
                    lineHeight = 33.sp,
                    color = FlowFolio.palette.ink
                )
            )
        }
        when (page) {
            SettingsPage.Appearance -> AppearanceGroup(settings, actions)

            SettingsPage.Text -> {
                Text(
                    stringResource(R.string.settings_text_defaults),
                    style = FlowFolioType.body.copy(fontSize = 13.sp, color = FlowFolio.palette.ink2),
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                )
                TextGroup(settings.text, 2, actions.onReaderText)
            }

            SettingsPage.Reading -> ReadingGroup(settings, actions)

            SettingsPage.Sound -> SoundGroup(settings, actions)

            SettingsPage.Atmosphere -> AtmosphereGroup(settings, actions, audition)

            SettingsPage.Library -> LibraryGroup(settings, actions)

            SettingsPage.Flow -> ConversionGroup(settings, actions)

            SettingsPage.Updates -> UpdatesGroup(settings, housekeeping.version, housekeeping.update, actions)

            SettingsPage.Storage -> HousekeepingGroup(housekeeping.cacheBytes, actions)

            SettingsPage.Help -> HelpGroup(settings, housekeeping.version, actions, onOpenLicenses)
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
    val wide = libraryWide()
    Box(Modifier.fillMaxSize().chromeStart().testTag(LibraryTestTags.SETTINGS)) {
        if (wide) {
            // Unfolded (ADR-0052): the hub as a compact list on the left, the chosen page on the right.
            val shown = page ?: SettingsPage.Appearance
            Row(Modifier.fillMaxSize()) {
                SettingsMenu(
                    settings,
                    housekeeping,
                    { page = it },
                    Modifier.width(WIDE_LIST),
                    compact = true,
                    selected = shown
                )
                AnimatedContent(
                    targetState = shown,
                    transitionSpec = {
                        (
                            fadeIn(tween(Motion.MEDIUM_MS, delayMillis = PANE_DELAY_MS)) +
                                slideInVertically(tween(Motion.MEDIUM_MS, easing = Motion.Rise)) { it / PANE_RISE }
                            )
                            .togetherWith(fadeOut(tween(Motion.FAST_MS)))
                    },
                    label = "settingsPane",
                    modifier = Modifier.weight(1f)
                ) { p ->
                    SettingsPageScreen(
                        p,
                        settings,
                        actions,
                        audition,
                        housekeeping,
                        onOpenLicenses,
                        Modifier.fillMaxSize(),
                        onBack = null
                    )
                }
            }
        } else {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val way = if (targetState == null) -1 else 1
                    (
                        slideInHorizontally(tween(Motion.MEDIUM_MS, easing = Motion.Emphasized)) {
                            it / PAGE_IN * way
                        } +
                            fadeIn(tween(Motion.MEDIUM_MS))
                        )
                        .togetherWith(
                            slideOutHorizontally(tween(Motion.FAST_MS, easing = Motion.EmphasizedExit)) {
                                -it / PAGE_OUT * way
                            } +
                                fadeOut(tween(Motion.FAST_MS))
                        )
                },
                label = "settingsPage"
            ) { p ->
                if (p == null) {
                    SettingsMenu(settings, housekeeping, { page = it }, Modifier.fillMaxSize())
                } else {
                    SettingsPageScreen(
                        p,
                        settings,
                        actions,
                        audition,
                        housekeeping,
                        onOpenLicenses,
                        Modifier.fillMaxSize()
                    ) {
                        page = null
                    }
                }
            }
        }
    }
}

private val WIDE_LIST = 260.dp
private const val PANE_DELAY_MS = 60
private const val PANE_RISE = 24
private const val PAGE_OUT = 8
private const val PAGE_IN = 5

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
            R.string.settings_stats_band,
            R.string.settings_stats_band_detail,
            listOf(R.string.stats_band_collapsed, R.string.stats_band_expanded, R.string.stats_band_hidden),
            StatsBand.entries.indexOf(settings.statsBand)
        ) { actions.onStatsBand(StatsBand.entries[it]) }
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
    }
}

/** Sound & voice (ADR-0052): page-turn and riffle sounds with their volumes, and the riffle's haptics. */
@Composable
private fun SoundGroup(settings: UserSettings, actions: SettingsActions) {
    Group(R.string.settings_sound, 3) {
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
    Text(
        stringResource(R.string.settings_voice_note),
        style = FlowFolioType.body.copy(fontSize = 13.sp, color = FlowFolio.palette.ink2),
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp)
    )
}

/** Help & about (ADR-0052): the tour again, reader tips again, licences, feedback and the version. */
@Composable
private fun HelpGroup(settings: UserSettings, version: String, actions: SettingsActions, onOpenLicenses: () -> Unit) {
    var tutorial by rememberSaveable { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf(false) }
    if (tutorial) TutorialSheet { tutorial = false }
    val context = androidx.compose.ui.platform.LocalContext.current
    if (notes) {
        val all = androidx.compose.runtime.remember { Changelog.read(context) }
        FlowFolioSheet(onDismissRequest = {
            notes = false
        }, title = stringResource(R.string.settings_update_whats_new)) {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                ChangelogView(all, Changelog.current(all, version))
            }
        }
    }
    Group(R.string.settings_help, 6) {
        ActionRow(R.string.settings_tour_again, R.string.settings_tour_again_detail) { tutorial = true }
        Divider()
        ActionRow(R.string.settings_update_whats_new, R.string.settings_whats_new_detail) { notes = true }
        Divider()
        ActionRow(R.string.settings_guides_reset, R.string.settings_guides_reset_detail, actions.onGuidesReset)
        Divider()
        ActionRow(R.string.settings_licenses, R.string.settings_licenses_detail, onOpenLicenses)
        Divider()
        ActionRow(R.string.settings_feedback, R.string.settings_feedback_detail) {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(FEEDBACK_URL))
            )
        }
    }
    GuideTip(Guide.Atmosphere, settings, actions.onGuideSeen, Modifier.padding(top = 8.dp))
    Text(
        stringResource(R.string.settings_version_line, version),
        style = FlowFolioType.body.copy(fontSize = 12.5.sp, color = FlowFolio.palette.ink3),
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 16.dp)
    )
}

/** Where feedback goes: the project's public issues. */
private const val FEEDBACK_URL = "https://github.com/mgmourad/FlowFolio/issues"

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

private fun AppStyle.styleNameRes(): Int = when (this) {
    AppStyle.FLOWFOLIO -> R.string.style_flowfolio
    AppStyle.RUSTIC -> R.string.style_rustic
    AppStyle.MATERIAL -> R.string.style_material
    AppStyle.AMOLED -> R.string.style_amoled
}
