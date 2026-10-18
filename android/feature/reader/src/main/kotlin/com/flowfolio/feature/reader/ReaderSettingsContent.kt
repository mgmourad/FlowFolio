package com.flowfolio.feature.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.flowfolio.core.designsystem.FlowFolioToggle
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.model.PageTurn
import com.flowfolio.core.model.PaperStyle

/**
 * The mock's reading sheet ("Aa", `#sheet`): the page colour as swatches (Auto, Paper, Sepia, Night, Black; photos keep
 * their colours), how pages turn (Curl, Slide, Scroll), and the spread layout options. Shown on glass by
 * [ReaderPanels].
 */
@Composable
internal fun ReaderSettingsContent(viewModel: ReaderSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Column(Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp).testTag(ReaderTestTags.SETTINGS_SHEET)) {
        Heading(R.string.reader_paper)
        val reveal = LocalPaperReveal.current
        PaperSwatches(settings.paperStyle) { style ->
            if (reveal != null) reveal.change(style, viewModel::setPaperStyle) else viewModel.setPaperStyle(style)
        }
        Text(
            stringResource(R.string.reader_paper_note),
            style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
            modifier = Modifier.padding(top = 10.dp)
        )
        Heading(R.string.reader_turn, Modifier.padding(top = 22.dp))
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
        DirectionChoice(viewModel)
        Heading(R.string.reader_layout, Modifier.padding(top = 22.dp))
        SettingRow(
            title = stringResource(R.string.reader_settings_spreads),
            detail = stringResource(R.string.reader_settings_spreads_detail),
            checked = settings.readerSpreads,
            enabled = settings.pageTurn != PageTurn.SCROLL,
            tag = ReaderTestTags.SETTING_SPREADS,
            onChange = viewModel::setSpreads
        )
        SettingRow(
            title = stringResource(R.string.reader_settings_cover),
            detail = stringResource(R.string.reader_settings_cover_detail),
            checked = settings.spreadCoverAlone,
            enabled = settings.readerSpreads && settings.pageTurn != PageTurn.SCROLL,
            tag = ReaderTestTags.SETTING_COVER,
            onChange = viewModel::setCoverAlone
        )
    }
}

/** Which way this book's pages run (a per-book choice): right to left for Arabic, Hebrew and similar books. */
@Composable
private fun DirectionChoice(viewModel: ReaderSettingsViewModel) {
    val rightToLeft by viewModel.rightToLeft.collectAsStateWithLifecycle()
    Heading(R.string.reader_direction, Modifier.padding(top = 22.dp))
    FlowFolioSegmented(
        listOf(stringResource(R.string.reader_direction_ltr), stringResource(R.string.reader_direction_rtl)),
        if (rightToLeft) 1 else 0,
        { viewModel.setRightToLeft(it == 1) },
        modifier = Modifier.testTag(ReaderTestTags.SETTING_DIRECTION)
    )
    Text(
        stringResource(R.string.reader_direction_note),
        style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
        modifier = Modifier.padding(top = 10.dp)
    )
}

@Composable
private fun Heading(text: Int, modifier: Modifier = Modifier) {
    Text(
        stringResource(text).uppercase(),
        style = FlowFolioType.eyebrow.merge(FlowFolio.subItem),
        modifier = modifier.padding(top = 6.dp, bottom = 12.dp)
    )
}

/** `.paper-sw`: a round swatch per paper showing "Aa" in its ink; the chosen one grows and gets a ring. */
@Composable
private fun PaperSwatches(selected: PaperStyle, onSelect: (PaperStyle) -> Unit) {
    val p = FlowFolio.palette
    Row(
        Modifier.fillMaxWidth().testTag(ReaderTestTags.PAPER_SWATCHES),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PaperStyle.entries.forEach { style ->
            val (paper, ink, label) = when (style) {
                PaperStyle.AUTO -> Triple(p.background, p.title, "A")
                PaperStyle.PAPER -> Triple(PageTone.PAPER.paper, PageTone.PAPER.ink!!, "Aa")
                PaperStyle.SEPIA -> Triple(PageTone.SEPIA.paper, PageTone.SEPIA.ink!!, "Aa")
                PaperStyle.NIGHT -> Triple(PageTone.NIGHT.paper, PageTone.NIGHT.ink!!, "Aa")
                PaperStyle.BLACK -> Triple(PageTone.BLACK.paper, PageTone.BLACK.ink!!, "Aa")
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
            Text(label, style = TextStyle(color = ink, fontSize = 16.sp, fontFamily = face))
        }
        Text(
            stringResource(style.label),
            style = FlowFolioType.pill.merge(if (on) FlowFolio.title else FlowFolio.hint),
            modifier = Modifier.padding(top = 8.dp)
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
    }

@Composable
private fun SettingRow(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 10.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = FlowFolioType.rowLabel.merge(FlowFolio.item))
            Text(detail, style = FlowFolioType.rowDetail.merge(FlowFolio.hint))
        }
        FlowFolioToggle(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

private val SWATCH = 46.dp
private const val SELECTED_SCALE = 1.12f
