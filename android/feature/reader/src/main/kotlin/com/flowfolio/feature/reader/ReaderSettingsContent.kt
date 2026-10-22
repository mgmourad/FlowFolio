package com.flowfolio.feature.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioSegmented
import com.flowfolio.core.designsystem.FlowFolioSlider
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.model.ControlsAutoHide
import com.flowfolio.core.model.PageTurn
import com.flowfolio.core.model.PaperStyle
import com.flowfolio.core.model.UserSettings

/**
 * The reading sheet ("Aa"), in tabs so it stays under half the screen (owner, 2026-10-21): Text (EPUBs: font, size,
 * spacing, margins, justification and hyphenation), Page (paper, how pages turn, direction, spreads) and Controls
 * (when the bars hide, sounds, haptics). The tab row stays put; each tab scrolls if it must. Shown on glass by
 * [ReaderPanels].
 */
@Composable
internal fun ColumnScope.ReaderSettingsContent(
    reflowable: Boolean,
    bookFont: android.graphics.Typeface? = null,
    viewModel: ReaderSettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val tabs = buildList {
        if (reflowable) add(SheetTab(stringResource(R.string.reader_tab_text), Glyph.TextSize, ReaderTestTags.TAB_TEXT))
        add(SheetTab(stringResource(R.string.reader_tab_page), Glyph.Page, ReaderTestTags.TAB_PAGE))
        add(SheetTab(stringResource(R.string.reader_tab_controls), Glyph.Sliders, ReaderTestTags.TAB_CONTROLS))
    }
    var selected by rememberSaveable(reflowable) { mutableIntStateOf(0) }
    Column(Modifier.testTag(ReaderTestTags.SETTINGS_SHEET)) {
        SheetTabs(tabs, selected.coerceIn(0, tabs.lastIndex)) { selected = it }
    }
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp, start = 4.dp, end = 4.dp)
    ) {
        when (tabs[selected.coerceIn(0, tabs.lastIndex)].tag) {
            ReaderTestTags.TAB_TEXT -> TextChoice(settings.text, bookFont, viewModel::setText)
            ReaderTestTags.TAB_PAGE -> PageChoice(settings, viewModel)
            else -> ControlsChoice(settings, viewModel)
        }
    }
}

/** The paper, how pages turn, which way they run, and the spread layout. */
@Composable
private fun PageChoice(settings: UserSettings, viewModel: ReaderSettingsViewModel) {
    val reveal = LocalPaperReveal.current
    PaperSwatches(settings.paperStyle) { style ->
        if (reveal != null) reveal.change(style, viewModel::setPaperStyle) else viewModel.setPaperStyle(style)
    }
    LabeledRow(stringResource(R.string.reader_turn)) {
        FlowFolioSegmented(
            listOf(
                stringResource(R.string.reader_turn_curl),
                stringResource(R.string.reader_turn_slide),
                stringResource(R.string.reader_turn_scroll)
            ),
            PageTurn.entries.indexOf(settings.pageTurn),
            { viewModel.setPageTurn(PageTurn.entries[it]) },
            modifier = Modifier.testTag(ReaderTestTags.SETTING_TURN)
        )
    }
    DirectionChoice(viewModel)
    val scroll = settings.pageTurn == PageTurn.SCROLL
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleChip(
            stringResource(R.string.reader_chip_spreads),
            settings.readerSpreads,
            ReaderTestTags.SETTING_SPREADS,
            enabled = !scroll,
            onChange = viewModel::setSpreads
        )
        ToggleChip(
            stringResource(R.string.reader_chip_cover),
            settings.spreadCoverAlone,
            ReaderTestTags.SETTING_COVER,
            enabled = settings.readerSpreads && !scroll,
            onChange = viewModel::setCoverAlone
        )
    }
}

/** Which way this book's pages run (a per-book choice): right to left for Arabic, Hebrew and similar books. */
@Composable
private fun DirectionChoice(viewModel: ReaderSettingsViewModel) {
    val rightToLeft by viewModel.rightToLeft.collectAsStateWithLifecycle()
    LabeledRow(stringResource(R.string.reader_direction)) {
        FlowFolioSegmented(
            listOf(
                stringResource(R.string.reader_direction_ltr_short),
                stringResource(R.string.reader_direction_rtl_short)
            ),
            if (rightToLeft) 1 else 0,
            { viewModel.setRightToLeft(it == 1) },
            modifier = Modifier.testTag(ReaderTestTags.SETTING_DIRECTION)
        )
    }
}

