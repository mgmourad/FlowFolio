package com.flowfolio.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.model.ReadingFont

/**
 * The seven reading fonts (ADR-0027) as cards of one size in a row that scrolls: "Ag" in the font itself at one size
 * (so its real shape, x-height and weight show), its name below in the app's own face. The chosen one is filled and
 * ringed. Shared by the reader's "Aa" sheet and Settings. The first card is the book's own font ([bookLabel]), drawn in
 * [bookFont] when the open book has one.
 */
@Composable
fun FlowFolioFontPicker(
    selected: ReadingFont,
    onSelect: (ReadingFont) -> Unit,
    bookLabel: String,
    modifier: Modifier = Modifier,
    bookFont: FontFamily? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val p = FlowFolio.palette
    val list = rememberLazyListState(initialFirstVisibleItemIndex = ReadingFont.entries.indexOf(selected))
    // A saved scroll position can hide the chosen card: bring it into view whenever the choice changes.
    LaunchedEffect(selected) {
        val index = ReadingFont.entries.indexOf(selected)
        if (list.layoutInfo.visibleItemsInfo.none { it.index == index }) list.animateScrollToItem(index)
    }
    LazyRow(
        modifier.fillMaxWidth().selectableGroup(),
        state = list,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ReadingFont.entries) { font ->
            val on = font == selected
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .size(FONT_CARD_WIDTH, FONT_CARD_HEIGHT)
                    .clip(shape)
                    .background(if (on) p.selected else Color.Transparent)
                    .border(if (on) 2.dp else 1.dp, if (on) p.ink else p.ink2.copy(alpha = OUTLINE_ALPHA), shape)
                    .selectable(on, role = Role.RadioButton) { onSelect(font) }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Every sample on one baseline, whatever the font's own ascent and descent, with room above and below.
                Box(Modifier.fillMaxWidth().height(SAMPLE_HEIGHT), contentAlignment = Alignment.TopCenter) {
                    Text(
                        "Ag",
                        modifier = Modifier.paddingFromBaseline(top = SAMPLE_BASELINE),
                        style = TextStyle(
                            fontFamily = if (font ==
                                ReadingFont.BOOK
                            ) {
                                bookFont ?: FontFamily.Serif
                            } else {
                                font.fontFamily
                            },
                            fontSize = 28.sp,
                            fontStyle = if (font == ReadingFont.BOOK && bookFont == null) FontStyle.Italic else null
                        ),
                        color = if (on) p.ink else p.ink,
                        maxLines = 1
                    )
                }
                Text(
                    if (font == ReadingFont.BOOK) bookLabel else font.displayName,
                    style = FlowFolioType.pill.merge(if (on) FlowFolio.title else FlowFolio.hint),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val FONT_CARD_WIDTH = 92.dp
private val FONT_CARD_HEIGHT = 86.dp
private val SAMPLE_HEIGHT = 46.dp
private val SAMPLE_BASELINE = 34.dp
private const val OUTLINE_ALPHA = 0.35f
