package com.flowfolio.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioRing
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.card
import com.flowfolio.core.model.GoalPeriod
import com.flowfolio.core.model.ReadingGoal
import com.flowfolio.core.model.StatsBand

/** Where a stats tile leads (ADR-0052): a Library chip, a Notebook section, or the reading goal. */
sealed interface StatTarget {
    data class Filter(val filter: LibraryFilter) : StatTarget

    data class Notebook(val section: NotebookSection) : StatTarget

    data object Goal : StatTarget
}

/**
 * The stats band (`.statsband`, ADR-0052): one summary line, "17 books · 5 in progress · 2 finished" (plus ribbons and
 * time read unfolded), with More or Less and a chevron. Open, it shows eight tiles, each leading to its filtered view.
 * Opening and closing spring; the state is remembered ([StatsBand]).
 */
@Composable
internal fun StatsBandCard(
    state: LibraryUiState,
    stats: LibraryStats,
    wide: Boolean,
    actions: LibraryActions,
    modifier: Modifier = Modifier
) {
    val p = FlowFolio.palette
    val open = state.statsBand == StatsBand.EXPANDED
    val turn by animateFloatAsState(if (open) HALF_TURN else 0f, Motion.springSpec(), label = "statsChevron")
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.fillMaxWidth().card(shape).clip(shape).testTag(LibraryTestTags.STATS_BAND)) {
        val label = stringResource(if (open) R.string.stats_less else R.string.stats_more)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) {
                    actions.onStatsBand(if (open) StatsBand.COLLAPSED else StatsBand.EXPANDED)
                }
                .semantics { stateDescription = label }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FlowFolioGlyph(Glyph.Stopwatch, null, color = p.accent, size = 18.dp, glow = false)
            Text(
                summary(state, stats, wide),
                style = FlowFolioType.body.copy(fontSize = 13.5.sp, color = p.ink2),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 13.5.sp),
                modifier = Modifier.weight(1f)
            )
            Text(label, style = FlowFolioType.body.copy(fontSize = 12.sp, color = p.ink3))
            FlowFolioGlyph(
                Glyph.Back,
                null,
                color = p.ink3,
                size = 16.dp,
                glow = false,
                modifier = Modifier.rotate(CHEVRON_DOWN + turn)
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(spring(stiffness = BAND_STIFFNESS)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = BAND_STIFFNESS)) + fadeOut()
        ) {
            StatTiles(state, stats, if (wide) WIDE_COLUMNS else NARROW_COLUMNS, actions.onStat)
        }
    }
}

@Composable
private fun summary(state: LibraryUiState, stats: LibraryStats, wide: Boolean): AnnotatedString {
    val books = pluralStringResource(R.plurals.stats_books_n, state.totalCount, state.totalCount)
    val progress = stringResource(R.string.library_in_progress, state.inProgressCount)
    val finished = stringResource(R.string.library_finished, state.finishedCount)
    val text = if (wide) {
        stringResource(
            R.string.stats_summary_wide,
            books,
            progress,
            finished,
            pluralStringResource(R.plurals.stats_ribbons_n, stats.bookmarks, stats.bookmarks),
            readTime(stats.readMillis)
        )
    } else {
        stringResource(R.string.stats_summary, books, progress, finished)
    }
    return boldNumbers(text, FlowFolio.palette.ink)
}

/** The figures in [text] in ink and semi-bold (`.sum b`), the words around them in the second ink. */
internal fun boldNumbers(text: String, ink: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        append(text)
        Regex("""[<]?\d[\d,.]*\s?[hm]?(?:\s\d+m)?""").findAll(text).forEach {
            addStyle(SpanStyle(color = ink, fontWeight = FontWeight.SemiBold), it.range.first, it.range.last + 1)
        }
    }

@Composable
private fun StatTiles(state: LibraryUiState, stats: LibraryStats, columns: Int, onStat: (StatTarget) -> Unit) {
    val tiles = listOf(
        Tile("books", Glyph.Library, R.string.stats_books, "${state.totalCount}", StatTarget.Filter(LibraryFilter.ALL)),
        Tile(
            "progress",
            Glyph.Hourglass,
            R.string.stats_in_progress,
            "${state.inProgressCount}",
            StatTarget.Filter(LibraryFilter.IN_PROGRESS)
        ),
        Tile(
            "finished",
            Glyph.Check,
            R.string.stats_finished,
            "${state.finishedCount}",
            StatTarget.Filter(LibraryFilter.FINISHED)
        ),
        Tile(
            "favorites",
            Glyph.Star,
            R.string.stats_favorites,
            "${state.favoriteCount}",
            StatTarget.Filter(LibraryFilter.FAVORITES)
        ),
        Tile(
            "bookmarks",
            Glyph.Bookmark,
            R.string.stats_bookmarks,
            "${stats.bookmarks}",
            StatTarget.Notebook(NotebookSection.BOOKMARKS)
        ),
        Tile(
            "annotations",
            Glyph.Highlight,
            R.string.stats_annotations,
            "${stats.annotations}",
            StatTarget.Notebook(NotebookSection.HIGHLIGHTS)
        ),
        Tile(
            "words",
            Glyph.Translate,
            R.string.stats_words,
            "${stats.dictionary}",
            StatTarget.Notebook(NotebookSection.WORDS)
        ),
        Tile("time", Glyph.Glasses, R.string.stats_reading_time, readTime(stats.readMillis), StatTarget.Goal)
    )
    Column(
        Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tiles.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { StatTileView(it, onStat, Modifier.weight(1f)) }
            }
        }
    }
}

private class Tile(val key: String, val glyph: Glyph, val label: Int, val value: String, val target: StatTarget)

