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
import com.flowfolio.core.model.pageLabel
import com.flowfolio.core.model.printedPages

/**
 * `.hero-card`: the book in progress, how far along, Resume, and shortcuts to its annotations, its bookmarks and the
 * shelf it is on ([shelfId]; owner, 2026-10-22).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContinueReadingCard(
    item: LibraryItem,
    actions: LibraryActions,
    shelfId: Long,
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
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            val cover by rememberCover(book)
            actions.heroSpot.face = book.face(cover)
            Box(Modifier.coverSpot(actions.heroSpot)) {
                ShelfBook(item, HERO_ORIGIN, actions, width = 104.dp, height = 152.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.continue_reading).uppercase(),
                    style = FlowFolioType.eyebrow.merge(FlowFolio.subItem)
                )
                Text(
                    book.title,
                    style = FlowFolioType.heroTitle.merge(FlowFolio.item),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                )
                book.author?.let { Text(it, style = FlowFolioType.sub.merge(FlowFolio.hint), maxLines = 1) }
                FlowFolioProgressBar(book.progress, Modifier.padding(top = 12.dp, bottom = 6.dp))
                Text(
                    stringResource(R.string.page_of, book.pageLabel(book.lastPage ?: 0), book.printedPages()),
                    style = FlowFolioType.rowDetail.merge(FlowFolio.hint)
                )
                // On a narrow card the shortcuts wrap under Resume.
                FlowRow(
                    Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    FlowFolioPrimaryButton(
                        text = stringResource(R.string.action_resume),
                        onClick = { actions.onResume(item) },
                        modifier = Modifier.testTag(LibraryTestTags.RESUME),
                        icon = { PlayGlyph() }
                    )
                    HeroShortcuts(book, actions, shelfId)
                }
            }
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

/** Continue Reading's shortcuts: the book's annotations, its bookmarks, and its shelf. */
@Composable
private fun HeroShortcuts(book: Book, actions: LibraryActions, shelfId: Long) {
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
private const val GLOW_SHIFT = 0.1f
private val HERO_GLOW = 110.dp
