package com.flowfolio.feature.library

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.flowfolio.core.designsystem.FeatureSplash
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGhostButton
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.TipCard
import com.flowfolio.core.model.UserSettings

/**
 * The things the app introduces itself with (owner, M5): a window the first time a feature is used, and quiet tips
 * beside features a reader could otherwise walk past.
 *
 * Each is shown once and then never again, which is what keeps them worth reading. [id] is what is remembered, so
 * it must not change once a build has shipped with it; reordering the entries is free, renaming one is not.
 */
enum class Guide(
    val id: String,
    val glyph: Glyph,
    @StringRes val title: Int,
    @StringRes val body: Int,
    @StringRes val tip: Int = body
) {
    Welcome("welcome", Glyph.Library, R.string.guide_welcome_title, R.string.guide_welcome_body),
    Atmosphere("atmosphere", Glyph.Sound, R.string.guide_atmosphere_title, R.string.guide_atmosphere_body),
    ReadingGoal("reading_goal", Glyph.ReadingGoal, R.string.guide_goal_title, R.string.guide_goal_body),
    DynamicFlow("dynamic_flow", Glyph.DynamicFlow, R.string.guide_flow_title, R.string.guide_flow_body),
    Dictionary("dictionary", Glyph.Translate, R.string.guide_dictionary_title, R.string.guide_dictionary_body),
    Intents("intents", Glyph.Bookmark, R.string.guide_intents_title, R.string.guide_intents_body),
    VoiceNote("voice_note", Glyph.Mic, R.string.guide_voice_title, R.string.guide_voice_body),
    Detour("detour", Glyph.Detour, R.string.guide_detour_title, R.string.guide_detour_body);

    /** Whether this one still has something to say: tips are on, and this guide has not been seen. */
    fun unseen(settings: UserSettings): Boolean = settings.tipsEnabled && id !in settings.seenGuides
}

/**
 * Shows [guide]'s window the first time its feature is reached, then marks it seen (owner, M5). Drawing nothing
 * once it has been seen is the whole point, so this is safe to leave in place permanently.
 */
@Composable
internal fun GuideSplash(
    guide: Guide,
    settings: UserSettings,
    onSeen: (String) -> Unit,
    onNeverShow: (() -> Unit)? = null
) {
    if (!guide.unseen(settings)) return
    FeatureSplash(
        glyph = guide.glyph,
        title = stringResource(guide.title),
        body = stringResource(guide.body),
        dismiss = stringResource(R.string.guide_got_it),
        onDismiss = { onSeen(guide.id) },
        neverShow = stringResource(R.string.guide_never_show),
        onNeverShow = onNeverShow
    )
}

/** The quieter form: a line beside the feature rather than a window over it. */
@Composable
internal fun GuideTip(guide: Guide, settings: UserSettings, onSeen: (String) -> Unit, modifier: Modifier = Modifier) {
    if (!guide.unseen(settings)) return
    TipCard(text = stringResource(guide.tip), onDismiss = { onSeen(guide.id) }, modifier = modifier)
}

/**
 * The whole tour in one window, from the floating help button in Settings (owner, M5). Unlike the splashes it can
 * be opened whenever, so it repeats what they said rather than assuming they were read.
 */
@Composable
internal fun TutorialSheet(onDismiss: () -> Unit) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = TUTORIAL_WIDTH)
                .fillMaxWidth()
                .heightIn(max = TUTORIAL_HEIGHT)
                .clip(shape)
                .background(p.background, shape)
                .border(1.dp, p.edge, shape)
                .padding(22.dp)
                .verticalScroll(rememberScrollState())
                .testTag(LibraryTestTags.TUTORIAL),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowFolioGlyph(Glyph.Help, contentDescription = null, color = p.ink, size = 24.dp)
                Text(
                    stringResource(R.string.tutorial_title),
                    style = FlowFolioType.sectionTitle.merge(FlowFolio.title),
                    modifier = Modifier.weight(1f).padding(start = 10.dp)
                )
            }
            Text(stringResource(R.string.tutorial_intro), style = FlowFolioType.sub.merge(FlowFolio.hint))
            Guide.entries.forEach { guide ->
                HorizontalDivider(color = p.edge)
                TutorialEntry(guide)
            }
            FlowFolioGhostButton(
                text = stringResource(R.string.guide_got_it),
                onClick = onDismiss,
                modifier = Modifier.testTag(LibraryTestTags.TUTORIAL_CLOSE)
            )
        }
    }
}

/** One feature in the tour: its glyph, its name, and what it is for. */
@Composable
private fun TutorialEntry(guide: Guide) {
    val p = FlowFolio.palette
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.size(TUTORIAL_PLATE).clip(CircleShape).background(p.surface, CircleShape),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            FlowFolioGlyph(guide.glyph, contentDescription = null, color = p.ink2, size = 20.dp, glow = false)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(guide.title), style = FlowFolioType.rowLabel.merge(FlowFolio.item))
            Text(stringResource(guide.body), style = FlowFolioType.rowDetail.merge(FlowFolio.hint))
        }
    }
}

private val TUTORIAL_WIDTH = 440.dp
private val TUTORIAL_HEIGHT = 640.dp
private val TUTORIAL_PLATE = 40.dp
