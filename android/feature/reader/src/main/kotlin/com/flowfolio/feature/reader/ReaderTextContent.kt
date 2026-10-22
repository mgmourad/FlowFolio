package com.flowfolio.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioFontPicker
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.displayName
import com.flowfolio.core.designsystem.fontFamily
import com.flowfolio.core.model.LineSpacing
import com.flowfolio.core.model.PageMargins
import com.flowfolio.core.model.ReaderText
import com.flowfolio.core.model.ReadingFont

/**
 * How an EPUB's text is set (M3): the reading font, the size (also by pinching the page), line spacing and margins
 * as small pictures, and justification and hyphenation as chips. Every change lays the book out again at the reader's
 * place.
 */
@Composable
internal fun TextChoice(text: ReaderText, onChange: (ReaderText) -> Unit) {
    FlowFolioFontPicker(
        text.font,
        { onChange(text.copy(font = it)) },
        Modifier.padding(bottom = 6.dp).testTag(ReaderTestTags.SETTING_FONT)
    )
    LabeledRow(stringResource(R.string.reader_text_size)) {
        SizeStepper(text.sizeSp) { onChange(text.copy(sizeSp = it)) }
    }
    LabeledRow(stringResource(R.string.reader_line_spacing)) {
        IconChoice(
            listOf(
                stringResource(R.string.reader_spacing_tight),
                stringResource(R.string.reader_spacing_normal),
                stringResource(R.string.reader_spacing_relaxed),
                stringResource(R.string.reader_spacing_loose)
            ),
            LineSpacing.entries.indexOf(text.lineSpacing),
            ReaderTestTags.SETTING_SPACING,
            { onChange(text.copy(lineSpacing = LineSpacing.entries[it])) }
        ) { i, color -> spacingIcon(i, color) }
    }
    LabeledRow(stringResource(R.string.reader_margins)) {
        IconChoice(
            listOf(
                stringResource(R.string.reader_margins_narrow),
                stringResource(R.string.reader_margins_normal),
                stringResource(R.string.reader_margins_wide)
            ),
            PageMargins.entries.indexOf(text.margins),
            ReaderTestTags.SETTING_MARGINS,
            { onChange(text.copy(margins = PageMargins.entries[it])) }
        ) { i, color -> marginsIcon(i, color) }
    }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleChip(stringResource(R.string.reader_justify), text.justify, ReaderTestTags.SETTING_JUSTIFY) {
            onChange(text.copy(justify = it))
        }
        ToggleChip(stringResource(R.string.reader_hyphenate), text.hyphenate, ReaderTestTags.SETTING_HYPHENATE) {
            onChange(text.copy(hyphenate = it))
        }
    }
}

/** Smaller and larger text, one step at a time, with the size between. */
@Composable
private fun SizeStepper(sizeSp: Int, onSize: (Int) -> Unit) {
    val p = FlowFolio.palette
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val smaller = stringResource(R.string.reader_text_smaller)
        val larger = stringResource(R.string.reader_text_larger)
        FlowFolioIconButton(
            onClick = { onSize((sizeSp - 1).coerceAtLeast(ReaderText.MIN_SIZE_SP)) },
            glass = true,
            modifier = Modifier.semantics { contentDescription = smaller }.testTag(ReaderTestTags.TEXT_SMALLER)
        ) { Text("A", style = TextStyle(fontSize = 13.sp), color = p.item) }
        Text(
            "$sizeSp",
            style = FlowFolioType.rowLabel.merge(FlowFolio.title),
            modifier = Modifier.widthIn(min = 28.dp)
        )
        FlowFolioIconButton(
            onClick = { onSize((sizeSp + 1).coerceAtMost(ReaderText.MAX_SIZE_SP)) },
            glass = true,
            modifier = Modifier.semantics { contentDescription = larger }.testTag(ReaderTestTags.TEXT_LARGER)
        ) { Text("A", style = TextStyle(fontSize = 21.sp), color = p.item) }
    }
}

/** Three lines of text, further apart for each looser spacing. */
private fun DrawScope.spacingIcon(index: Int, color: Color) {
    val gap = size.height * (SPACING_GAP_BASE + SPACING_GAP_STEP * index)
    val top = (size.height - 2 * gap) / 2
    for (k in 0..2) {
        val y = top + k * gap
        drawLine(
            color,
            Offset(0f, y),
            Offset(size.width, y),
            strokeWidth = LINE_STROKE * density,
            cap = StrokeCap.Round
        )
    }
}

/** A page with lines of text, set further in from its sides for each wider margin. */
private fun DrawScope.marginsIcon(index: Int, color: Color) {
    val stroke = LINE_STROKE * density
    drawRoundRect(
        color,
        style = Stroke(stroke * PAGE_STROKE),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke)
    )
    val inset = size.width * (MARGIN_BASE + MARGIN_STEP * index)
    for (k in 1..TEXT_LINES) {
        val y = size.height * k / (TEXT_LINES + 1f)
        drawLine(color, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

private const val LINE_STROKE = 1.8f
private const val PAGE_STROKE = 0.8f
private const val SPACING_GAP_BASE = 0.18f
private const val SPACING_GAP_STEP = 0.07f
private const val MARGIN_BASE = 0.14f
private const val MARGIN_STEP = 0.1f
private const val TEXT_LINES = 3
