package com.flowfolio.feature.library

import java.text.Normalizer

/**
 * Fuzzy matching for the library's search (owner, M3T2): how well a query matches a text, 0 for not at all. Case and
 * accents never matter. Best is the text starting with the query, then the query starting a word, then anywhere in
 * the text; then every word of the query found in the text's words, each as a prefix or with one letter wrong, added
 * or missing ("phisics" finds "Physics"); last, the query's letters in order, close together ("unphy" finds
 * "University Physics").
 */
internal object Fuzzy {
    fun score(query: String, text: String?): Int {
        val q = fold(query).trim()
        val t = text?.let(::fold).orEmpty()
        val at = t.indexOf(q)
        return when {
            q.isEmpty() || t.isEmpty() -> 0
            at == 0 -> STARTS
            at > 0 && !t[at - 1].isLetterOrDigit() -> WORD_STARTS
            at > 0 -> INSIDE
            else -> maxOf(wordsScore(q, t), lettersScore(q, t))
        }
    }

    /** Lower case without accents ("Éclair" -> "eclair"). */
    fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "").lowercase()

    /** Every query word in the text, each as a word's prefix or within one edit of a word. */
    private fun wordsScore(q: String, t: String): Int {
        val words = t.split(SPLIT).filter { it.isNotEmpty() }
        val wanted = q.split(SPLIT).filter { it.isNotEmpty() }
        var misses = 0
        for (w in wanted) {
            when {
                words.any { it.startsWith(w) } -> Unit
                w.length >= TYPO_MIN && words.any { near(w, it) || near(w, it.take(w.length)) } -> misses++
                else -> return 0
            }
        }
        return WORDS - misses * TYPO_COST
    }

    /** The query's letters in order in the text, scored by how close together they are. */
    private fun lettersScore(q: String, t: String): Int {
        val letters = q.filter { it.isLetterOrDigit() }
        // Where each letter is found, after the one before it; MISSING from the first that is not.
        val at = letters.runningFold(-1) { prev, c ->
            if (prev == MISSING) MISSING else t.indexOf(c, prev + 1).takeIf { it >= 0 } ?: MISSING
        }.drop(1)
        val spread = if (at.isEmpty() || at.last() < 0) Int.MAX_VALUE else at.last() + 1 - at.first()
        // Letters scattered through a long text are chance, not a match.
        return if (letters.length < LETTERS_MIN || spread > letters.length * SPREAD_MAX) {
            0
        } else {
            (LETTERS * letters.length / spread).coerceIn(1, LETTERS)
        }
    }

    /** Whether [a] and [b] are at most one edit apart (a letter changed, added or left out). */
    fun near(a: String, b: String): Boolean {
        var i = 0
        var j = 0
        var edits = if (kotlin.math.abs(a.length - b.length) > 1) 2 else 0
        while (edits <= 1 && i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                i++
                j++
                continue
            }
            edits++
            when {
                a.length > b.length -> i++

                a.length < b.length -> j++

                else -> {
                    i++
                    j++
                }
            }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }

    const val STARTS = 100
    const val WORD_STARTS = 90
    const val INSIDE = 75
    const val WORDS = 60
    private const val TYPO_COST = 10
    private const val TYPO_MIN = 4
    private const val LETTERS = 40
    private const val LETTERS_MIN = 3
    private const val SPREAD_MAX = 3
    private const val MISSING = -2
    private val MARKS = Regex("\\p{Mn}+")
    private val SPLIT = Regex("[^\\p{L}\\p{N}]+")
}