/** When the controls hide, the two paper sounds with their loudness, and riffle haptics. */
@Composable
private fun ControlsChoice(settings: UserSettings, viewModel: ReaderSettingsViewModel) {
    LabeledRow(stringResource(R.string.reader_auto_hide)) {
        FlowFolioSegmented(
            ControlsAutoHide.entries.map { stringResource(it.label) },
            ControlsAutoHide.entries.indexOf(settings.controlsAutoHide),
            { viewModel.setControlsAutoHide(ControlsAutoHide.entries[it]) },
            modifier = Modifier.testTag(ReaderTestTags.SETTING_AUTO_HIDE)
        )
    }
    val sounds = LocalReaderSounds.current
    SoundRow(
        stringResource(R.string.reader_chip_turn_sound),
        settings.turnSound,
        settings.turnVolume,
        ReaderTestTags.SETTING_TURN_SOUND to ReaderTestTags.SETTING_TURN_VOLUME,
        stringResource(R.string.reader_turn_volume),
        onToggle = viewModel::setTurnSound
    ) {
        viewModel.setTurnVolume(it)
        sounds?.let { s -> s.settings = s.settings.copy(turnVolume = it) }
        sounds?.previewTurn()
    }
    SoundRow(
        stringResource(R.string.reader_chip_riffle_sound),
        settings.riffleSound,
        settings.riffleVolume,
        ReaderTestTags.SETTING_RIFFLE_SOUND to ReaderTestTags.SETTING_RIFFLE_VOLUME,
        stringResource(R.string.reader_riffle_volume),
        onToggle = viewModel::setRiffleSound
    ) {
        viewModel.setRiffleVolume(it)
        sounds?.let { s -> s.settings = s.settings.copy(riffleVolume = it) }
        sounds?.previewRiffle()
    }
    Row(Modifier.padding(top = 8.dp)) {
        ToggleChip(
            stringResource(R.string.reader_chip_haptics),
            settings.scrubHaptics,
            ReaderTestTags.SETTING_HAPTICS,
            onChange = viewModel::setScrubHaptics
        )
    }
}

/**
 * A sound on one line: its switch as a chip, then its loudness. Letting go of the slider plays the sound at the new
 * loudness (ADR-0026).
 */
@Composable
private fun SoundRow(
    label: String,
    on: Boolean,
    volume: Float,
    tags: Pair<String, String>,
    volumeLabel: String,
    onToggle: (Boolean) -> Unit,
    onVolume: (Float) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ToggleChip(label, on, tags.first, Modifier.width(SOUND_CHIP), onChange = onToggle)
        FlowFolioSlider(
            volume,
            onValueChangeFinished = onVolume,
            label = volumeLabel,
            enabled = on,
            modifier = Modifier.weight(1f).padding(start = 12.dp).testTag(tags.second)
        )
    }
}

/** The auto-hide delay's short name. */
private val ControlsAutoHide.label: Int
    get() = when (this) {
        ControlsAutoHide.S3 -> R.string.reader_auto_hide_3
        ControlsAutoHide.S5 -> R.string.reader_auto_hide_5
        ControlsAutoHide.S8 -> R.string.reader_auto_hide_8
        ControlsAutoHide.S15 -> R.string.reader_auto_hide_15
        ControlsAutoHide.NEVER -> R.string.reader_auto_hide_never
    }

/** `.paper-sw`: a round swatch per paper showing "Aa" in its ink; the chosen one grows and gets a ring. */
@Composable
private fun PaperSwatches(selected: PaperStyle, onSelect: (PaperStyle) -> Unit) {
    val p = FlowFolio.palette
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp).testTag(ReaderTestTags.PAPER_SWATCHES),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        PaperStyle.entries.forEach { style ->
            val (paper, ink, label) = when (style) {
                PaperStyle.AUTO -> Triple(p.background, p.title, "A")
                PaperStyle.PAPER -> Triple(PageTone.PAPER.paper, PageTone.PAPER.ink!!, "Aa")
                PaperStyle.SEPIA -> Triple(PageTone.SEPIA.paper, PageTone.SEPIA.ink!!, "Aa")
                PaperStyle.NIGHT -> Triple(PageTone.NIGHT.paper, PageTone.NIGHT.ink!!, "Aa")
                PaperStyle.BLACK -> Triple(PageTone.BLACK.paper, PageTone.BLACK.ink!!, "Aa")
                PaperStyle.RUSTIC -> Triple(PageTone.RUSTIC.paper, PageTone.RUSTIC.ink!!, "Aa")
            }
            Swatch(style, label, paper, ink, style == selected) { onSelect(style) }
        }
    }
}

@Composable
private fun Swatch(style: PaperStyle, label: String, paper: Color, ink: Color, on: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (on) SELECTED_SCALE else 1f, tween(Motion.SPRING_MS, easing = Motion.Spring))
    val ring = FlowFolio.palette.title
    val edge = FlowFolio.palette.edge
    // The swatch and its caption are one target.
    Column(
        Modifier.selectable(on, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(SWATCH)
                .clip(CircleShape)
                .drawBehind { drawCircle(paper) }
                .border(if (on) 2.5.dp else 1.dp, if (on) ring else edge, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val face = FlowFolioType.heroTitle.fontFamily
            Text(label, style = TextStyle(color = ink, fontSize = 15.sp, fontFamily = face))
        }
        Text(
            stringResource(style.label),
            style = FlowFolioType.pill.merge(if (on) FlowFolio.title else FlowFolio.hint),
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

private val PaperStyle.label: Int
    get() = when (this) {
        PaperStyle.AUTO -> R.string.reader_paper_auto
        PaperStyle.PAPER -> R.string.reader_paper_paper
        PaperStyle.SEPIA -> R.string.reader_paper_sepia
        PaperStyle.NIGHT -> R.string.reader_paper_night
        PaperStyle.BLACK -> R.string.reader_paper_black
        PaperStyle.RUSTIC -> R.string.reader_paper_rustic
    }

private val SWATCH = 42.dp
private val SOUND_CHIP = 132.dp
private const val SELECTED_SCALE = 1.1f
