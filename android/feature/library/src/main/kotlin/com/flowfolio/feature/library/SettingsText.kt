package com.flowfolio.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioFontPicker
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.fontFamily
import com.flowfolio.core.model.LineSpacing
import com.flowfolio.core.model.PageMargins
import com.flowfolio.core.model.ReaderText

/**
 * Settings > Text (M3): how EPUBs are set, the same choices as the reader's "Aa" sheet, with a sample paragraph set
 * as the pages will be. PDFs keep their own type.
 */
@Composable
internal fun TextGroup(text: ReaderText, order: Int, onChange: (ReaderText) -> Unit) {
    Group(R.string.settings_text, order) {
        Label(
            R.string.settings_text_font,
            R.string.settings_text_font_detail,
            Modifier.padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 12.dp)
        )
        FlowFolioFontPicker(
            text.font,
            { onChange(text.copy(font = it)) },
            stringResource(R.string.settings_text_book_font),
            Modifier.testTag(LibraryTestTags.TEXT_FONT),
            contentPadding = PaddingValues(horizontal = 16.dp)
        )
        Sample(text)
        Divider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Label(R.string.settings_text_size, R.string.settings_text_size_detail, Modifier.weight(1f))
            SizeStepper(text.sizeSp) { onChange(text.copy(sizeSp = it)) }
        }
        Divider()
        ChoiceRow(
            R.string.settings_text_spacing,
            R.string.settings_text_spacing_detail,
            listOf(R.string.spacing_tight, R.string.spacing_normal, R.string.spacing_relaxed, R.string.spacing_loose),
            LineSpacing.entries.indexOf(text.lineSpacing),
            stacked = true
        ) { onChange(text.copy(lineSpacing = LineSpacing.entries[it])) }
        Divider()
        ChoiceRow(
            R.string.settings_text_margins,
            R.string.settings_text_margins_detail,
            listOf(R.string.margins_narrow, R.string.margins_normal, R.string.margins_wide),
            PageMargins.entries.indexOf(text.margins)
        ) { onChange(text.copy(margins = PageMargins.entries[it])) }
        Divider()
        SwitchRow(R.string.settings_text_justify, R.string.settings_text_justify_detail, text.justify, {
            onChange(text.copy(justify = it))
        })
        Divider()
        SwitchRow(R.string.settings_text_hyphenate, R.string.settings_text_hyphenate_detail, text.hyphenate, {
            onChange(text.copy(hyphenate = it))
        })
    }
}

/** A paragraph in the chosen font, size, spacing and alignment, so a change shows before a book is opened. */
@Composable
private fun Sample(text: ReaderText) {
    Text(
        stringResource(R.string.settings_text_sample),
        style = TextStyle(
            fontFamily = text.font.fontFamily,
            fontSize = text.sizeSp.sp,
            lineHeight = text.lineSpacing.factor.em,
            textAlign = if (text.justify) TextAlign.Justify else TextAlign.Start,
            hyphens = if (text.hyphenate) Hyphens.Auto else Hyphens.None,
            lineBreak = LineBreak.Paragraph
        ).merge(FlowFolio.text),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp + SAMPLE_INSET * text.margins.ordinal, vertical = 16.dp)
            .testTag(LibraryTestTags.TEXT_SAMPLE)
    )
}

/** Smaller and larger text, one step at a time, with the size between. */
@Composable
private fun SizeStepper(sizeSp: Int, onSize: (Int) -> Unit) {
    val p = FlowFolio.palette
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val smaller = stringResource(R.string.settings_text_smaller)
        val larger = stringResource(R.string.settings_text_larger)
        FlowFolioIconButton(
            onClick = { onSize((sizeSp - 1).coerceAtLeast(ReaderText.MIN_SIZE_SP)) },
            modifier = Modifier.semantics { contentDescription = smaller }.testTag(LibraryTestTags.TEXT_SMALLER),
            tooltip = smaller
        ) { Text("A", style = TextStyle(fontSize = 13.sp), color = p.item) }
        Text(
            "$sizeSp",
            style = FlowFolioType.rowLabel.merge(FlowFolio.title),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 28.dp)
        )
        FlowFolioIconButton(
            onClick = { onSize((sizeSp + 1).coerceAtMost(ReaderText.MAX_SIZE_SP)) },
            modifier = Modifier.semantics { contentDescription = larger }.testTag(LibraryTestTags.TEXT_LARGER),
            tooltip = larger
        ) { Text("A", style = TextStyle(fontSize = 21.sp), color = p.item) }
    }
}

/** How much further in the sample sits for each wider margin, a hint of the page's margins. */
private val SAMPLE_INSET = 10.dp
