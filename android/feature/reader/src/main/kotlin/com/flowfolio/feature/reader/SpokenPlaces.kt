package com.flowfolio.feature.reader

/** The narrator's marks on the page (ADR-0059): the sentence being read, light, and the word being said. */
internal const val SPOKEN_SENTENCE_ID = -2L
internal const val SPOKEN_WORD_ID = -3L

/**
 * Where the narrator's words are on the page (ADR-0059), so the sentence being read and the word being said can be
 * highlighted there. The sentences the narrator says are the page's text prepared for speech (hyphens joined,
 * headers dropped, "e.g." said as "for example"), so they are matched to the page word by word, in order:
 * - each spoken word is looked for a few words ahead of the last one found, so a word the page lacks (an expanded
 *   abbreviation) or a page word never said (a running head) costs nothing further on;
 * - a word broken across lines on the page ("infor-" / "mation") matches its spoken whole.
 */
internal class SpokenPlaces(page: String, sentences: List<String>) {
    private class Token(val start: Int, val end: Int, val key: String)

    /** For each sentence, its words' places in the sentence and on the page (null where not found). */
    private val words: List<List<Pair<Token, IntRange?>>>

    init {
        val onPage = tokens(page)
        var cursor = 0
        words = sentences.map { sentence ->
            val spoken = tokens(sentence)
            spoken.mapIndexed { k, w ->
                // A short word ("the", "a") is taken only where the next spoken word follows it, so the "The" of a
                // running head above is not where the sentence starts.
                val next = spoken.getOrNull(k + 1)?.key
                val fits = { at: IntRange ->
                    w.key.length > SHORT || next == null || find(onPage, at.last + 1, next, FOLLOW) != null
                }
                // "Mister" is said for the page's "Mr.": the printed form is looked for too.
                val found = find(onPage, cursor, w.key, fits = fits)
                    ?: PRINTED[w.key]?.let { find(onPage, cursor, it, fits = fits) }
                if (found != null) cursor = found.last + 1
                w to found?.let { onPage[it.first].start until onPage[it.last].end }
            }
        }
    }

    /** The page text sentence [index] covers, from its first word found to its last; null when none was. */
    fun sentence(index: Int): IntRange? {
        val found = words.getOrNull(index)?.mapNotNull { it.second }.orEmpty()
        if (found.isEmpty()) return null
        return found.first().first..found.last().last
    }

    /** The page text of the word at [start]..[end] of sentence [index]'s text (the engine's range for it). */
    fun word(index: Int, start: Int, end: Int): IntRange? = words.getOrNull(index)
        ?.firstOrNull { (w, place) -> place != null && w.end > start && w.start < end.coerceAtLeast(start + 1) }
        ?.second

    private companion object {
        /** How many page words a spoken word may be ahead of the last one found. */
        const val WINDOW = 12

        /** The longest run of page pieces one spoken word may join ("infor" + "ma" + "tion"). */
        const val MAX_PIECES = 3

        val WORD = Regex("""[\p{L}\p{N}]+""")

        fun tokens(text: String): List<Token> =
            WORD.findAll(text).map { Token(it.range.first, it.range.last + 1, it.value.lowercase()) }.toList()

        /** The page tokens (first..last index) that spell [key], starting within [WINDOW] of [from]. */
        @Suppress("ReturnCount", "CyclomaticComplexMethod") // a word whole, broken over pieces, or found again
        fun find(
            onPage: List<Token>,
            from: Int,
            key: String,
            window: Int = WINDOW,
            fits: (IntRange) -> Boolean = { true }
        ): IntRange? {
            for (i in from until minOf(onPage.size, from + window)) {
                if (onPage[i].key == key && fits(i..i)) return i..i
                if (!key.startsWith(onPage[i].key)) continue
                var joined = onPage[i].key
                for (j in i + 1 until minOf(onPage.size, i + MAX_PIECES)) {
                    joined += onPage[j].key
                    if (joined == key && fits(i..j)) return i..j
                    if (!key.startsWith(joined)) break
                }
            }
            if (window < WINDOW) return null
            // Lost (a table or a figure the narrator does not read): a longer word further on finds the place again.
            if (key.length >= RESYNC_LENGTH) {
                for (i in minOf(onPage.size, from + WINDOW) until onPage.size) if (onPage[i].key == key) return i..i
            }
            return null
        }

        /** The words `SpeechText` says in full, by the printed form they stand for. */
        val PRINTED = mapOf(
            "mister" to "mr",
            "missus" to "mrs",
            "miz" to "ms",
            "doctor" to "dr",
            "professor" to "prof",
            "junior" to "jr",
            "figure" to "fig",
            "number" to "no",
            "page" to "p",
            "pages" to "pp",
            "chapter" to "ch",
            "volume" to "vol",
            "versus" to "vs",
            "approximately" to "approx"
        )

        /** Words this short are common enough to need the next word beside them. */
        const val SHORT = 3

        /** How near the next spoken word must follow a short one. */
        const val FOLLOW = 2

        /** A word this long is rare enough on one page to find the place again by. */
        const val RESYNC_LENGTH = 6
    }
}
