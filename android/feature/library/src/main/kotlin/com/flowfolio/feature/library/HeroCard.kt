package com.flowfolio.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioPrimaryButton
import com.flowfolio.core.designsystem.FlowFolioProgressBar
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.coverSpot
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookFormat
import com.flowfolio.core.model.pageLabel
import com.flowfolio.core.model.printedPages

/**
 * `.hero-card`: the book in progress, how long it has been read ([readMillis]) and how far along, Resume, and a row of
 * shortcuts under it all: its Dynamic Flow (lit when on), its annotations, its bookmarks and the shelf it is on
 * ([shelfId]; owner, 2026-10-22, and M3T1).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContinueReadingCard(
    item: LibraryItem,
    actions: LibraryActions,
    shelfId: Long,
    readMillis: Long,
    modifier: Modifier = Modifier
) {
    val book = item.book
    FlowFolioCard(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag(LibraryTestTags.HERO),
        radius = 26.dp
    ) {
        HeroGlow(FlowFolio.palette.item)
        Column {
            // The cover at the top, its top edge level with the title (owner, M3T1).
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                val cover by rememberCover(book)
                actions.heroSpot.face = book.face(cover)
                Box(Modifier.padding(top = TITLE_TOP).coverSpot(actions.heroSpot)) {
                    ShelfBook(item, HERO_ORIGIN, actions, width = COVER_WIDTH, height = COVER_HEIGHT)
                }
                Column(Modifier.weight(1f)) {
                    HeroDetails(book, readMillis)
                    FlowFolioPrimaryButton(
                        text = stringResource(R.string.action_resume),
                        onClick = { actions.onResume(item) },
                        modifier = Modifier.padding(top = 14.dp).testTag(LibraryTestTags.RESUME),
                        icon = { PlayGlyph() }
                    )
                }
            }
            // The shortcuts on a line of their own under the cover and the words, room for more (owner, M3T1).
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) { HeroShortcuts(book, actions, shelfId) }
        }
    }
}

/** `.hero-card::before`: a soft glow in the item colour, top right. */
@Composable
private fun BoxScope.HeroGlow(glow: Color) {
    Box(
        Modifier.matchParentSize().drawBehind {
            val r = HERO_GLOW.toPx()
            val centre = Offset(size.width + r * GLOW_SHIFT, -r * GLOW_SHIFT)
            drawCircle(
                Brush.radialGradient(listOf(glow.copy(alpha = HERO_GLOW_ALPHA), Color.Transparent), centre, r),
                radius = r,
                center = centre
            )
        }
    )
}

/** The card's words: the eyebrow, the title and author, and the book's progress and read time. */
@Composable
private fun HeroDetails(book: Book, readMillis: Long) {
    Text(
        stringResource(R.string.continue_reading).uppercase(),
        style = FlowFolioType.eyebrow.merge(FlowFolio.subItem)
    )
    Text(
        book.title,
        style = FlowFolioType.heroTitle.copy(fontSize = HERO_TITLE_SP).merge(FlowFolio.item),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
    book.author?.let {
        Text(
            it,
            style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    // Read time first, then the progress with its bar, which ends the words (owner, M3T1).
    HeroMeasure(
        Glyph.Clock,
        stringResource(R.string.hero_read_time),
        readTime(readMillis),
        Modifier.padding(top = 12.dp)
    )
    HeroMeasure(
        Glyph.OpenBook,
        stringResource(R.string.hero_progress),
        stringResource(R.string.page_of, book.pageLabel(book.lastPage ?: 0), book.printedPages()),
        Modifier.padding(top = 8.dp)
    )
    FlowFolioProgressBar(book.progress, Modifier.padding(top = 6.dp))
}

/**
 * A measure of the book on one line: its glyph and name, and its value at the end ("p. 90 of 201"). When both don't
 * fit (a long book on the cover screen), the value goes under the name rather than cutting the name short.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroMeasure(glyph: Glyph, name: String, value: String, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FlowFolioGlyph(glyph, contentDescription = null, size = 18.dp, glow = false)
            Text(
                name,
                style = FlowFolioType.rowDetail.merge(FlowFolio.item),
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Text(value, style = FlowFolioType.rowDetail.merge(FlowFolio.hint), maxLines = 1)
    }
}

/** Continue Reading's shortcuts: the book's Dynamic Flow, its annotations, its bookmarks, and its shelf. */
@Composable
private fun HeroShortcuts(book: Book, actions: LibraryActions, shelfId: Long) {
    val onFlow = actions.onFlow
    if (onFlow != null && book.format == BookFormat.PDF) {
        val on = book.flowMode == true
        FlowFolioIconButton(onClick = { onFlow(book) }, modifier = Modifier.testTag(LibraryTestTags.HERO_FLOW)) {
            // Lit and glowing while the book is read in Dynamic Flow, dim while it is read as the PDF.
            FlowFolioGlyph(
                Glyph.DynamicFlow,
                stringResource(if (on) R.string.hero_flow_on else R.string.hero_flow_off),
                color = if (on) FlowFolio.palette.title else FlowFolio.palette.hint.copy(alpha = FLOW_OFF_ALPHA),
                glow = on
            )
        }
    }
    actions.onAnnotations?.let { open ->
        FlowFolioIconButton(onClick = { open(book) }, modifier = Modifier.testTag(LibraryTestTags.HERO_ANNOTATIONS)) {
            FlowFolioGlyph(Glyph.Highlight, stringResource(R.string.hero_annotations))
        }
    }
    actions.onBookmarks?.let { open ->
        FlowFolioIconButton(onClick = { open(book) }, modifier = Modifier.testTag(LibraryTestTags.HERO_BOOKMARKS)) {
            FlowFolioGlyph(Glyph.Bookmark, stringResource(R.string.hero_bookmarks), color = FlowFolio.palette.subItem)
        }
    }
    FlowFolioIconButton(
        onClick = { actions.onOpenShelf(shelfId) },
        modifier = Modifier.testTag(LibraryTestTags.HERO_SHELF)
    ) {
        FlowFolioGlyph(Glyph.Shelves, stringResource(R.string.hero_open_shelf))
    }
}

private const val HERO_GLOW_ALPHA = 0.22f
private const val FLOW_OFF_ALPHA = 0.55f
private val HERO_TITLE_SP = 22.sp
private val COVER_WIDTH = 116.dp
private val COVER_HEIGHT = 170.dp

/** The eyebrow's height and the gap under it: where the title starts, so the cover's top is level with it. */
private val TITLE_TOP = 24.dp
private const val GLOW_SHIFT = 0.1f
private val HERO_GLOW = 110.dp
