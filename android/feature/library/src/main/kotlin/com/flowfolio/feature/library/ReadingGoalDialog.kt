package com.flowfolio.feature.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioChip
import com.flowfolio.core.designsystem.FlowFolioGhostButton
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioPrimaryButton
import com.flowfolio.core.designsystem.FlowFolioRing
import com.flowfolio.core.designsystem.FlowFolioSheet
import com.flowfolio.core.designsystem.FlowFolioToggle
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.softShadow
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.GoalPeriod
import com.flowfolio.core.model.ReadingGoal
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The Reading Goal sheet (ADR-0052, the mock's `goalSheet`; owner, M3T3): a ring showing the target, preset
 * chips with five minutes less or more either side, Day/Week/Month (library goal), the reminder (time of day and
 * days), Reset and Save goal; opened from the library, it ends with the books in progress.
 */
@Suppress("LongMethod") // one sheet, section by section
@Composable
internal fun ReadingGoalDialog(
    scope: String,
    initial: ReadingGoal?,
    inProgress: List<Book>,
    onSave: (ReadingGoal) -> Unit,
    onReset: () -> Unit,
    onOpenBook: (Book) -> Unit,
    onDismiss: () -> Unit
) {
    var target by rememberSaveable(scope) { mutableIntStateOf(initial?.targetMinutes ?: DEFAULT_GOAL_MINUTES) }
    var period by rememberSaveable(scope) { mutableStateOf(initial?.period ?: GoalPeriod.DAY) }
    var remind by rememberSaveable(scope) { mutableStateOf(initial?.remindEnabled ?: false) }
    var remHour by rememberSaveable(scope) { mutableIntStateOf(initial?.remindHour ?: DEFAULT_REM_HOUR) }
    var remMinute by rememberSaveable(scope) { mutableIntStateOf(initial?.remindMinute ?: 0) }
    var remDays by rememberSaveable(scope) { mutableIntStateOf(initial?.remindDaysMask ?: ReadingGoal.ALL_DAYS_MASK) }
    val p = FlowFolio.palette
    FlowFolioSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.goal_set_title),
        trailing = {
            FlowFolioIconButton(onClick = onDismiss, size = 36.dp, tooltip = stringResource(R.string.action_cancel)) {
                FlowFolioGlyph(Glyph.Close, stringResource(R.string.action_cancel), size = 18.dp, glow = false)
            }
        }
    ) {
        Column(
            Modifier
                .heightIn(max = SHEET_MAX_HEIGHT)
                .verticalScroll(rememberScrollState())
                .testTag(LibraryTestTags.GOAL_DIALOG),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FlowFolioRing(
                (target / RING_FULL_MINUTES.toFloat()).coerceAtMost(1f),
                Modifier.align(Alignment.CenterHorizontally),
                diameter = RING_SIZE
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatGoalMinutes(target),
                        style = FlowFolioType.screenTitle.copy(color = p.ink, fontSize = 30.sp)
                    )
                    Text(
                        stringResource(period.perLabel),
                        style = FlowFolioType.rowDetail.copy(color = p.ink2)
                    )
                }
            }
            GoalPresets(target) { target = it.coerceIn(MIN_GOAL_MINUTES, MAX_GOAL_MINUTES) }
            if (scope == ReadingGoal.LIBRARY_SCOPE) PeriodSelector(period) { period = it }
            ReminderSection(remind, remHour, remMinute, remDays, { remind = it }, { h, m ->
                remHour = h
                remMinute = m
            }, { remDays = it })
            GoalButtonsRow(
                onReset = {
                    onReset()
                    onDismiss()
                },
                onSave = {
                    onSave(ReadingGoal(scope, target, period, remind, remHour, remMinute, remDays))
                    onDismiss()
                }
            )
            if (scope == ReadingGoal.LIBRARY_SCOPE && inProgress.isNotEmpty()) {
                InProgressSection(inProgress) { book ->
                    onDismiss()
                    onOpenBook(book)
                }
            }
        }
    }
}

