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
 * The centred Reading Goal window (owner, M3T3): Set Reading Goal (with iPhone-style hours/minutes wheels, period
 * selector, Reset, and Remind me) and, when opened from the library, the In-progress books section with gradient
 * progress bars.
 */
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
    val startMinutes = initial?.targetMinutes ?: DEFAULT_GOAL_MINUTES
    var hours by rememberSaveable(scope) { mutableIntStateOf((startMinutes / 60).coerceIn(0, MAX_HOURS)) }
    var minutes by rememberSaveable(scope) { mutableIntStateOf(startMinutes % 60) }
    var period by rememberSaveable(scope) { mutableStateOf(initial?.period ?: GoalPeriod.DAY) }
    var remind by rememberSaveable(scope) { mutableStateOf(initial?.remindEnabled ?: false) }
    var remHour by rememberSaveable(scope) { mutableIntStateOf(initial?.remindHour ?: DEFAULT_REM_HOUR) }
    var remMinute by rememberSaveable(scope) { mutableIntStateOf(initial?.remindMinute ?: 0) }
    var remDays by rememberSaveable(scope) { mutableIntStateOf(initial?.remindDaysMask ?: ReadingGoal.ALL_DAYS_MASK) }
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = DIALOG_MAX_WIDTH)
                .fillMaxWidth()
                .heightIn(max = DIALOG_MAX_HEIGHT)
                .clip(shape)
                .background(p.background, shape)
                .border(1.dp, p.edge, shape)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
                .testTag(LibraryTestTags.GOAL_DIALOG),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GoalHeader(onDismiss)
            WheelPickerRow(hours, minutes, onHours = { hours = it }, onMinutes = { minutes = it })
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
                    val total = (hours * 60 + minutes).coerceAtLeast(MIN_GOAL_MINUTES)
                    onSave(ReadingGoal(scope, total, period, remind, remHour, remMinute, remDays))
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

@Composable
private fun GoalHeader(onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FlowFolioGlyph(Glyph.ReadingGoal, contentDescription = null, color = FlowFolio.palette.title, size = 24.dp)
        Text(
            stringResource(R.string.goal_set_title),
            style = FlowFolioType.sectionTitle.merge(FlowFolio.title),
            modifier = Modifier.weight(1f).padding(start = 10.dp)
        )
        FlowFolioIconButton(onClick = onDismiss, size = 36.dp, tooltip = stringResource(R.string.action_cancel)) {
            FlowFolioGlyph(Glyph.Back, stringResource(R.string.action_cancel), size = 18.dp, glow = false)
        }
    }
}

@Composable
private fun WheelPickerRow(hours: Int, minutes: Int, onHours: (Int) -> Unit, onMinutes: (Int) -> Unit) {
    val p = FlowFolio.palette
    FlowFolioCard(
        Modifier.fillMaxWidth(),
        radius = 20.dp,
        padding = PaddingValues(vertical = 10.dp, horizontal = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WheelColumn(
                count = MAX_HOURS + 1,
                selected = hours,
                label = { stringResource(R.string.goal_wheel_hours, it) },
                onSelect = onHours,
                modifier = Modifier.width(104.dp)
            )
            Text(
                ":",
                style = FlowFolioType.heroTitle.merge(FlowFolio.title),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            WheelColumn(
                count = 60,
                selected = minutes,
                label = { stringResource(R.string.goal_wheel_minutes, it) },
                onSelect = onMinutes,
                modifier = Modifier.width(104.dp)
            )
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT)
                .border(1.dp, p.title.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
        )
    }
}

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
private const val MAX_HOURS = 12
private const val DEFAULT_REM_HOUR = 20
internal val WHEEL_ITEM_HEIGHT = 36.dp
private val DIALOG_MAX_WIDTH = 440.dp
private val DIALOG_MAX_HEIGHT = 640.dp
