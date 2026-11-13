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
import kotlin.math.roundToInt
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
    val open = if (item == null) openAppIntent(context) else openBookIntent(context, item.book.id)
    WidgetCard(colors, actionStartActivity(open)) {
        if (item == null) {
            Empty(context.getString(R.string.widget_continue_empty), colors)
            return@WidgetCard
        }
        val art = WidgetArt(context)
        val inner = DpSize(size.width - CARD_PADDING * 2, size.height - CARD_PADDING * 2)
        if (size.width >= ContinueReadingWidget.MEDIUM.width) {
            ContinueWide(item, colors, art, inner)
        } else {
            // Small: the book itself, and how far through it you are under it.
            val cover = minOf(coverWidthFor(inner.height - GAP * 2), inner.width / 1.28f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ArtImage(art.book(item.cover, item.book.title, cover), null, item.book.title)
                Spacer(GlanceModifier.height(GAP))
                ProgressLine(item.book.progress, cover, colors)
            }
        }
    }
}

/**
 * As the library's Continue card, without its buttons: the book, "Continue reading", the title in Fraunces, the
 * author, "Page X of Y · N%" and the progress line.
 */
@Composable
private fun ContinueWide(item: ContinueBook, colors: WidgetColors, art: WidgetArt, inner: DpSize) {
    val context = LocalContext.current
    val book = item.book
    val cover = coverWidthFor(inner.height)
    val textWidth = inner.width - bookFootprint(cover) - GAP
    val roomy = inner.height >= ROOMY
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        ArtImage(art.book(item.cover, book.title, cover), null, book.title)
        Spacer(GlanceModifier.width(GAP))
        Column(GlanceModifier.width(textWidth)) {
            ArtImage(
                art.text(
                    context.getString(R.string.widget_continue_label).uppercase(),
                    WidgetArt.Face.UI,
                    11.5f,
                    textWidth,
                    weight = WidgetArt.BOLD,
                    tracking = 0.12f
                ),
                colors.accent
            )
            Spacer(GlanceModifier.height(4.dp))
            ArtImage(
                art.text(
                    book.title,
                    WidgetArt.Face.DISPLAY,
                    if (roomy) 24f else 20f,
                    textWidth,
                    maxLines = 2,
                    weight = WidgetArt.SEMIBOLD
                ),
                colors.ink,
                book.title
            )
            book.author?.takeIf { it.isNotBlank() }?.let {
                Spacer(GlanceModifier.height(2.dp))
                ArtImage(art.text(it, WidgetArt.Face.UI, if (roomy) 15f else 13.5f, textWidth), colors.ink2, it)
            }
            Spacer(GlanceModifier.height(if (roomy) 12.dp else 8.dp))
            if (book.pageCount > 0) {
                val page = "%,d".format((book.lastPage ?: 0) + 1)
                val where = context.getString(
                    R.string.widget_continue_where,
                    page,
                    "%,d".format(book.pageCount),
                    (book.progress * PERCENT).roundToInt()
                )
                ArtImage(
                    art.text(where, WidgetArt.Face.UI, 13f, textWidth, weight = WidgetArt.MEDIUM),
                    colors.ink2,
                    where
                )
                Spacer(GlanceModifier.height(6.dp))
            }
            ProgressLine(book.progress, textWidth, colors)
        }
    }
}

@Composable
internal fun Empty(text: String, colors: WidgetColors) {
    Text(text, style = TextStyle(color = colors.ink2, fontSize = 14.sp))
}

private val GAP = 14.dp
private val ROOMY = 150.dp
private const val PERCENT = 100
