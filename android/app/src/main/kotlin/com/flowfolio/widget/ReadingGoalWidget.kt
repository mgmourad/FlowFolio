package com.flowfolio.widget

import android.content.Context
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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.flowfolio.R
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.model.GoalPeriod
import com.flowfolio.core.model.ReadingGoal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Where the reader stands against the library goal, and the streak. */
internal class GoalToday(val goal: ReadingGoal, val readMillis: Long, val streak: Int) {
    val readMinutes: Int get() = (readMillis / MINUTE_MS).toInt()
    val progress: Float get() = goal.progress(readMillis)
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun goalToday(context: Context): Flow<GoalToday> {
    val goals = context.widgetGraph().goals()
    // No goal set yet: the default one, as the library's goal card shows it.
    return goals.observeLibraryGoal().map { it ?: ReadingGoal() }.flatMapLatest { goal ->
        goals.observePeriodReadMillis(goal.period).map { read -> GoalToday(goal, read, goals.streakDays()) }
    }
}

/** Reading goal (TM2): today's (or this week's, this month's) progress ring and the streak. */
class ReadingGoalWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideLive(combine(goalToday(context), context.widgetColors(), ::Pair)) { (today, colors) ->
            GoalContent(today, colors)
        }
    }

    companion object {
        /** From this width a widget shows its medium layout; below it, its small one. */
        internal val MEDIUM = DpSize(250.dp, 110.dp)
    }
}

class ReadingGoalReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReadingGoalWidget()
}

@Composable
private fun GoalContent(today: GoalToday, colors: WidgetColors) {
    val context = LocalContext.current
    val size = LocalSize.current
    val art = WidgetArt(context)
    val inner = DpSize(size.width - CARD_PADDING * 2, size.height - CARD_PADDING * 2)
    WidgetCard(colors, actionStartActivity(openAppIntent(context))) {
        if (size.width >= ReadingGoalWidget.MEDIUM.width) {
            val ring = inner.height
            val textWidth = inner.width - ring - GAP
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Ring(today, colors, art, ring)
                Spacer(GlanceModifier.width(GAP))
                Column(GlanceModifier.width(textWidth)) {
                    ArtImage(
                        art.text(
                            context.getString(R.string.widget_goal_label).uppercase(),
                            WidgetArt.Face.UI,
                            11.5f,
                            textWidth,
                            weight = WidgetArt.BOLD,
                            tracking = 0.12f
                        ),
                        colors.accent
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    val line = context.getString(
                        periodText(today.goal.period),
                        today.readMinutes,
                        today.goal.targetMinutes
                    )
                    ArtImage(
                        art.text(
                            line,
                            WidgetArt.Face.DISPLAY,
                            21f,
                            textWidth,
                            maxLines = 2,
                            weight = WidgetArt.SEMIBOLD
                        ),
                        colors.ink,
                        line
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    Streak(today.streak, colors, art, textWidth)
                }
            }
        } else {
            val ring = minOf(inner.width, inner.height - STREAK_ROOM)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Ring(today, colors, art, ring)
                Spacer(GlanceModifier.height(6.dp))
                Streak(today.streak, colors, art, inner.width)
            }
        }
    }
}

/** The track, the progress over it, faint reading glasses behind, and the minutes read in Fraunces in the middle. */
@Composable
private fun Ring(today: GoalToday, colors: WidgetColors, art: WidgetArt, ring: Dp) {
    val context = LocalContext.current
    Box(GlanceModifier.size(ring), contentAlignment = Alignment.Center) {
        Image(
            ImageProvider(ringBitmap(1f)),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.track),
            modifier = GlanceModifier.fillMaxSize()
        )
        Image(
            ImageProvider(ringBitmap(today.progress)),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.accent),
            modifier = GlanceModifier.fillMaxSize()
        )
        ArtImage(art.glyph(Glyph.Glasses, ring * GLASSES_SHARE, strokeBoost = 0.8f), colors.faint)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val number = if (today.progress >= 1f) "✓" else "${today.readMinutes}"
            ArtImage(
                art.text(
                    number,
                    WidgetArt.Face.DISPLAY,
                    ring.value * NUMBER_SHARE,
                    ring * 0.7f,
                    weight = WidgetArt.SEMIBOLD
                ),
                colors.ink,
                number
            )
            val of = context.getString(R.string.widget_goal_of, today.goal.targetMinutes)
            ArtImage(art.text(of, WidgetArt.Face.UI, ring.value * 0.085f, ring * 0.7f), colors.ink3, of)
        }
    }
}

@Composable
private fun Streak(days: Int, colors: WidgetColors, art: WidgetArt, width: Dp) {
    val resources = LocalContext.current.resources
    val text = if (days > 0) {
        resources.getQuantityString(R.plurals.widget_goal_streak, days, days)
    } else {
        resources.getString(R.string.widget_goal_no_streak)
    }
    ArtImage(art.text(text, WidgetArt.Face.UI, 13.5f, width, weight = WidgetArt.MEDIUM), colors.ink2, text)
}

private fun periodText(period: GoalPeriod): Int = when (period) {
    GoalPeriod.DAY -> R.string.widget_goal_today
    GoalPeriod.WEEK -> R.string.widget_goal_week
    GoalPeriod.MONTH -> R.string.widget_goal_month
}

private const val MINUTE_MS = 60_000L
private val GAP = 16.dp
private val STREAK_ROOM = 26.dp
private const val GLASSES_SHARE = 0.72f
private const val NUMBER_SHARE = 0.32f
