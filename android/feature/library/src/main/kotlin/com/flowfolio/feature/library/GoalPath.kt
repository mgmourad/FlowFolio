@file:Suppress("MagicNumber")

package com.flowfolio.feature.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.ConfettiHeight
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioConfetti
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.rememberCelebration
import com.flowfolio.core.designsystem.softShadow
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.GoalPeriod
import com.flowfolio.core.model.ReadingGoal

/**
 * The curved reading-goal path in the library header (owner, M3T3): a route from reading glasses ([Glyph.Glasses]) to
 * an open book ([Glyph.OpenBook]) that fills as you near the goal, its colour shifting along the theme's gradient on
 * the way. Tapping it opens the Reading Goal window.
 */
@Suppress("LongMethod")
@Composable
internal fun GoalPath(goal: ReadingGoal?, readMillis: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = FlowFolio.palette
    val targetProgress = goal?.progress(readMillis) ?: 0f
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        if (shown) targetProgress else 0f,
        tween(Motion.GROW_MS, Motion.GROW_DELAY_MS, Motion.EaseOut),
        label = "goalPath"
    )
    val endColor = gradientColorAt(progress, p.ink2, p.ink, p.ink)
    val reached = targetProgress >= 1f
    val celebrate = rememberCelebration(targetProgress, goal?.scope?.let { "goal:" + it })
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 2.dp)
                .testTag(LibraryTestTags.GOAL_PATH),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FlowFolioGlyph(
                    Glyph.Glasses,
                    contentDescription = null,
                    color = if (goal != null) p.ink2 else p.ink2,
                    size = PATH_GLYPH,
                    glow = false
                )
                CurvedRoute(
                    progress = progress,
                    startColor = p.ink2,
                    midColor = p.ink,
                    endColor = endColor,
                    trackColor = p.ink2.copy(alpha = TRACK_ALPHA),
                    modifier = Modifier.weight(1f).height(PATH_HEIGHT)
                )
                FlowFolioGlyph(
                    Glyph.OpenBook,
                    contentDescription = null,
                    color = if (reached) p.ink else p.ink,
                    size = PATH_GLYPH,
                    glow = reached
                )
                // The goal is met: a small green tick at the end of the route (owner, M4).
                if (reached) {
                    FlowFolioGlyph(
                        Glyph.Check,
                        contentDescription = stringResource(R.string.goal_reached),
                        color = p.green,
                        size = PATH_TICK,
                        glow = true
                    )
                }
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .drawBehind {
                        drawRect(Brush.verticalGradient(listOf(p.buttonTop, p.buttonBottom)))
                        drawLine(
                            Color.White.copy(alpha = 0.45f),
                            Offset(0f, 0.5f),
                            Offset(size.width, 0.5f),
                            1.dp.toPx()
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    goalCaption(goal, readMillis),
                    style = FlowFolioType.pill.copy(color = p.buttonInk),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (celebrate.value) {
            FlowFolioConfetti(
                play = true,
                modifier = Modifier.fillMaxWidth().requiredHeight(ConfettiHeight)
            ) { celebrate.value = false }
        }
    }
}

@Composable
private fun goalCaption(goal: ReadingGoal?, readMillis: Long): String {
    if (goal == null) return stringResource(R.string.goal_path_empty)
    val periodLabel = when (goal.period) {
        GoalPeriod.DAY -> stringResource(R.string.goal_period_today)
        GoalPeriod.WEEK -> stringResource(R.string.goal_period_week)
        GoalPeriod.MONTH -> stringResource(R.string.goal_period_month)
    }
    return stringResource(R.string.goal_path_status, readTime(readMillis), readTime(goal.targetMillis), periodLabel)
}

@Composable
private fun CurvedRoute(
    progress: Float,
    startColor: Color,
    midColor: Color,
    endColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val pad = 4.dp.toPx()
        val start = Offset(pad, h * 0.86f)
        val end = Offset(w - pad, h * 0.14f)
        val full = Path().apply {
            moveTo(start.x, start.y)
            // The control points sit beyond the ends and just outside the box, which bends the route much further
            // than the M3T3 curve did (owner, M4).
            cubicTo(
                w * 0.26f,
                h * -0.16f,
                w * 0.74f,
                h * 1.16f,
                end.x,
                end.y
            )
        }
        val strokeWidth = 2.6.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()), 0f)
        drawPath(full, trackColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, pathEffect = dash))
        if (progress > 0f) {
            val measure = PathMeasure().apply { setPath(full, false) }
            val stop = measure.length * progress.coerceIn(0f, 1f)
            val filled = Path()
            measure.getSegment(0f, stop, filled, true)
            val brush = Brush.linearGradient(
                colors = listOf(startColor, midColor, endColor),
                start = start,
                end = end
            )
            drawPath(filled, brush = brush, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            val tip = measure.getPosition(stop)
            if (tip != Offset.Unspecified) {
                drawCircle(endColor, radius = 3.6.dp.toPx(), center = tip)
            }
        }
    }
}

