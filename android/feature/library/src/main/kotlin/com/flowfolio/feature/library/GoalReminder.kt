package com.flowfolio.feature.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioChip
import com.flowfolio.core.designsystem.FlowFolioGhostButton
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioPrimaryButton
import com.flowfolio.core.designsystem.FlowFolioToggle
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.softShadow
import java.util.Locale

/**
 * "Remind me" inside the Reading Goal window: the switch, the time of day, and the days it runs on. Split out of
 * [ReadingGoalDialog] in M4, when the time of day grew a window of its own.
 */
@Composable
internal fun ReminderSection(
    enabled: Boolean,
    hour: Int,
    minute: Int,
    daysMask: Int,
    onEnabled: (Boolean) -> Unit,
    onTime: (Int, Int) -> Unit,
    onDays: (Int) -> Unit
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        onEnabled(true)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FlowFolioGlyph(Glyph.Clock, contentDescription = null, size = 18.dp, glow = false)
            Text(
                stringResource(R.string.goal_remind_me),
                style = FlowFolioType.rowLabel.merge(FlowFolio.item),
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            FlowFolioToggle(
                checked = enabled,
                onCheckedChange = { turnOn ->
                    if (
                        turnOn &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onEnabled(turnOn)
                    }
                },
                modifier = Modifier.testTag(LibraryTestTags.GOAL_REMIND_SWITCH)
            )
        }
        if (enabled) {
            ReminderDaysRow(hour, minute, daysMask, onTime, onDays)
        }
    }
}

@Composable
private fun ReminderDaysRow(hour: Int, minute: Int, daysMask: Int, onTime: (Int, Int) -> Unit, onDays: (Int) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(stringResource(R.string.goal_reminder_time), style = FlowFolioType.sub.merge(FlowFolio.hint))
        // Tapping the time used to step it on a quarter of an hour, which gave no sign that it could be set at
        // all; it now opens a window with the same wheels as the goal above (owner, M4).
        FlowFolioChip(
            text = String.format(Locale.US, "%02d:%02d", hour, minute),
            selected = true,
            onClick = { picking = true },
            modifier = Modifier.testTag(LibraryTestTags.GOAL_TIME)
        )
    }
    if (picking) {
        TimeOfDayDialog(hour, minute, onDismiss = { picking = false }) { h, m ->
            onTime(h, m)
            picking = false
        }
    }
    DayToggles(daysMask, onDays)
}

/** The seven days: solid and glowing when the reminder runs on them, hollow and dim when it does not (owner, M4). */
@Composable
private fun DayToggles(daysMask: Int, onDays: (Int) -> Unit) {
    val p = FlowFolio.palette
    val names = listOf(
        R.string.goal_day_mon,
        R.string.goal_day_tue,
        R.string.goal_day_wed,
        R.string.goal_day_thu,
        R.string.goal_day_fri,
        R.string.goal_day_sat,
        R.string.goal_day_sun
    )
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEachIndexed { idx, letter ->
            val bit = 1 shl idx
            val active = (daysMask and bit) != 0
            val name = stringResource(names[idx])
            val described = stringResource(if (active) R.string.goal_day_on else R.string.goal_day_off, name)
            Box(
                Modifier
                    .size(DAY_SIZE)
                    .then(
                        if (active) {
                            Modifier.softShadow(CircleShape, p.ink.copy(alpha = DAY_GLOW), blur = 12.dp)
                        } else {
                            Modifier
                        }
                    )
                    .clip(CircleShape)
                    .background(if (active) p.ink else Color.Transparent, CircleShape)
                    .border(if (active) 0.dp else 1.dp, if (active) Color.Transparent else p.edge, CircleShape)
                    .clickable {
                        val toggled = daysMask xor bit
                        // A reminder with no day left would never fire, so the last one cannot be turned off.
                        if (toggled != 0) onDays(toggled)
                    }
                    .semantics { contentDescription = described }
                    .testTag(LibraryTestTags.GOAL_DAY_PREFIX + idx),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    letter,
                    style = FlowFolioType.pill.copy(
                        color = if (active) p.buttonInk else p.ink2.copy(alpha = DAY_OFF_INK)
                    )
                )
            }
        }
    }
}

/** Setting the reminder's time of day: an hour wheel and a minute wheel in five-minute steps. */
@Composable
private fun TimeOfDayDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onSet: (Int, Int) -> Unit) {
    val p = FlowFolio.palette
    var pickedHour by rememberSaveable { mutableIntStateOf(hour.coerceIn(0, HOURS_IN_DAY - 1)) }
    // A time saved before M4 can be on any minute, so it is snapped onto the nearest step when the wheel opens.
    var pickedStep by rememberSaveable { mutableIntStateOf((minute / MINUTE_STEP).coerceIn(0, STEPS - 1)) }
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = TIME_DIALOG_WIDTH)
                .fillMaxWidth()
                .clip(shape)
                .background(p.background, shape)
                .border(1.dp, p.edge, shape)
                .padding(20.dp)
                .testTag(LibraryTestTags.GOAL_TIME_DIALOG),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FlowFolioGlyph(Glyph.Clock, contentDescription = null, color = p.ink, size = 22.dp)
                Text(
                    stringResource(R.string.goal_time_title),
                    style = FlowFolioType.sectionTitle.merge(FlowFolio.title),
                    modifier = Modifier.weight(1f).padding(start = 10.dp)
                )
            }
            TimeWheels(pickedHour, pickedStep, { pickedHour = it }, { pickedStep = it })
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowFolioGhostButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
                FlowFolioPrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = { onSet(pickedHour, pickedStep * MINUTE_STEP) },
                    modifier = Modifier.testTag(LibraryTestTags.GOAL_TIME_SET)
                )
            }
        }
    }
}

/** The hour and minute wheels of [TimeOfDayDialog], under the same selection band as the goal's own wheels. */
@Composable
private fun TimeWheels(hour: Int, step: Int, onHour: (Int) -> Unit, onStep: (Int) -> Unit) {
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
                count = HOURS_IN_DAY,
                selected = hour,
                label = { String.format(Locale.US, "%02d", it) },
                onSelect = onHour,
                modifier = Modifier.width(96.dp)
            )
            Text(
                ":",
                style = FlowFolioType.heroTitle.merge(FlowFolio.title),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            WheelColumn(
                count = STEPS,
                selected = step,
                label = { String.format(Locale.US, "%02d", it * MINUTE_STEP) },
                onSelect = onStep,
                modifier = Modifier.width(96.dp)
            )
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT)
                .border(1.dp, p.ink.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
        )
    }
}

private val DAY_SIZE = 34.dp
private val TIME_DIALOG_WIDTH = 340.dp
private const val DAY_GLOW = 0.75f
private const val DAY_OFF_INK = 0.7f
private const val HOURS_IN_DAY = 24

/** The reminder's minutes move in steps: a 60-entry wheel is a long scroll for something set once. */
private const val MINUTE_STEP = 5
private const val STEPS = 60 / MINUTE_STEP
