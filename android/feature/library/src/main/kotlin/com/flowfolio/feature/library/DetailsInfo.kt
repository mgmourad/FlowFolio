package com.flowfolio.feature.library

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.model.Book

/**
 * Details: what is known about the book and about its file, as two lists of label and value in one card each, rows
 * split by hairlines. Unknown fields say so, quietly.
 */
@Composable
internal fun InfoPanel(state: DetailsUiState, book: Book) {
    val context = LocalContext.current
    val dateFormat = DateFormat.getMediumDateFormat(context)
    val d = book.details
    val type = d.type?.let { stringResource(it.label) }
    val lastOpened = book.lastOpenedAtMillis?.let { dateFormat.format(it) } ?: stringResource(R.string.info_never)
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        InfoGroup(
            R.string.info_group_book,
            listOf(
                R.string.info_author to book.author,
                R.string.info_series to d.series,
                R.string.info_pages to book.pageCount.takeIf { it > 0 }?.toString(),
                R.string.info_print_pages to d.printPages?.toString(),
                R.string.info_publisher to d.publisher,
                R.string.info_released to d.released,
                R.string.info_edition to d.edition,
                R.string.info_isbn to d.isbn,
                R.string.info_language to d.language,
                R.string.info_type to type,
                R.string.info_subjects to d.subjects.takeIf { it.isNotEmpty() }?.joinToString(", "),
                R.string.info_rating to d.rating?.let { r ->
                    val count = d.ratingsCount
                    if (count != null) {
                        stringResource(R.string.info_rating_value, "%.1f".format(r), count)
                    } else {
                        stringResource(R.string.info_rating_alone, "%.1f".format(r))
                    }
                }
            )
        )
        InfoGroup(
            R.string.info_group_file,
            listOf(
                R.string.info_format to stringResource(book.formatLabel()),
                R.string.info_size to Formatter.formatShortFileSize(context, book.sizeBytes),
                R.string.info_added to dateFormat.format(book.addedAtMillis),
                R.string.info_last_opened to lastOpened,
                R.string.info_storage to stringResource(storageLabel(state, book)),
                R.string.info_folder to book.folder,
                R.string.info_cover to stringResource(coverLabel(book))
            )
        )
    }
}

@Composable
private fun InfoGroup(title: Int, rows: List<Pair<Int, String?>>) {
    Column {
        Text(
            stringResource(title).uppercase(),
            style = FlowFolioType.eyebrow.merge(FlowFolio.hint),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        FlowFolioCard(Modifier.fillMaxWidth(), radius = 20.dp, padding = PaddingValues(horizontal = 16.dp)) {
            Column {
                rows.forEachIndexed { i, (label, value) ->
                    if (i > 0) HorizontalDivider(color = FlowFolio.palette.edge)
                    InfoRow(label, value)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: Int, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp)) {
        Text(
            stringResource(label),
            style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
            modifier = Modifier.weight(LABEL_SHARE).alignByBaseline().padding(end = 12.dp)
        )
        Text(
            value ?: stringResource(R.string.info_unknown),
            style = if (value == null) {
                FlowFolioType.rowDetail.merge(FlowFolio.hint).copy(fontStyle = FontStyle.Italic)
            } else {
                FlowFolioType.rowLabel.merge(FlowFolio.item)
            },
            textAlign = TextAlign.End,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f - LABEL_SHARE).alignByBaseline()
        )
    }
}

private const val LABEL_SHARE = 0.38f
