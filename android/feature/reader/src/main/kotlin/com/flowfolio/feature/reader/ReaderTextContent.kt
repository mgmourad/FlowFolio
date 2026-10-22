package com.flowfolio.feature.reader

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioSegmented
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.displayName
import com.flowfolio.core.designsystem.fontFamily
import com.flowfolio.core.model.LineSpacing
import com.flowfolio.core.model.PageMargins
import com.flowfolio.core.model.ReaderText
import com.flowfolio.core.model.ReadingFont

/**
 * How an EPUB's text is set (M3): the reading font, each shown in its own face; the size, also changed by pinching the
 * page; line spacing; side margins; justification and hyphenation. Every change lays the book out again at the
 * reader's place.
 */
@Composable
internal fun TextChoice(text: ReaderText, onChange: (ReaderText) -> Unit) {
    Column {
        TextHeading(R.string.reader_text)
        FontPicker(text.font) { onChange(text.copy(font = it)) }
        SizeStepper(text.sizeSp) { onChange(text.copy(sizeSp = it)) }
        Label(R.string.reader_line_spacing)
        FlowFolioSegmented(
            listOf(
                stringResource(R.string.reader_spacing_tight),
                stringResource(R.string.reader_spacing_normal),
                stringResource(R.string.reader_spacing_relaxed),
                stringResource(R.string.reader_spacing_loose)
            ),
            LineSpacing.entries.indexOf(text.lineSpacing),
            { onChange(text.copy(lineSpacing = LineSpacing.entries[it])) },
            modifier = Modifier.testTag(ReaderTestTags.SETTING_SPACING)
        )
        Label(R.string.reader_margins)
        FlowFolioSegmented(
            listOf(
                stringResource(R.string.reader_margins_narrow),
                stringResource(R.string.reader_margins_normal),
                stringResource(R.string.reader_margins_wide)
            ),
            PageMargins.entries.indexOf(text.margins),
            { onChange(text.copy(margins = PageMargins.entries[it])) },
            modifier = Modifier.testTag(ReaderTestTags.SETTING_MARGINS)
        )
        SettingRow(
            title = stringResource(R.string.reader_justify),
            detail = stringResource(R.string.reader_justify_detail),
            checked = text.justify,
            enabled = true,
            tag = ReaderTestTags.SETTING_JUSTIFY,
            onChange = { onChange(text.copy(justify = it)) }
        )
        SettingRow(
            title = stringResource(R.string.reader_hyphenate),
            detail = stringResource(R.string.reader_hyphenate_detail),
            checked = text.hyphenate,
            enabled = true,
            tag = ReaderTestTags.SETTING_HYPHENATE,
            onChange = { onChange(text.copy(hyphenate = it)) }
        )
    }
}

/** The seven reading fonts in a row that scrolls, each name in its own face; the chosen one has a ring. */
@Composable
private fun FontPicker(selected: ReadingFont, onSelect: (ReadingFont) -> Unit) {
    val p = FlowFolio.palette
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag(ReaderTestTags.SETTING_FONT),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReadingFont.entries.forEach { font ->
            val on = font == selected
            val shape = RoundedCornerShape(14.dp)
            Column(
                Modifier
                    .clip(shape)
                    .border(if (on) 2.dp else 1.dp, if (on) p.title else p.hint.copy(alpha = OUTLINE_ALPHA), shape)
                    .selectable(on, role = Role.RadioButton) { onSelect(font) }
                    .widthIn(min = 76.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Aa", style = TextStyle(fontFamily = font.fontFamily, fontSize = 24.sp), color = p.item)
                Text(
                    font.displayName,
                    style = TextStyle(fontFamily = font.fontFamily, fontSize = 12.sp),
                    color = if (on) p.title else p.hint,
                    maxLines = 1
                )
            }
        }
    }
}

/** Smaller and larger text, one step at a time, with the size between. */
@Composable
private fun SizeStepper(sizeSp: Int, onSize: (Int) -> Unit) {
    val p = FlowFolio.palette
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(stringResource(R.string.reader_text_size), style = FlowFolioType.rowLabel.merge(FlowFolio.item))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val smaller = stringResource(R.string.reader_text_smaller)
            val larger = stringResource(R.string.reader_text_larger)
            FlowFolioIconButton(
                onClick = { onSize((sizeSp - 1).coerceAtLeast(ReaderText.MIN_SIZE_SP)) },
                glass = true,
                modifier = Modifier.semantics { contentDescription = smaller }.testTag(ReaderTestTags.TEXT_SMALLER)
            ) { Text("A", style = TextStyle(fontSize = 14.sp), color = p.item) }
            Text(
                "$sizeSp",
                style = FlowFolioType.rowLabel.merge(FlowFolio.title),
                modifier = Modifier.widthIn(min = 28.dp)
            )
            FlowFolioIconButton(
                onClick = { onSize((sizeSp + 1).coerceAtMost(ReaderText.MAX_SIZE_SP)) },
                glass = true,
                modifier = Modifier.semantics { contentDescription = larger }.testTag(ReaderTestTags.TEXT_LARGER)
            ) { Text("A", style = TextStyle(fontSize = 22.sp), color = p.item) }
        }
    }
}

@Composable
private fun TextHeading(text: Int) {
    Text(
        stringResource(text).uppercase(),
        style = FlowFolioType.eyebrow.merge(FlowFolio.subItem),
        modifier = Modifier.padding(top = 28.dp, bottom = 12.dp)
    )
}

@Composable
private fun Label(text: Int) {
    Text(
        stringResource(text),
        style = FlowFolioType.rowLabel.merge(FlowFolio.item),
        modifier = Modifier.padding(top = 16.dp, bottom = 10.dp)
    )
}

private const val OUTLINE_ALPHA = 0.35f