/** `.stile`: a glyph in the accent, the figure in Fraunces, the label in the third ink. */
@Composable
private fun StatTileView(tile: Tile, onStat: (StatTarget) -> Unit, modifier: Modifier) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(p.surface2, shape)
            .clickable(role = Role.Button) { onStat(tile.target) }
            .padding(horizontal = 8.dp, vertical = 11.dp)
            .testTag(LibraryTestTags.statTile(tile.key))
    ) {
        FlowFolioGlyph(tile.glyph, null, color = p.accent, size = 16.dp, glow = false)
        Text(
            tile.value,
            style = FlowFolioType.stat.copy(fontSize = 20.sp, color = p.ink),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 20.sp),
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            stringResource(tile.label),
            style = FlowFolioType.body.copy(fontSize = 11.sp, color = p.ink3),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp)
        )
    }
}

/** What the reading-goal card and ring say: time read in the goal's period, and whether the goal is met. */
internal class GoalStatus(val goal: ReadingGoal?, val readMillis: Long) {
    val progress: Float get() = goal?.progress(readMillis) ?: 0f
    val reached: Boolean get() = goal != null && progress >= 1f
}

/**
 * The folded reading-goal card (`.goalmini`, ADR-0052): a 46 dp ring, the time read today, whether the goal is met
 * and how big it is, the reminder when one is set, and a chevron. It opens the goal.
 */
@Suppress("LongMethod") // one layout, read top to bottom
@Composable
internal fun GoalCard(status: GoalStatus, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(22.dp)
    val goal = status.goal
    Row(
        modifier
            .fillMaxWidth()
            .card(shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(LibraryTestTags.GOAL_CARD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        FlowFolioRing(status.progress, diameter = 46.dp, color = if (status.reached) p.ok else null)
        Column(Modifier.weight(1f)) {
            if (goal == null) {
                Text(stringResource(R.string.goal_card_none), style = FlowFolioType.rowLabel.copy(color = p.ink))
                Text(
                    stringResource(R.string.goal_card_none_detail),
                    style = FlowFolioType.body.copy(fontSize = 12.5.sp, color = p.ink2)
                )
            } else {
                Text(
                    stringResource(goal.period.readLine, readTime(status.readMillis)),
                    style = FlowFolioType.rowLabel.copy(color = p.ink)
                )
                val size = readTime(goal.targetMillis)
                val unit = stringResource(goal.period.unit)
                Text(
                    if (status.reached) {
                        stringResource(R.string.goal_card_reached, size, unit)
                    } else {
                        val left = (goal.targetMillis - status.readMillis).coerceAtLeast(0L)
                        stringResource(R.string.goal_card_to_go, readTime(left), size, unit)
                    },
                    style = FlowFolioType.body.copy(fontSize = 12.5.sp, color = if (status.reached) p.ok else p.ink2),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (goal.remindEnabled) {
                    val days = Integer.bitCount(goal.remindDaysMask)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FlowFolioGlyph(Glyph.Clock, null, color = p.ink2, size = 13.dp, glow = false)
                        Text(
                            stringResource(
                                R.string.goal_card_reminder,
                                "%02d:%02d".format(goal.remindHour, goal.remindMinute),
                                pluralStringResource(R.plurals.goal_days_n, days, days)
                            ),
                            style = FlowFolioType.body.copy(fontSize = 12.5.sp, color = p.ink2)
                        )
                    }
                }
            }
        }
        FlowFolioGlyph(
            Glyph.Back,
            null,
            color = p.ink3,
            size = 20.dp,
            glow = false,
            modifier = Modifier.rotate(HALF_TURN)
        )
    }
}

/**
 * The unfolded goal ring (`.goalring`), in the Continue reading card's third column: 104 dp, the time read in its
 * middle over "of 45m", and "Goal reached ✓" or what is left under it.
 */
@Composable
internal fun GoalRing(status: GoalStatus, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val p = FlowFolio.palette
    val goal = status.goal
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(8.dp)
            .testTag(LibraryTestTags.READING_GOAL),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FlowFolioRing(status.progress, diameter = 104.dp, color = if (status.reached) p.ok else null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(readTime(status.readMillis), style = FlowFolioType.stat.copy(fontSize = 22.sp, color = p.ink))
                if (goal != null) {
                    Text(
                        stringResource(R.string.goal_ring_of, readTime(goal.targetMillis)),
                        style = FlowFolioType.body.copy(fontSize = 10.5.sp, color = p.ink3)
                    )
                }
            }
        }
        Text(
            when {
                goal == null -> stringResource(R.string.goal_card_none)

                status.reached -> stringResource(R.string.goal_ring_reached)

                else -> stringResource(
                    R.string.goal_ring_to_go,
                    readTime((goal.targetMillis - status.readMillis).coerceAtLeast(0L))
                )
            },
            style = FlowFolioType.body.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (status.reached) p.ok else p.ink2
            ),
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

private val GoalPeriod.readLine: Int
    get() = when (this) {
        GoalPeriod.DAY -> R.string.goal_read_today
        GoalPeriod.WEEK -> R.string.goal_read_week
        GoalPeriod.MONTH -> R.string.goal_read_month
    }

private val GoalPeriod.unit: Int
    get() = when (this) {
        GoalPeriod.DAY -> R.string.goal_unit_day
        GoalPeriod.WEEK -> R.string.goal_unit_week
        GoalPeriod.MONTH -> R.string.goal_unit_month
    }

private const val HALF_TURN = 180f
private const val CHEVRON_DOWN = -90f
private const val WIDE_COLUMNS = 8
private const val NARROW_COLUMNS = 4
private const val BAND_STIFFNESS = 500f
