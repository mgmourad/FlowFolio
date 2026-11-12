package com.flowfolio.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.flowfolio.R
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.LibrarySort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The book to go back to, as the library's Continue card picks it: the last opened and not finished. */
internal class ContinueBook(val book: Book, val cover: Bitmap?)

internal fun continueBook(context: Context): Flow<ContinueBook?> =
    context.widgetGraph().library().observeBooks(LibrarySort.RECENT)
        .map { books ->
            books.filter { it.lastOpenedAtMillis != null && !it.finished }.maxByOrNull { it.lastOpenedAtMillis ?: 0L }
        }
        .distinctUntilChanged()
        .map { book -> book?.let { ContinueBook(it, coverBitmap(it.coverPath)) } }

/** Continue reading (TM2): the cover, title, progress, and a tap that opens the book where it was left. */
class ContinueReadingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideLive(combine(continueBook(context), context.widgetColors(), ::Pair)) { (book, colors) ->
            ContinueContent(book, colors)
        }
    }

    companion object {
        /** From this width a widget shows its medium layout; below it, its small one. */
        internal val MEDIUM = DpSize(250.dp, 110.dp)
    }
}

class ContinueReadingReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContinueReadingWidget()
}

@Composable
private fun ContinueContent(item: ContinueBook?, colors: WidgetColors) {
    val context = LocalContext.current
    val size = LocalSize.current
    val wide = size.width >= ContinueReadingWidget.MEDIUM.width
    // A book's shape (2:3) at the widget's full height, less the padding.
    val coverHeight = size.height - 24.dp
    val open = if (item == null) openAppIntent(context) else openBookIntent(context, item.book.id)
    Box(
        GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(colors.background)
            .clickable(actionStartActivity(open)).padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            item == null -> Empty(context.getString(R.string.widget_continue_empty), colors)

            wide -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Cover(item.cover, GlanceModifier.width(coverHeight * 2 / 3).fillMaxHeight())
                Spacer(GlanceModifier.width(12.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(
                        context.getString(R.string.widget_continue_label),
                        style = TextStyle(color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    )
                    Text(
                        item.book.title,
                        maxLines = 2,
                        style = TextStyle(color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    )
                    item.book.author?.takeIf { it.isNotBlank() }?.let {
                        Text(it, maxLines = 1, style = TextStyle(color = colors.ink2, fontSize = 12.sp))
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    Progress(item.book, colors)
                }
            }

            else -> Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Cover(item.cover, GlanceModifier.defaultWeight().fillMaxWidth())
                Spacer(GlanceModifier.height(6.dp))
                Progress(item.book, colors, words = false)
            }
        }
    }
}

@Composable
private fun Cover(cover: Bitmap?, modifier: GlanceModifier) {
    if (cover == null) return
    Image(
        ImageProvider(cover),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.cornerRadius(6.dp)
    )
}

@Composable
private fun Progress(book: Book, colors: WidgetColors, words: Boolean = true) {
    LinearProgressIndicator(
        progress = book.progress,
        modifier = GlanceModifier.fillMaxWidth().height(4.dp),
        color = colors.accent,
        backgroundColor = colors.track
    )
    if (words && book.pageCount > 0) {
        val page = (book.lastPage ?: 0) + 1
        Text(
            LocalContext.current.getString(
                R.string.widget_continue_page,
                "%,d".format(page),
                "%,d".format(book.pageCount)
            ),
            style = TextStyle(color = colors.ink3, fontSize = 11.sp),
            modifier = GlanceModifier.padding(top = 4.dp)
        )
    }
}

@Composable
internal fun Empty(text: String, colors: WidgetColors) {
    Text(text, style = TextStyle(color = colors.ink2, fontSize = 13.sp))
}
