package com.flowfolio.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.color
import com.flowfolio.core.model.Bookmark
import com.flowfolio.core.model.BookmarkColor

/**
 * The ribbon button (`#rMark`): outlined when the page has no ribbon, in the ribbon's colour when it has one. A tap
 * opens a strip of the app's colours ([ColourStrip]) to put a ribbon on the page (or recolour it) and, when there is
 * one, to remove it.
 */
@Composable
internal fun BookmarkButton(mark: ChromeMark, actions: ChromeActions) {
    FlowFolioIconButton(
        onClick = { actions.onPanel(Panel.MARK) },
        glass = true,
        modifier = Modifier.testTag(ReaderTestTags.BOOKMARK)
    ) {
        FlowFolioGlyph(
            Glyph.Bookmark,
            stringResource(if (mark.color != null) R.string.reader_bookmarked else R.string.reader_bookmark),
            color = mark.color?.color ?: FlowFolio.palette.subItem,
            glow = mark.color != null
        )
    }
}

/** The colour choices, the current one ringed, then a remove button when the page has a ribbon. */
@Composable
internal fun ColourStrip(mark: ChromeMark, onChoose: (BookmarkColor?) -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(ReaderTestTags.BOOKMARK_COLOURS),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookmarkColor.entries.forEach { c ->
            val colour = c.color
            val ring = FlowFolio.palette.text
            Box(
                Modifier
                    .size(SWATCH)
                    .semantics {
                        contentDescription = c.name
                        selected = c == mark.color
                    }
                    .clip(CircleShape)
                    .drawBehind { drawCircle(colour) }
                    .then(if (c == mark.color) Modifier.border(2.5.dp, ring, CircleShape) else Modifier)
                    .clickable { onChoose(c) }
            )
        }
        if (mark.color != null) {
            Box(
                Modifier.size(SWATCH).clip(CircleShape).clickable { onChoose(null) },
                contentAlignment = Alignment.Center
            ) {
                FlowFolioGlyph(
                    Glyph.Trash,
                    stringResource(R.string.reader_bookmark_remove),
                    color = FlowFolio.palette.hint,
                    size = 22.dp,
                    glow = false
                )
            }
        }
    }
}

private val SWATCH = 32.dp

/**
 * The book's ribbons under the colour strip (owner, 2026-10-18): each with its colour, page and chapter; a tap goes
 * there. Long books keep the list to a few rows and scroll.
 */
@Composable
internal fun RibbonList(marks: List<Bookmark>, navigation: BookNavigation, onGo: (Int) -> Unit) {
    if (marks.isEmpty()) return
    HorizontalDivider(color = FlowFolio.palette.edge)
    Text(
        stringResource(R.string.reader_ribbons_in_book).uppercase(),
        style = FlowFolioType.eyebrow.merge(FlowFolio.subItem),
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
    )
    LazyColumn(Modifier.heightIn(max = RIBBON_LIST_MAX)) {
        items(marks, key = { it.id }) { m ->
            val chapter = PageLookup.chapterAt(navigation.outline, m.page).takeIf { it >= 0 }
                ?.let { navigation.outline[it].title }
            Row(
                Modifier.fillMaxWidth().clickable { onGo(m.page) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tint = m.color.color
                Canvas(Modifier.size(10.dp, 18.dp)) { drawRect(tint) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        stringResource(R.string.reader_page_short, PageLookup.labelOf(m.page, navigation.labels)),
                        style = FlowFolioType.rowLabel.merge(FlowFolio.item)
                    )
                    chapter?.let {
                        Text(
                            it,
                            style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private val RIBBON_LIST_MAX = 320.dp
