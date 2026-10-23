package com.flowfolio.feature.library

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioProgressBar
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookFormat
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.OutlineEntry
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
internal fun OverviewPanel(state: DetailsUiState, book: Book) {
    Column(Modifier.fillMaxWidth()) {
        book.details.description?.let {
            Text(
                it,
                style = FlowFolioType.sub.copy(fontSize = 15.sp, lineHeight = 23.sp).merge(FlowFolio.item),
                modifier = Modifier.padding(bottom = 22.dp)
            )
        }
        Text(
            stringResource(R.string.percent_complete, (book.progress * PERCENT).roundToInt()),
            style = FlowFolioType.rowLabel.merge(FlowFolio.title),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        FlowFolioProgressBar(book.progress)
        Row(
            Modifier.fillMaxWidth().padding(top = 26.dp).height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Stat(readTime(state.readMillis), R.string.stat_read, Modifier.weight(1f))
            StatRule()
            Stat(state.ribbons.toString(), R.string.stat_ribbons, Modifier.weight(1f))
            StatRule()
            Stat(state.bookShelves.size.toString(), R.string.stat_shelves, Modifier.weight(1f))
        }
        if (state.bookShelves.isNotEmpty()) {
            Text(
                state.bookShelves.joinToString(SEPARATOR) { it.name },
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
            )
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

@Composable
private fun Stat(value: String, label: Int, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = FlowFolioType.stat.merge(FlowFolio.item))
        Text(
            stringResource(label).uppercase(),
            style = FlowFolioType.eyebrow.merge(FlowFolio.hint),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun StatRule() {
    Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 4.dp).background(FlowFolio.palette.edge))
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
        index < current -> p.item to 1f
        index == current -> p.title to 1f
        else -> p.hint to DOT_IDLE_ALPHA
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