/** The presets (15 m to 1 h 30 m) between − and +, five minutes a step. */
@Composable
private fun GoalPresets(target: Int, onTarget: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val less = stringResource(R.string.goal_less)
        val more = stringResource(R.string.goal_more)
        FlowFolioIconButton(onClick = { onTarget(target - STEP_MINUTES) }, size = 34.dp, tooltip = less) {
            Text("−", style = FlowFolioType.button.copy(color = FlowFolio.palette.ink))
        }
        Row(
            Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PRESETS.forEach { m ->
                FlowFolioChip(formatGoalMinutes(m), selected = m == target, onClick = { onTarget(m) })
            }
        }
        FlowFolioIconButton(onClick = { onTarget(target + STEP_MINUTES) }, size = 34.dp, tooltip = more) {
            Text("+", style = FlowFolioType.button.copy(color = FlowFolio.palette.ink))
        }
    }
}

/** "45m", "1h", "1h 30m". */
internal fun formatGoalMinutes(minutes: Int): String {
    val h = minutes / MINUTES_PER_HOUR
    val m = minutes % MINUTES_PER_HOUR
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

private val GoalPeriod.perLabel: Int
    get() = when (this) {
        GoalPeriod.DAY -> R.string.goal_period_day
        GoalPeriod.WEEK -> R.string.goal_period_week_chip
        GoalPeriod.MONTH -> R.string.goal_period_month_chip
    }

private val PRESETS = listOf(15, 30, 45, 60, 90)
private const val STEP_MINUTES = 5
private const val MINUTES_PER_HOUR = 60
private const val RING_FULL_MINUTES = 120
private const val MAX_GOAL_MINUTES = 720
private val RING_SIZE = 168.dp
private val SHEET_MAX_HEIGHT = 680.dp

@Composable
internal fun WheelColumn(
    count: Int,
    selected: Int,
    label: @Composable (Int) -> String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, count - 1))
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Center)
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { idx -> onSelect(idx.coerceIn(0, count - 1)) }
    }
    LazyColumn(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(vertical = WHEEL_ITEM_HEIGHT),
        modifier = modifier.height(WHEEL_ITEM_HEIGHT * 3),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(count) { index ->
            val distance = abs(index - selected)
            val alpha = when (distance) {
                0 -> 1f
                1 -> 0.48f
                else -> 0.22f
            }
            Box(
                Modifier
                    .height(WHEEL_ITEM_HEIGHT)
                    .fillMaxWidth()
                    .clickable { onSelect(index) }
                    .alpha(alpha),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label(index),
                    style = if (distance == 0) {
                        FlowFolioType.rowLabel.merge(FlowFolio.title)
                    } else {
                        FlowFolioType.sub.merge(FlowFolio.hint)
                    },
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PeriodSelector(selected: GoalPeriod, onSelect: (GoalPeriod) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GoalPeriod.entries.forEach { period ->
            val label = when (period) {
                GoalPeriod.DAY -> R.string.goal_period_day
                GoalPeriod.WEEK -> R.string.goal_period_week_chip
                GoalPeriod.MONTH -> R.string.goal_period_month_chip
            }
            FlowFolioChip(
                text = stringResource(label),
                selected = period == selected,
                onClick = { onSelect(period) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GoalButtonsRow(onReset: () -> Unit, onSave: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlowFolioGhostButton(
            text = stringResource(R.string.goal_reset),
            onClick = onReset,
            modifier = Modifier.testTag(LibraryTestTags.GOAL_RESET)
        )
        FlowFolioPrimaryButton(
            text = stringResource(R.string.action_save),
            onClick = onSave,
            modifier = Modifier.testTag(LibraryTestTags.GOAL_SAVE)
        )
    }
}

@Composable
private fun InProgressSection(books: List<Book>, onOpenBook: (Book) -> Unit) {
    HorizontalDivider(color = FlowFolio.palette.edge)
    Text(
        stringResource(R.string.filter_in_progress),
        style = FlowFolioType.sectionTitle.merge(FlowFolio.title)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        books.forEach { book ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpenBook(book) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        book.title,
                        style = FlowFolioType.rowLabel.merge(FlowFolio.item),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Text(
                        stringResource(R.string.percent_complete, (book.progress * 100).roundToInt()),
                        style = FlowFolioType.rowDetail.merge(FlowFolio.hint)
                    )
                }
                GradientProgressBar(book.progress)
            }
        }
    }
}

private const val DEFAULT_GOAL_MINUTES = 30
private const val MIN_GOAL_MINUTES = 5
private const val DEFAULT_REM_HOUR = 20
internal val WHEEL_ITEM_HEIGHT = 36.dp
