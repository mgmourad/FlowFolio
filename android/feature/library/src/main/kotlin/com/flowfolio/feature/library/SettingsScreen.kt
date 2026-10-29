package com.flowfolio.feature.library

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioSegmented
import com.flowfolio.core.designsystem.FlowFolioToggle
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.LocalChromePadding
import com.flowfolio.core.designsystem.chromeStart
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.model.AppStyle
import com.flowfolio.core.model.ControlsAutoHide
import com.flowfolio.core.model.FlowCarry
import com.flowfolio.core.model.PageTurn
import com.flowfolio.core.model.StorageMode
import com.flowfolio.core.model.ThemePreference
import com.flowfolio.core.model.UserSettings

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
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
            onFlowAsk = viewModel::setFlowAsk
        )
    )
}

/**
 * The mock's Settings (`#s-settings`): headed groups on cards. Appearance (theme, Neon glow, page turn), Text (how
 * EPUBs are set, M3), Reading (the spread layout), Library (how imports are stored) and Conversion (where it
 * happens). Unfolded, the groups sit in two columns. The mock's notification settings stay hidden until they exist.
 */
@Composable
fun SettingsContent(settings: UserSettings, actions: SettingsActions) {
    val chrome = LocalChromePadding.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    BoxWithConstraints(Modifier.fillMaxSize().chromeStart().testTag(LibraryTestTags.SETTINGS)) {
        val twoColumns = maxWidth >= TWO_COLUMNS
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = insets.calculateTopPadding())
                .padding(horizontal = 18.dp)
        ) {
            Column(Modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp).reveal(0)) {
                Text(stringResource(R.string.settings_title), style = FlowFolioType.screenTitle.merge(FlowFolio.title))
                Text(
                    stringResource(R.string.settings_sub),
                    style = FlowFolioType.sub.merge(FlowFolio.hint),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (twoColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(Modifier.weight(1f)) {
                        AppearanceGroup(settings, actions)
                        ReadingGroup(settings, actions)
                    }
                    Column(Modifier.weight(1f)) {
                        TextGroup(settings.text, 2, actions.onReaderText)
                        LibraryGroup(settings, actions)
                        ConversionGroup(settings, actions)
                    }
                }
            } else {
                AppearanceGroup(settings, actions)
                TextGroup(settings.text, 2, actions.onReaderText)
                ReadingGroup(settings, actions)
                LibraryGroup(settings, actions)
                ConversionGroup(settings, actions)
            }
            Spacer(Modifier.height(insets.calculateBottomPadding() + chrome.calculateBottomPadding() + 24.dp))
        }
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

private val TWO_COLUMNS = 600.dp
