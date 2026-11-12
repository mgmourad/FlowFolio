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
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.flowfolio.R
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
    val wide = size.width >= ReadingGoalWidget.MEDIUM.width
    // The ring fills the height (medium) or the square left over by the streak line (small), less the padding.
    val ring = if (wide) size.height - 32.dp else minOf(size.width - 32.dp, size.height - 52.dp)
    Box(
        GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(colors.background)
            .clickable(actionStartActivity(openAppIntent(context))).padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ring(today, colors, ring)
                Spacer(GlanceModifier.width(14.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(
                        context.getString(R.string.widget_goal_label),
                        style = TextStyle(color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    )
                    Text(
                        context.getString(periodText(today.goal.period), today.readMinutes, today.goal.targetMinutes),
                        style = TextStyle(color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    )
                    Streak(today.streak, colors)
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Ring(today, colors, ring)
                Streak(today.streak, colors)
            }
        }
    }
}

/** The track, the progress over it, and the minutes read in the middle. */
@Composable
private fun Ring(today: GoalToday, colors: WidgetColors, ring: Dp) {
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (today.progress >= 1f) "✓" else "${today.readMinutes}",
                style = TextStyle(
                    color = colors.ink,
                    fontSize = (ring.value / 4).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            )
            Text(
                LocalContext.current.getString(R.string.widget_goal_of, today.goal.targetMinutes),
                style = TextStyle(color = colors.ink3, fontSize = (ring.value / 11).sp, textAlign = TextAlign.Center)
            )
        }
    }
}

@Composable
private fun Streak(days: Int, colors: WidgetColors) {
    val resources = LocalContext.current.resources
    val text = if (days > 0) {
        resources.getQuantityString(R.plurals.widget_goal_streak, days, days)
    } else {
        resources.getString(R.string.widget_goal_no_streak)
    }
    Text(text, maxLines = 1, style = TextStyle(color = colors.ink2, fontSize = 12.sp))
}

private fun periodText(period: GoalPeriod): Int = when (period) {
    GoalPeriod.DAY -> R.string.widget_goal_today
    GoalPeriod.WEEK -> R.string.widget_goal_week
    GoalPeriod.MONTH -> R.string.widget_goal_month
}

private const val MINUTE_MS = 60_000L
