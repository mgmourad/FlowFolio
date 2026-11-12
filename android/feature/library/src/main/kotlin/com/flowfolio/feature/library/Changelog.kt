package com.flowfolio.feature.library

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph

/** One version's notes from `CHANGELOG.md`: its name ("0.6.0-canary.1", or "Unreleased"), date and sections. */
data class VersionNotes(val version: String, val date: String?, val sections: List<NotesSection>)

/** "New", "Changed" or "Fixed" (Keep a Changelog's Added, Changed, Fixed...), with its bullet lines. */
data class NotesSection(val heading: String, val lines: List<NotesLine>)

/** A bullet at [depth] (0 top level). */
data class NotesLine(val depth: Int, val text: String)

/**
 * Reads `CHANGELOG.md` (ADR-0052: What's new comes from the changelog bundled in the APK, so it works offline and says
 * exactly what this build is). Versions are `## [x]` headings, sections `### X`; block quotes and links are dropped.
 */
object Changelog {
    /** The file the build copies into the APK's assets (`app/build.gradle.kts`). */
    const val ASSET = "CHANGELOG.md"

    fun read(context: Context): List<VersionNotes> = runCatching {
        context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) }
    }.getOrDefault(emptyList())

    fun parse(markdown: String): List<VersionNotes> {
        val builder = Builder()
        markdown.lineSequence().forEach { builder.line(it.trimEnd()) }
        return builder.finish().filter { it.sections.isNotEmpty() }
    }

    /** Collects versions, sections and bullets line by line. */
    private class Builder {
        val out = ArrayList<VersionNotes>()
        var version: String? = null
        var date: String? = null
        val sections = ArrayList<NotesSection>()
        var heading: String? = null
        val lines = ArrayList<NotesLine>()

        fun line(line: String) {
            when {
                line.startsWith("## ") -> startVersion(line)

                line.startsWith("### ") && version != null -> startSection(line)

                heading == null -> Unit

                BULLET.matches(line) -> bullet(line)

                // A bullet's wrapped continuation joins the bullet before it.
                line.isNotBlank() && !line.trimStart().startsWith(">") && lines.isNotEmpty() ->
                    lines[lines.lastIndex] = lines.last().let { it.copy(text = it.text + " " + clean(line.trim())) }
            }
        }

        fun finish(): List<VersionNotes> {
            closeVersion()
            return out
        }

        private fun startVersion(line: String) {
            closeVersion()
            val m = VERSION.find(line)
            version = m?.groupValues?.get(1) ?: line.removePrefix("## ").trim()
            date = m?.groupValues?.get(2)?.takeIf { it.isNotBlank() }
        }

        private fun startSection(line: String) {
            closeSection()
            heading = friendly(line.removePrefix("### ").trim())
        }

        private fun bullet(line: String) {
            val indent = line.indexOfFirst { !it.isWhitespace() }
            lines += NotesLine(indent / 2, clean(line.trimStart().drop(2)))
        }

        private fun closeSection() {
            heading?.let { if (lines.isNotEmpty()) sections += NotesSection(it, lines.toList()) }
            lines.clear()
        }

        private fun closeVersion() {
            closeSection()
            version?.let { out += VersionNotes(it, date, sections.toList()) }
            sections.clear()
            heading = null
        }
    }

    /**
     * The notes for [versionName] (a debug build's "-dev" ignored), else the newest version that has any, labelled as
     * the installed build when they are "Unreleased": a build ahead of its changelog heading is what the reader is
     * running (M4: such a build was headed "Unreleased" or with an older version). An empty "Unreleased" is skipped.
     *
     * A version with a `### For readers` section shows only that section: the rest of the changelog is written for
     * the people building FlowFolio (corpus numbers, module names), not for someone who just updated the app.
     */
    fun current(all: List<VersionNotes>, versionName: String): VersionNotes? {
        val installed = versionName.removeSuffix("-dev")
        val notes = all.firstOrNull { it.version == installed }
            ?: all.firstOrNull { it.sections.isNotEmpty() }
                ?.let { if (it.version.equals(UNRELEASED, ignoreCase = true)) it.copy(version = installed) else it }
            ?: return null
        val forReaders = notes.sections.filter { it.heading.equals(FOR_READERS, ignoreCase = true) }
        return if (forReaders.isEmpty()) notes else notes.copy(sections = forReaders)
    }

    private const val UNRELEASED = "Unreleased"

    /** The changelog section What's new shows on its own when a version has one. */
    const val FOR_READERS = "For readers"

    private fun friendly(heading: String): String = when {
        heading.startsWith("Added", ignoreCase = true) -> "New"
        else -> heading.substringBefore(" (")
    }

    private fun clean(s: String): String = LINK.replace(s) { it.groupValues[1] }.replace("`", "")

    private val VERSION = Regex("""^## \[([^\]]+)\](?:\s*-\s*(.+))?""")
    private val BULLET = Regex("""^\s*[-*] .*""")
    private val LINK = Regex("""\[([^\]]+)\]\([^)]*\)""")
}

/** One version's notes, section by section, `**bold**` kept. */
@Composable
fun VersionNotesView(notes: VersionNotes) {
    val p = FlowFolio.palette
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        notes.sections.forEach { section ->
            Text(
                section.heading.uppercase(),
                style = FlowFolioType.eyebrow.copy(color = p.accent),
                modifier = Modifier.padding(top = 8.dp)
            )
            section.lines.forEach { line ->
                Row(Modifier.padding(start = (line.depth * INDENT).dp)) {
                    Text(if (line.depth == 0) "•  " else "–  ", style = FlowFolioType.body.copy(color = p.ink3))
                    Text(bold(line.text), style = FlowFolioType.body.copy(color = p.ink))
                }
            }
        }
    }
}

/** Every version: the first open, the rest one tap away (ADR-0052: "earlier versions collapsible"). */
@Composable
fun ChangelogView(all: List<VersionNotes>, first: VersionNotes?) {
    val p = FlowFolio.palette
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        first?.let { VersionNotesView(it) }
        all.filter { it != first }.forEach { v ->
            var open by rememberSaveable(v.version) { mutableStateOf(false) }
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        listOfNotNull(v.version, v.date).joinToString(" · "),
                        style = FlowFolioType.rowLabel.copy(color = p.ink),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        stringResource(if (open) R.string.changelog_hide else R.string.changelog_show),
                        style = FlowFolioType.rowDetail.copy(color = p.accent)
                    )
                    FlowFolioGlyph(Glyph.More, null, color = p.ink3, size = 16.dp, glow = false)
                }
                AnimatedVisibility(open) { VersionNotesView(v) }
            }
        }
    }
}

private fun bold(text: String) = buildAnnotatedString {
    text.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
    }
}

private const val INDENT = 14
