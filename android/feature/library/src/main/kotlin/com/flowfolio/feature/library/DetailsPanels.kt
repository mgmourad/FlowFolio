package com.flowfolio.feature.library

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioPill
import com.flowfolio.core.designsystem.FlowFolioProgressBar
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookFormat
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.OutlineEntry
import com.flowfolio.core.model.pageLabel
import kotlin.math.roundToInt

/** `.meta-row`: page count, format and size, plus a warning when the file is missing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsPills(state: DetailsUiState, book: Book, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        book.details.edition?.let { FlowFolioPill(it, FlowFolio.subItem) }
        // An EPUB's pages depend on the text size: shown only when the book lists its printed pages.
        if (book.pageCount > 0) {
            FlowFolioPill(pluralStringResource(R.plurals.page_count, book.pageCount, book.pageCount), FlowFolio.hint)
        }
        FlowFolioPill(stringResource(book.formatLabel()), FlowFolio.item)
        FlowFolioPill(Formatter.formatShortFileSize(context, book.sizeBytes), FlowFolio.subItem)
        if (state.availability == Availability.MISSING) {
            FlowFolioPill(stringResource(R.string.badge_missing), FlowFolio.subItem)
        }
    }
}

/** Overview: what the book is about, how far along, and three stats (`.stats`): time read, ribbons, shelves. */
@Composable
internal fun OverviewPanel(state: DetailsUiState, book: Book) {
    Column(Modifier.fillMaxWidth()) {
        book.details.description?.let {
            Text(
                it,
                style = FlowFolioType.sub.copy(fontSize = 15.sp, lineHeight = 23.sp).merge(FlowFolio.hint),
                modifier = Modifier.padding(bottom = 18.dp)
            )
        }
        FlowFolioProgressBar(book.progress, Modifier.padding(bottom = 6.dp))
        Text(
            stringResource(R.string.percent_complete, (book.progress * PERCENT).roundToInt()),
            style = FlowFolioType.rowDetail.merge(FlowFolio.hint)
        )
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(readTime(state.readMillis), R.string.stat_read, FlowFolio.item, Modifier.weight(1f))
            Stat(state.ribbons.toString(), R.string.stat_ribbons, FlowFolio.title, Modifier.weight(1f))
            Stat(state.bookShelves.size.toString(), R.string.stat_shelves, FlowFolio.subItem, Modifier.weight(1f))
        }
        if (state.bookShelves.isNotEmpty()) {
            Text(
                state.bookShelves.joinToString(" · ") { it.name },
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                modifier = Modifier.padding(top = 14.dp)
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
private fun Stat(value: String, label: Int, role: TextStyle, modifier: Modifier) {
    FlowFolioCard(modifier, radius = 18.dp, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Column {
            Text(value, style = FlowFolioType.stat.merge(role))
            Text(stringResource(label).uppercase(), style = FlowFolioType.eyebrow.merge(FlowFolio.hint))
        }
    }
}

/** Details (`.info-grid`): what is known about the book and its file. Unknown fields say so. */
@Composable
internal fun InfoPanel(state: DetailsUiState, book: Book) {
    val context = LocalContext.current
    val dateFormat = DateFormat.getMediumDateFormat(context)
    val unknown = stringResource(R.string.info_unknown)
    val d = book.details
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Info(R.string.info_author, book.author ?: unknown, Modifier.weight(1f))
            Info(R.string.info_pages, book.pageCount.takeIf { it > 0 }?.toString() ?: unknown, Modifier.weight(1f))
        }
        Info(R.string.info_isbn, d.isbn ?: unknown)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Info(R.string.info_released, d.released ?: unknown, Modifier.weight(1f))
            Info(R.string.info_edition, d.edition ?: unknown, Modifier.weight(1f))
        }
        Info(R.string.info_publisher, d.publisher ?: unknown)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Info(R.string.info_language, d.language ?: unknown, Modifier.weight(1f))
            Info(R.string.info_type, d.type?.let { stringResource(it.label) } ?: unknown, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Info(R.string.info_added, dateFormat.format(book.addedAtMillis), Modifier.weight(1f))
            Info(R.string.info_size, Formatter.formatShortFileSize(context, book.sizeBytes), Modifier.weight(1f))
        }
        Info(
            R.string.info_last_opened,
            book.lastOpenedAtMillis?.let { dateFormat.format(it) } ?: stringResource(R.string.info_never)
        )
        Info(R.string.info_storage, stringResource(storageLabel(state, book)))
        Info(R.string.info_cover, stringResource(coverLabel(book)), sub = true)
    }
}

@Composable
private fun Info(label: Int, value: String, modifier: Modifier = Modifier.fillMaxWidth(), sub: Boolean = false) {
    FlowFolioCard(modifier, radius = 18.dp, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Column {
            Text(stringResource(label).uppercase(), style = FlowFolioType.eyebrow.merge(FlowFolio.hint))
            Text(
                value,
                style = FlowFolioType.rowLabel.merge(if (sub) FlowFolio.subItem else FlowFolio.item),
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
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
        Text(
            stringResource(R.string.page_short, label),
            style = FlowFolioType.sub.merge(FlowFolio.hint)
        )
    }
}

/** The chapter containing [lastPage]: the last entry starting at or before it; -1 when not started. */
internal fun currentChapter(entries: List<OutlineEntry>, lastPage: Int?): Int {
    if (lastPage == null) return -1
    return entries.indexOfLast { it.pageIndex <= lastPage }
}

private const val PERCENT = 100
private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L

/** From ten hours on, minutes are noise. */
private const val WHOLE_HOURS_FROM = 10L
private const val DOT_IDLE_ALPHA = 0.4f
