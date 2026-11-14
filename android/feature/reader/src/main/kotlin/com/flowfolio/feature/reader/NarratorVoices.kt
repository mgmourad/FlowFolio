package com.flowfolio.feature.reader

import java.util.Locale

/**
 * A voice of the phone's speech engine as the voice picker shows it (ADR-0059): the engine's names
 * ("en-au-x-aub-network") mean nothing to a reader, so it reads "English (Australia)", "Voice 2", "Online".
 */
internal data class NarratorVoice(
    /** The engine's own name, which is what is saved and set. */
    val name: String,
    /** The language and country: "English (Australia)". */
    val language: String,
    /** The voice within its language: "Voice 2", the same number for its online and offline forms. */
    val label: String,
    /** True when it needs a connection. */
    val online: Boolean
)

internal object NarratorVoices {
    /**
     * The voices worth offering from [all]: those in [languages] (the phone's languages, its own country first),
     * each language's voices numbered by the engine's code for them so a voice keeps its number from one day to the
     * next, and the online and offline forms of one voice side by side.
     */
    fun of(all: List<android.speech.tts.Voice>, languages: List<Locale>): List<NarratorVoice> {
        val wanted = languages.map { it.language }.distinct()
        val kept = all.filter { it.locale.language in wanted }

        // The phone's own language and country first ("English (United States)" for an American phone), then the
        // same language elsewhere, alphabetically.
        fun rank(locale: Locale): Int {
            val exact = languages.indexOfFirst { it.language == locale.language && it.country == locale.country }
            return if (exact >= 0) exact else languages.size + wanted.indexOf(locale.language)
        }
        return kept.groupBy { it.locale }
            .toSortedMap(compareBy<Locale>({ rank(it) }, { languageName(it) }))
            .flatMap { (locale, voices) ->
                val codes = voices.map { codeOf(it.name) }.distinct().sorted()
                voices.sortedWith(compareBy({ codes.indexOf(codeOf(it.name)) }, { it.isNetworkConnectionRequired }))
                    .map { v ->
                        NarratorVoice(
                            name = v.name,
                            language = languageName(locale),
                            label = "${codes.indexOf(codeOf(v.name)) + 1}",
                            online = v.isNetworkConnectionRequired
                        )
                    }
            }
    }

    /** "English (Australia)", in the phone's own language. */
    private fun languageName(locale: Locale): String =
        locale.getDisplayName(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }

    /**
     * The part of an engine's voice name that tells one voice from another: "aub" in "en-au-x-aub-network", the
     * whole name otherwise ("en-AU-language", the engine's default voice for the language, sorts first as "").
     */
    internal fun codeOf(name: String): String {
        val parts = name.split('-')
        val x = parts.indexOf("x")
        return when {
            x >= 0 && x + 1 < parts.size -> parts[x + 1]
            name.endsWith("-language") -> ""
            else -> name
        }
    }
}
