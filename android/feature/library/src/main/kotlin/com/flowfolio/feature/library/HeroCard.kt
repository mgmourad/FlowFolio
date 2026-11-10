package com.flowfolio.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.BookCover
import com.flowfolio.core.designsystem.ButtonStyle
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioButton
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioProgressBar
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.coverLanding
import com.flowfolio.core.designsystem.coverSpot
import com.flowfolio.core.designsystem.shelfCoverKey
import kotlin.math.roundToInt

/**
 * Continue reading (`.hero`, ADR-0052): the book's cover, then "Continue reading", its title, its author, "Page X of Y"
 * with how far through, a thin progress bar and Resume (and Details, unfolded). Its menu sits in the card's top-right
 * corner, never under Resume. Unfolded, the reading-goal ring takes a third column ([goal]).
 */
@Suppress("LongMethod") // one layout, read top to bottom
@Composable
internal fun ContinueReadingCard(
    item: LibraryItem,
    actions: LibraryActions,
    wide: Boolean,
    goal: GoalStatus?,
    modifier: Modifier = Modifier
) {
    val p = FlowFolio.palette
    val book = item.book
    var menu by remember { mutableStateOf(false) }
    FlowFolioCard(
        modifier.fillMaxWidth().testTag(LibraryTestTags.HERO),
        padding = androidx.compose.foundation.layout.PaddingValues(if (wide) 22.dp else 18.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(if (wide) 26.dp else 18.dp)) {
            val cover by rememberCover(book)
            actions.heroSpot.face = book.face(cover)
            BookCover(
                book.face(cover),
                Modifier
                    .width(if (wide) 150.dp else 116.dp)
                    .clickable(role = Role.Button) { actions.onOpen(item, HERO_ORIGIN) }
                    .coverSpot(actions.heroSpot)
                    .coverLanding(shelfCoverKey(book.id, HERO_ORIGIN), 0f),
                badges = item.badges()
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.hero_eyebrow).uppercase(),
                    style = FlowFolioType.eyebrow.copy(color = p.accent),
                    modifier = Modifier.padding(end = if (wide) 0.dp else 34.dp)
                )
                Text(
                    book.title,
                    style = FlowFolioType.heroTitle.copy(
                        fontSize = if (wide) 32.sp else 25.sp,
                        lineHeight = if (wide) 35.sp else 27.sp,
                        color = p.ink
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                )
                book.author?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = FlowFolioType.body.copy(fontSize = 13.sp, color = p.ink2),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 7.dp)) {
                    Text(
                        stringResource(
                            R.string.hero_where,
                            (book.lastPage ?: 0) + 1,
                            "%,d".format(book.pageCount)
                        ),
                        style = FlowFolioType.body.copy(fontSize = 12.5.sp, color = p.ink2),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        stringResource(R.string.hero_percent, (book.progress * 100).roundToInt()),
                        style = FlowFolioType.body.copy(fontSize = 12.5.sp, color = p.ink2)
                    )
                }
                FlowFolioProgressBar(book.progress.coerceAtLeast(MIN_SHOWN))
                // Owner, 2026-11-10: unfolded, Details sits on Resume's line rather than wrapping under it.
                Row(
                    Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlowFolioButton(
                        stringResource(R.string.action_resume),
                        { actions.onResume(item) },
                        Modifier.testTag(LibraryTestTags.RESUME),
                        small = wide,
                        icon = Glyph.Play
                    )
                    if (wide) {
                        FlowFolioButton(
                            stringResource(R.string.action_details),
                            { actions.onOpen(item, HERO_ORIGIN) },
                            style = ButtonStyle.GHOST,
                            small = true
                        )
                    }
                }
            }
            if (wide && goal != null) {
                GoalRing(goal, actions.onReadingGoal, Modifier.width(136.dp).align(Alignment.CenterVertically))
            }
        }
        Box(Modifier.align(Alignment.TopEnd).offset(x = MORE_NUDGE, y = -MORE_NUDGE)) {
            FlowFolioIconButton(
                onClick = { menu = true },
                size = 36.dp,
                ghost = true,
                tooltip = stringResource(R.string.action_more),
                modifier = Modifier.testTag(LibraryTestTags.HERO_MORE)
            ) {
                FlowFolioGlyph(Glyph.More, null, color = p.ink2, size = 18.dp)
            }
            BookMenu(item, HERO_ORIGIN, menu, actions) { menu = false }
        }
    }
}

private const val MIN_SHOWN = 0.02f
private val MORE_NUDGE = 8.dp
