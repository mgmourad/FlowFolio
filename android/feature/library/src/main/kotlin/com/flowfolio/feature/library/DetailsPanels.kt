package com.flowfolio.feature.library

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.data.DefaultBookTagRepository
import com.flowfolio.core.designsystem.ButtonStyle
import com.flowfolio.core.designsystem.DialogAction
import com.flowfolio.core.designsystem.DialogField
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioChip
import com.flowfolio.core.designsystem.FlowFolioDialog
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioProgressBar
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.tint
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookFormat
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.OutlineEntry
import com.flowfolio.core.model.ReadingIntent
import com.flowfolio.core.model.pageLabel
import kotlin.math.roundToInt

/**
 * One quiet line under the author: edition, format, printed pages and file size, separated by middle dots, with a
 * warning when the file is missing. Facts, not buttons, so no pills.
 */
@Composable
internal fun DetailsMeta(state: DetailsUiState, book: Book, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val format = stringResource(book.formatLabel())
    // An EPUB's pages depend on the text size: shown only when the book lists its printed pages.
    val pages = if (book.pageCount > 0) {
        pluralStringResource(R.plurals.page_count, book.pageCount, book.pageCount)
    } else {
        null
    }
    val parts =
        listOfNotNull(book.details.edition, format, pages, Formatter.formatShortFileSize(context, book.sizeBytes))
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            parts.joinToString(SEPARATOR),
            style = FlowFolioType.sub.merge(FlowFolio.hint),
            textAlign = TextAlign.Center
        )
        if (state.availability == Availability.MISSING) {
            Text(
                stringResource(R.string.badge_missing),
                style = FlowFolioType.rowDetail.merge(FlowFolio.subItem),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * Overview: what the book is about, how far along (the percentage over a slim bar), and three figures side by side,
 * split by hairlines: time read, ribbons, shelves.
 */
@Composable
internal fun OverviewPanel(state: DetailsUiState, book: Book, actions: DetailsActions = DetailsActions()) {
    val p = FlowFolio.palette
    Column(Modifier.fillMaxWidth()) {
        book.details.description?.let {
            Text(it, style = FlowFolioType.body.copy(fontSize = 14.5.sp, lineHeight = 23.sp, color = p.ink2))
        }
        BookChips(state, book, actions, Modifier.padding(top = 14.dp))
        if (book.format == BookFormat.PDF) FlowBox(book, Modifier.padding(top = 16.dp))
        state.bookGoal?.let { goal ->
            Text(
                stringResource(R.string.goal_book_progress, readTime(state.readMillis), readTime(goal.targetMillis)),
                style = FlowFolioType.rowLabel.copy(color = p.ink),
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
            )
            FlowFolioProgressBar(goal.progress(state.readMillis))
            if (goal.exceeded(state.readMillis)) {
                Text(
                    stringResource(R.string.goal_exceeded_tip),
                    style = FlowFolioType.rowDetail.copy(color = p.ink2),
                    modifier = Modifier.padding(top = 6.dp).testTag(LibraryTestTags.GOAL_EXCEEDED_TIP)
                )
            }
        }
    }
}

/** Time read as the mock shows it: "14h", "2h 05m", "35m", or "<1m" for a glance. */
@Composable
internal fun readTime(millis: Long): String {
    val minutes = millis / MS_PER_MINUTE
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        minutes < 1 -> stringResource(R.string.read_under_minute)
        hours == 0L -> stringResource(R.string.read_minutes, minutes)
        rest == 0L || hours >= WHOLE_HOURS_FROM -> stringResource(R.string.read_hours, hours)
        else -> stringResource(R.string.read_hours_minutes, hours, rest.toInt())
    }
}

/**
 * Contents (`.toc`): the PDF's top-level outline with its page, chapters already read marked in the item colour and
 * the current one in the title colour. Tapping a chapter opens the reader there.
 */
@Composable
internal fun ContentsPanel(contents: DetailsContents, book: Book, onChapter: (Int) -> Unit) {
    when (contents) {
        DetailsContents.Loading -> Unit

        DetailsContents.None -> Text(
            stringResource(R.string.contents_none),
            style = FlowFolioType.sub.merge(FlowFolio.hint)
        )

        is DetailsContents.Ready -> Column(Modifier.testTag(LibraryTestTags.CONTENTS)) {
            val current = currentChapter(contents.entries, book.lastPage)
            contents.entries.forEachIndexed { i, entry ->
                ChapterRow(entry, book.pageLabel(entry.pageIndex), i, current, onChapter)
                HorizontalDivider(color = FlowFolio.palette.edge)
            }
        }
    }
}

@Composable
private fun ChapterRow(entry: OutlineEntry, label: String, index: Int, current: Int, onChapter: (Int) -> Unit) {
    val p = FlowFolio.palette
    val (dot, dotAlpha) = when {
        index < current -> p.ink to 1f
        index == current -> p.ink to 1f
        else -> p.ink2 to DOT_IDLE_ALPHA
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChapter(entry.pageIndex) }
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).alpha(dotAlpha).background(dot, CircleShape))
        Text(
            entry.title,
            style = FlowFolioType.rowLabel.merge(if (index == current) FlowFolio.title else FlowFolio.item),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 10.dp, end = 12.dp)
        )
        if (entry.pageIndex >= 0) {
            Text(
                stringResource(R.string.page_short, label),
                style = FlowFolioType.sub.merge(FlowFolio.hint)
            )
        }
    }
}

