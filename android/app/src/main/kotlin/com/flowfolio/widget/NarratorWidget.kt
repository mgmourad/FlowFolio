package com.flowfolio.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.flowfolio.R
import com.flowfolio.feature.reader.Narration
import com.flowfolio.feature.reader.R as ReaderR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/** What the Narrator widget shows: the book being read aloud, else the book to continue, ready to be read aloud. */
internal class NarratorShown(
    val bookId: String,
    val title: String,
    val chapter: String?,
    val cover: Bitmap?,
    val playing: Narration.Playing?
)

internal fun narratorShown(context: Context): Flow<NarratorShown?> =
    combine(Narration.playing, continueBook(context)) { now, next ->
        if (now != null) {
            NarratorShown(now.bookId, now.title, now.chapter, coverBitmap(now.coverPath), now)
        } else {
            next?.let { NarratorShown(it.book.id, it.book.title, it.book.author, it.cover, null) }
        }
    }

/**
 * Narrator (TM2): the book and chapter, play/pause, back, forward and speed, driving the narration that is running
 * (ADR-0053). With nothing playing, Play opens the book and starts narrating, since narration lives in the reader.
 */
class NarratorWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideLive(combine(narratorShown(context), context.widgetColors(), ::Pair)) { (shown, colors) ->
            NarratorContent(shown, colors)
        }
    }

    companion object {
        /** From this width a widget shows its medium layout; below it, its small one. */
        internal val MEDIUM = DpSize(250.dp, 110.dp)
    }
}

class NarratorWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NarratorWidget()
}

/** A player button pressed on the widget, passed to the narrating reader. */
class NarratorWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val command = parameters[COMMAND]?.let { name -> Narration.Command.entries.firstOrNull { it.name == name } }
        if (command != null) withContext(Dispatchers.Main) { Narration.send(command) }
    }

    companion object {
        internal val COMMAND = ActionParameters.Key<String>("command")
    }
}

private fun command(command: Narration.Command): Action =
    actionRunCallback<NarratorWidgetAction>(actionParametersOf(NarratorWidgetAction.COMMAND to command.name))

@Composable
private fun NarratorContent(shown: NarratorShown?, colors: WidgetColors) {
    val context = LocalContext.current
    val size = LocalSize.current
    val open = if (shown == null) openAppIntent(context) else openBookIntent(context, shown.bookId)
    WidgetCard(colors, actionStartActivity(open)) {
        if (shown == null) {
            Empty(context.getString(R.string.widget_narrator_empty), colors)
            return@WidgetCard
        }
        val art = WidgetArt(context)
        val inner = DpSize(size.width - CARD_PADDING * 2, size.height - CARD_PADDING * 2)
        if (size.width >= NarratorWidget.MEDIUM.width) {
            NarratorWide(shown, colors, art, inner)
        } else {
            // Small: the book, with a large Play over its corner.
            val cover = minOf(coverWidthFor(inner.height), inner.width / 1.28f)
            Box(contentAlignment = Alignment.BottomEnd) {
                ArtImage(art.book(shown.cover, shown.title, cover), null, shown.title)
                PlayButton(shown, colors, art, SMALL_PLAY)
            }
        }
    }
}

/** The book, "Narrator", its title and chapter, and a music player's row: back, play/pause, forward and speed. */
@Composable
private fun NarratorWide(shown: NarratorShown, colors: WidgetColors, art: WidgetArt, inner: DpSize) {
    val context = LocalContext.current
    val cover = coverWidthFor(inner.height)
    val textWidth = inner.width - bookFootprint(cover) - GAP
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        ArtImage(art.book(shown.cover, shown.title, cover), null, shown.title)
        Spacer(GlanceModifier.width(GAP))
        Column(GlanceModifier.width(textWidth)) {
            ArtImage(
                art.text(
                    context.getString(R.string.widget_narrator_label).uppercase(),
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
                    shown.title,
                    WidgetArt.Face.DISPLAY,
                    21f,
                    textWidth,
                    maxLines = 2,
                    weight = WidgetArt.SEMIBOLD
                ),
                colors.ink,
                shown.title
            )
            shown.chapter?.takeIf { it.isNotBlank() }?.let {
                Spacer(GlanceModifier.height(2.dp))
                ArtImage(art.text(it, WidgetArt.Face.UI, 14f, textWidth), colors.ink2, it)
            }
            Spacer(GlanceModifier.height(10.dp))
            Controls(shown, colors, art)
        }
    }
}

@Composable
private fun Controls(shown: NarratorShown, colors: WidgetColors, art: WidgetArt) {
    val context = LocalContext.current
    val playing = shown.playing
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (playing != null) {
            Skip(art, WidgetArt.Control.PREVIOUS, R.string.widget_narrator_previous, colors, Narration.Command.PREVIOUS)
            Spacer(GlanceModifier.width(6.dp))
        }
        PlayButton(shown, colors, art, WIDE_PLAY)
        if (playing != null) {
            Spacer(GlanceModifier.width(6.dp))
            Skip(art, WidgetArt.Control.NEXT, R.string.widget_narrator_next, colors, Narration.Command.NEXT)
            Spacer(GlanceModifier.width(10.dp))
            val speed = context.getString(R.string.widget_narrator_speed, speedText(playing.rate))
            Box(
                GlanceModifier.cornerRadius(14.dp).background(colors.panel).padding(horizontal = 10.dp, vertical = 6.dp)
                    .clickable(command(Narration.Command.SPEED)),
                contentAlignment = Alignment.Center
            ) {
                ArtImage(art.text(speed, WidgetArt.Face.UI, 14f, 60.dp, weight = WidgetArt.BOLD), colors.ink, speed)
            }
        }
    }
}

/** Play or pause in the accent circle; with nothing narrating, Play opens the book and starts it. */
@Composable
private fun PlayButton(shown: NarratorShown, colors: WidgetColors, art: WidgetArt, size: Dp) {
    val context = LocalContext.current
    val playing = shown.playing?.playing == true
    val action = if (shown.playing != null) {
        command(Narration.Command.PLAY_PAUSE)
    } else {
        actionStartActivity(openBookIntent(context, shown.bookId, narrate = true))
    }
    Box(
        GlanceModifier.size(size).cornerRadius(size / 2).background(colors.accent).clickable(action),
        contentAlignment = Alignment.Center
    ) {
        ArtImage(
            art.control(if (playing) WidgetArt.Control.PAUSE else WidgetArt.Control.PLAY, size * ICON_SHARE),
            colors.accentInk,
            context.getString(if (playing) R.string.widget_narrator_pause else R.string.widget_narrator_play)
        )
    }
}

@Composable
private fun Skip(art: WidgetArt, kind: WidgetArt.Control, label: Int, colors: WidgetColors, cmd: Narration.Command) {
    Box(GlanceModifier.size(SKIP).clickable(command(cmd)), contentAlignment = Alignment.Center) {
        ArtImage(art.control(kind, SKIP * ICON_SHARE), colors.ink, LocalContext.current.getString(label))
    }
}

/** 1, 1.25, 1.5: the speed as the reader's panel writes it. */
internal fun speedText(rate: Float): String = "%.2f".format(java.util.Locale.ROOT, rate).trimEnd('0').trimEnd('.')

private val GAP = 14.dp
private val WIDE_PLAY = 58.dp
private val SMALL_PLAY = 54.dp
private val SKIP = 46.dp
private const val ICON_SHARE = 0.56f