/**
 * A progress bar whose colour moves along the theme's gradient (`subItem -> item -> title`) as the book or goal nears
 * its end (owner, M3T3).
 */
@Composable
internal fun GradientProgressBar(progress: Float, modifier: Modifier = Modifier, celebrationId: Any? = null) {
    val p = FlowFolio.palette
    val celebrate = rememberCelebration(progress, celebrationId)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val clamped = progress.coerceIn(0f, 1f)
    val grown by animateFloatAsState(
        if (shown) clamped else 0f,
        tween(Motion.GROW_MS, Motion.GROW_DELAY_MS, Motion.EaseOut),
        label = "gradientProgress"
    )
    val tipColor = gradientColorAt(clamped, p.ink2, p.ink, p.ink)
    val shape = RoundedCornerShape(6.dp)
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(shape)
                .drawBehind { drawRect(p.ink2.copy(alpha = 0.25f)) }
        ) {
            Spacer(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(grown)
                    .softShadow(shape, tipColor.copy(alpha = if (p.dark) 0.55f else 0f), blur = 8.dp)
                    .clip(shape)
                    .drawBehind {
                        drawRect(Brush.horizontalGradient(listOf(p.ink2, p.ink, tipColor)))
                    }
            )
        }
        if (celebrate.value) {
            FlowFolioConfetti(
                play = true,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().requiredHeight(ConfettiHeight)
            ) { celebrate.value = false }
        }
    }
}

internal fun gradientColorAt(progress: Float, start: Color, mid: Color, end: Color): Color {
    val t = progress.coerceIn(0f, 1f)
    return if (t < 0.5f) {
        lerp(start, mid, t * 2f)
    } else {
        lerp(mid, end, (t - 0.5f) * 2f)
    }
}

@Composable
internal fun rememberLibraryGoalDialog(
    statsViewModel: LibraryStatsViewModel,
    stats: LibraryStats,
    state: LibraryUiState,
    onOpenDetails: (Book, String) -> Unit
): () -> Unit {
    var open by rememberSaveable { mutableStateOf(false) }
    if (open) {
        val inProgress = remember(state.items) {
            state.items.map { it.book }.filter { it.lastPage != null && !it.finished }
        }
        ReadingGoalDialog(
            scope = ReadingGoal.LIBRARY_SCOPE,
            initial = stats.libraryGoal,
            inProgress = inProgress,
            onSave = statsViewModel::saveGoal,
            onReset = { statsViewModel.resetGoal(ReadingGoal.LIBRARY_SCOPE) },
            onOpenBook = { book -> onOpenDetails(book, "goal") },
            onDismiss = { open = false }
        )
    }
    return { open = true }
}

@Composable
internal fun rememberBookGoalDialog(book: Book, initial: ReadingGoal?, viewModel: DetailsViewModel): () -> Unit {
    var open by rememberSaveable { mutableStateOf(false) }
    if (open) {
        ReadingGoalDialog(
            scope = book.id,
            initial = initial,
            inProgress = emptyList(),
            onSave = viewModel::saveGoal,
            onReset = viewModel::resetGoal,
            onOpenBook = {},
            onDismiss = { open = false }
        )
    }
    return { open = true }
}

@Composable
internal fun rememberLibraryBookGoalDialog(statsViewModel: LibraryStatsViewModel): (Book) -> Unit {
    var target by remember { mutableStateOf<Book?>(null) }
    val book = target
    if (book != null) {
        val initial by remember(book.id) { statsViewModel.bookGoal(book.id) }.collectAsStateWithLifecycle(null)
        ReadingGoalDialog(
            scope = book.id,
            initial = initial,
            inProgress = emptyList(),
            onSave = statsViewModel::saveGoal,
            onReset = { statsViewModel.resetGoal(book.id) },
            onOpenBook = {},
            onDismiss = { target = null }
        )
    }
    return { target = it }
}

private val PATH_GLYPH = 16.dp
private val PATH_TICK = 13.dp

/** Taller since M4, so the deeper curve has somewhere to swing. */
private val PATH_HEIGHT = 34.dp
private const val TRACK_ALPHA = 0.35f
