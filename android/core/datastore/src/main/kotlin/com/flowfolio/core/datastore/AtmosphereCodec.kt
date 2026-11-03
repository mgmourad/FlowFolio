package com.flowfolio.core.datastore

import com.flowfolio.core.model.Ambient
import com.flowfolio.core.model.Atmosphere
import com.flowfolio.core.model.DEFAULT_AMBIENT_VOLUME
import com.flowfolio.core.model.LineSpacing
import com.flowfolio.core.model.PageMargins
import com.flowfolio.core.model.PaperStyle
import com.flowfolio.core.model.ReaderText
import com.flowfolio.core.model.ReadingFont

/**
 * The reader's saved atmospheres as one preference string (owner, M5).
 *
 * Written by hand rather than with kotlinx.serialization because `:core:datastore` does not carry that plugin and a
 * handful of flat records do not earn one. Fields are separated by the ASCII unit separator and records by the
 * record separator, neither of which a name typed on a phone can contain; a name is stripped of them anyway.
 *
 * Anything that fails to parse -- a field added later, a truncated write -- is skipped rather than thrown, so a bad
 * row costs one atmosphere instead of the reader's whole library of them.
 */
internal object AtmosphereCodec {
    private const val FIELD = '\u001F'
    private const val RECORD = '\u001E'

    fun encode(list: List<Atmosphere>): String = list.joinToString(RECORD.toString()) { a ->
        listOf(
            a.name.filterNot { it == FIELD || it == RECORD },
            a.text.font.name,
            a.text.sizeSp.toString(),
            a.text.lineSpacing.name,
            a.text.margins.name,
            a.text.justify.toString(),
            a.text.hyphenate.toString(),
            a.paper.name,
            a.ambient.name,
            a.volume.toString(),
            a.soundUri.orEmpty()
        ).joinToString(FIELD.toString())
    }

    fun decode(raw: String?): List<Atmosphere> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(RECORD).mapNotNull { record ->
            val f = record.split(FIELD)
            if (f.size < FIELDS || f[0].isBlank()) return@mapNotNull null
            Atmosphere(
                name = f[0],
                text = ReaderText(
                    font = f[1].toEnumOr(ReadingFont.LITERATA),
                    sizeSp = f[2].toIntOrNull() ?: ReaderText.DEFAULT_SIZE_SP,
                    lineSpacing = f[3].toEnumOr(LineSpacing.NORMAL),
                    margins = f[4].toEnumOr(PageMargins.NORMAL),
                    justify = f[5].toBooleanStrictOrNull() ?: true,
                    hyphenate = f[6].toBooleanStrictOrNull() ?: true
                ),
                paper = f[7].toEnumOr(PaperStyle.AUTO),
                ambient = f[8].toEnumOr(Ambient.SILENCE),
                volume = f[9].toFloatOrNull()?.coerceIn(0f, 1f) ?: DEFAULT_AMBIENT_VOLUME,
                builtIn = false,
                soundUri = f.getOrNull(SOUND_URI)?.takeIf { it.isNotBlank() }
            )
        }
    }

    private inline fun <reified T : Enum<T>> String.toEnumOr(fallback: T): T =
        enumValues<T>().firstOrNull { it.name == this } ?: fallback

    private const val FIELDS = 10

    /** Where the reader's own sound sits in a record; added after the first ten fields. */
    private const val SOUND_URI = 10
}
