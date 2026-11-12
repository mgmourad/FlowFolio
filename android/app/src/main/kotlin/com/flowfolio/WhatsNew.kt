package com.flowfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.flowfolio.core.data.Release
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioPrimaryButton
import com.flowfolio.core.designsystem.FlowFolioType

/**
 * The "what's new" window shown once after an update (owner, 2026-11-07). It is dismissed only by "Ok" -- there is
 * no outside-tap or back way out, so the notes cannot be swept away unread. The release notes are Markdown from the
 * releases page, rendered here with headings, bullets and bold, at a readable size.
 */
@Composable
internal fun WhatsNewDialog(release: Release, onOk: () -> Unit) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(24.dp)
    Dialog(onDismissRequest = { /* only "Ok" dismisses */ }) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(p.background, shape)
                .border(1.dp, p.edge, shape)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.whats_new_title),
                style = FlowFolioType.heroTitle.merge(FlowFolio.title)
            )
            Text(
                // The installed build, not the newest published release: a build ahead of the releases page
                // (or a release whose page is not up yet) was headed with the older version (M4).
                stringResource(R.string.whats_new_version, BuildConfig.VERSION_NAME.removeSuffix("-dev")),
                style = FlowFolioType.subtitle.merge(FlowFolio.hint)
            )
            Spacer(Modifier.height(2.dp))
            Column(
                Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // ADR-0052: this build's own notes from the bundled CHANGELOG, offline; the releases page's notes
                // only when the changelog has nothing for it.
                val context = androidx.compose.ui.platform.LocalContext.current
                // Changelog.current keeps to the reader-facing section when the version has one.
                val bundled = androidx.compose.runtime.remember {
                    val all = com.flowfolio.feature.library.Changelog.read(context)
                    val own = all.any { it.version == BuildConfig.VERSION_NAME.removeSuffix("-dev") }
                    if (own) com.flowfolio.feature.library.Changelog.current(all, BuildConfig.VERSION_NAME) else null
                }
                if (bundled != null) {
                    com.flowfolio.feature.library.VersionNotesView(bundled)
                } else {
                    ReleaseNotes(release.notes)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FlowFolioPrimaryButton(text = stringResource(R.string.whats_new_ok), onClick = onOk)
            }
        }
    }
}

/** The release notes, Markdown-lite: `#` headings, `-` bullets, `**bold**`, everything else body. */
@Composable
private fun ReleaseNotes(notes: String) {
    notes.lineSequence().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> Spacer(Modifier.height(4.dp))

            trimmed.startsWith('#') -> Text(
                trimmed.trimStart('#', ' '),
                style = FlowFolioType.rowLabel.copy(fontSize = 17.sp).merge(FlowFolio.title)
            )

            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> MarkdownLine(
                "•  " + trimmed.drop(2),
                style = FlowFolioType.prose.merge(FlowFolio.item)
            )

            else -> MarkdownLine(trimmed, style = FlowFolioType.prose.merge(FlowFolio.item))
        }
    }
}

/** One line of notes with `**bold**` spans rendered in a heavier weight. */
@Composable
private fun MarkdownLine(text: String, style: androidx.compose.ui.text.TextStyle) {
    val annotated = buildAnnotatedString {
        text.split("**").forEachIndexed { i, part ->
            if (i % 2 == 1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) }
            } else {
                append(part)
            }
        }
    }
    Text(annotated, style = style)
}