/** The chapter containing [lastPage]: the last entry starting at or before it; -1 when not started. */
internal fun currentChapter(entries: List<OutlineEntry>, lastPage: Int?): Int {
    if (lastPage == null) return -1
    // Nearest by page, not by place in the list: a stray entry listed late can point early in the book.
    return entries.withIndex()
        .filter { it.value.pageIndex <= lastPage }
        .maxWithOrNull(compareBy({ it.value.pageIndex }, { it.index }))
        ?.index ?: -1
}

private const val PERCENT = 100
private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L

/** From ten hours on, minutes are noise. */
private const val WHOLE_HOURS_FROM = 10L
private const val DOT_IDLE_ALPHA = 0.4f
private const val SEPARATOR = " \u00B7 "

/** The Notebook tab (ADR-0052): this book's marks by kind, and its whole notebook a tap away. */
@Composable
internal fun NotebookPanel(state: DetailsUiState, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        CountPills(state.counts)
        Text(
            if (state.counts.total == 0) {
                stringResource(R.string.notebook_book_empty)
            } else {
                pluralStringResource(R.plurals.details_marks, state.counts.total, state.counts.total)
            },
            style = FlowFolioType.sub.copy(color = FlowFolio.palette.ink2),
            modifier = Modifier.padding(top = 14.dp)
        )
        com.flowfolio.core.designsystem.FlowFolioButton(
            stringResource(R.string.details_open_notebook),
            onOpen,
            Modifier.padding(top = 16.dp),
            style = com.flowfolio.core.designsystem.ButtonStyle.SOFT,
            icon = com.flowfolio.core.designsystem.Glyph.Notebook
        )
    }
}

/** The four fact tiles (`.facts`, ADR-0052): Complete, Read, Ribbons and Shelves, named singular or plural. */
@Composable
internal fun FactTiles(state: DetailsUiState, book: Book, wide: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val tile = Modifier.weight(1f).fillMaxHeight()
        Stat(
            stringResource(R.string.percent_value, (book.progress * PERCENT).roundToInt()),
            stringResource(R.string.stat_complete),
            wide,
            tile
        )
        Stat(readTime(state.readMillis), stringResource(R.string.stat_read), wide, tile)
        Stat(
            state.counts.bookmarks.toString(),
            pluralStringResource(R.plurals.stat_ribbons_n, state.counts.bookmarks),
            wide,
            tile
        )
        Stat(
            state.bookShelves.size.toString(),
            pluralStringResource(R.plurals.stat_shelves_n, state.bookShelves.size),
            wide,
            tile
        )
    }
}

/** One tile: the figure in Fraunces over its name, on the second surface; centred folded, start-aligned unfolded. */
@Composable
private fun Stat(value: String, label: String, wide: Boolean, modifier: Modifier) {
    val p = FlowFolio.palette
    Column(
        modifier
            .background(p.surface2, RoundedCornerShape(16.dp))
            .padding(horizontal = if (wide) 10.dp else 4.dp, vertical = if (wide) 14.dp else 12.dp),
        horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally
    ) {
        Text(value, style = FlowFolioType.stat.copy(fontSize = 22.sp, color = p.ink), maxLines = 1)
        Text(
            label.uppercase(),
            style = FlowFolioType.eyebrow.copy(fontSize = 9.5.sp, letterSpacing = 0.4.sp, color = p.ink3),
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
