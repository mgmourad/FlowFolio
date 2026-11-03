package com.flowfolio.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolioChip
import com.flowfolio.core.designsystem.scrollEdgeFades
import com.flowfolio.core.model.ReadingIntent

/**
 * The reader's own intention for a book (owner, M5), as chips under its details: tapping one sets it, tapping the
 * one already set clears it, so there is no separate "none" to hunt for.
 *
 * These say nothing about pages turned -- a book can be untouched for a year and still be paused rather than
 * abandoned -- which is why they are only ever set by hand.
 */
@Composable
internal fun IntentChips(current: ReadingIntent?, onIntent: (ReadingIntent?) -> Unit, modifier: Modifier = Modifier) {
    // One line, scrolled: four of these labels stacked into a column and pushed Read off the page. The ends fade
    // while there is more past them, so the row reads as something to slide (owner, M5 round 3).
    val scroll = rememberScrollState()
    Row(
        modifier
            .fillMaxWidth()
            .scrollEdgeFades(scroll)
            .horizontalScroll(scroll)
            // Inside the scroll, so the chosen chip's glow has room to spread rather than being cut off by the
            // row's own edge; the fade's layer is measured around it.
            .padding(vertical = GLOW_ROOM)
            .testTag(LibraryTestTags.INTENT_CHIPS),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReadingIntent.entries.forEach { intent ->
            val on = intent == current
            FlowFolioChip(
                text = stringResource(intent.label),
                selected = on,
                onClick = { onIntent(if (on) null else intent) },
                glow = true
            )
        }
    }
}

/** What each intention is called. */
internal val ReadingIntent.label: Int
    get() = when (this) {
        ReadingIntent.NEEDS_MOOD -> R.string.intent_needs_mood
        ReadingIntent.TO_STUDY -> R.string.intent_to_study
        ReadingIntent.PAUSED -> R.string.intent_paused
        ReadingIntent.WITH_SOMEONE -> R.string.intent_with_someone
    }

private val GLOW_ROOM = 7.dp
