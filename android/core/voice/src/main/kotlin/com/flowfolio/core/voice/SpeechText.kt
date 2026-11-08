package com.flowfolio.core.voice

/**
 * Normalizes reflowed book text for speech: the reflow view carries the book's curly quotes, hard line
 * breaks and hyphenation, which the Kokoro lexicon does not understand (they become skipped OOV words).
 */
object SpeechText {
    fun normalize(text: String): String = text
        .replace('\u2018', '\'') // ' -> '
        .replace('\u2019', '\'') // ' -> '
        .replace("\u201C", "") // " -> strip (quotes are not spoken)
        .replace("\u201D", "") // " -> strip
        .replace("\"", "") // straight " -> strip
        .replace('\u2014', ' ') // em dash -> break
        .replace('\u2013', '-') // en dash -> hyphen
        .replace("\u00AD", "") // soft hyphen -> join
        .replace('\u00A0', ' ') // non-breaking space
        .replace("\u2026", "...") // ellipsis
        .replace(HYPHENATED_BREAK, "$1") // word-<newline> -> word
        .replace('\n', ' ')
        .replace(WHITESPACE, " ")
        .trim()

    private val HYPHENATED_BREAK = Regex("(\\w)-\\s*\n")
    private val WHITESPACE = Regex("\\s+")
}
