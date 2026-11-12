package com.flowfolio.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
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

@Suppress("LongMethod") // one layout per size, read top to bottom
@Composable
private fun NarratorContent(shown: NarratorShown?, colors: WidgetColors) {
    val context = LocalContext.current
    val size = LocalSize.current
    val wide = size.width >= NarratorWidget.MEDIUM.width
    val coverHeight = size.height - 24.dp
    val open = if (shown == null) openAppIntent(context) else openBookIntent(context, shown.bookId)
    Box(
        GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(colors.background)
            .clickable(actionStartActivity(open)).padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            shown == null -> Empty(context.getString(R.string.widget_narrator_empty), colors)

            wide -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                shown.cover?.let {
                    Image(
                        ImageProvider(it),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = GlanceModifier.size(coverHeight * 2 / 3, coverHeight).cornerRadius(6.dp)
                    )
                    Spacer(GlanceModifier.width(12.dp))
                }
                Column(GlanceModifier.defaultWeight()) {
                    Text(
                        context.getString(R.string.widget_narrator_label),
                        style = TextStyle(color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    )
                    Text(
                        shown.title,
                        maxLines = 1,
                        style = TextStyle(color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    )
                    shown.chapter?.takeIf { it.isNotBlank() }?.let {
                        Text(it, maxLines = 1, style = TextStyle(color = colors.ink2, fontSize = 12.sp))
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    Controls(shown, colors)
                }
            }

            // Small: the cover (or the title, without one), with Play over its bottom corner.
            else -> Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (shown.cover != null) {
                        Image(
                            ImageProvider(shown.cover),
                            contentDescription = shown.title,
                            contentScale = ContentScale.Fit,
                            modifier = GlanceModifier.fillMaxSize().cornerRadius(6.dp)
                        )
                    } else {
                        Text(
                            shown.title,
                            maxLines = 3,
                            style = TextStyle(color = colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
                PlayButton(shown, colors)
            }
        }
    }
}

@Composable
private fun Controls(shown: NarratorShown, colors: WidgetColors) {
    val context = LocalContext.current
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val live = shown.playing != null
        if (live) {
            IconButton(ReaderR.drawable.ic_narrator_previous, R.string.widget_narrator_previous, colors) {
                command(Narration.Command.PREVIOUS)
            }
        }
        PlayButton(shown, colors)
        if (live) {
            IconButton(ReaderR.drawable.ic_narrator_next, R.string.widget_narrator_next, colors) {
                command(Narration.Command.NEXT)
            }
            Spacer(GlanceModifier.width(6.dp))
            Text(
                context.getString(R.string.widget_narrator_speed, speedText(shown.playing.rate)),
                style = TextStyle(color = colors.ink2, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.cornerRadius(12.dp).background(colors.panel)
                    .padding(horizontal = 8.dp, vertical = 4.dp).clickable(command(Narration.Command.SPEED))
            )
        }
    }
}

/** Play or pause the narration; with none running, open the book and start it. */
@Composable
private fun PlayButton(shown: NarratorShown, colors: WidgetColors) {
    val context = LocalContext.current
    val playing = shown.playing?.playing == true
    val action = if (shown.playing != null) {
        command(Narration.Command.PLAY_PAUSE)
    } else {
        actionStartActivity(openBookIntent(context, shown.bookId, narrate = true))
    }
    Box(
        GlanceModifier.size(40.dp).cornerRadius(20.dp).background(colors.accent).clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Image(
            ImageProvider(if (playing) ReaderR.drawable.ic_narrator_pause else ReaderR.drawable.ic_narrator_play),
            contentDescription = context.getString(
                if (playing) R.string.widget_narrator_pause else R.string.widget_narrator_play
            ),
            colorFilter = ColorFilter.tint(colors.accentInk),
            modifier = GlanceModifier.size(22.dp)
        )
    }
}

@Composable
private fun IconButton(icon: Int, label: Int, colors: WidgetColors, action: () -> Action) {
    Box(GlanceModifier.size(40.dp).clickable(action()), contentAlignment = Alignment.Center) {
        Image(
            ImageProvider(icon),
            contentDescription = LocalContext.current.getString(label),
            colorFilter = ColorFilter.tint(colors.ink),
            modifier = GlanceModifier.size(22.dp)
        )
    }
}

/** 1, 1.25, 1.5: the speed as the reader's panel writes it. */
internal fun speedText(rate: Float): String = "%.2f".format(java.util.Locale.ROOT, rate).trimEnd('0').trimEnd('.')
