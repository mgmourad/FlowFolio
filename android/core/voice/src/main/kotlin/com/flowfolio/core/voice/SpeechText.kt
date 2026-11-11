package com.flowfolio.core.voice

/** One speakable clause or sentence with the pause (in milliseconds) that should follow it. */
data class SpeechSegment(val text: String, val pauseMs: Int)

internal enum class BlockKind {
    TITLE,
    SUBTITLE,
    PARAGRAPH
}

internal data class StructuredBlock(val text: String, val kind: BlockKind, val endsSentence: Boolean)

/**
 * Normalizes reflowed and PDF book text for speech: resolves curly quotes, PDFium (`\u0002`), soft (`\u00AD`) and
 * hard line-break hyphenation, strips running headers/folios, and structures titles, subtitles, paragraphs, and
 * cross-page mid-sentence continuations with natural narration pauses.
 */
@Suppress("TooManyFunctions")
object SpeechText {
    const val TITLE_PAUSE_MS = 480
    const val SUBTITLE_PAUSE_MS = 340
    const val PARAGRAPH_PAUSE_MS = 320
    const val SENTENCE_PAUSE_MS = 150
    const val CLAUSE_PAUSE_MS = 65
    const val MID_SENTENCE_PAGE_PAUSE_MS = 0

    fun normalize(text: String): String = joinHyphenatedWords(cleanQuotesAndDashes(text))
        .replace('\n', ' ')
        .replace(WHITESPACE, " ")
        .trim()

    /**
     * Breaks [rawText] into speakable segments with structural pauses:
     * - [TITLE_PAUSE_MS] after a title/chapter heading
     * - [SUBTITLE_PAUSE_MS] after a subtitle/section heading
     * - [PARAGRAPH_PAUSE_MS] at the end of a paragraph before the next paragraph
     * - [SENTENCE_PAUSE_MS] between sentences within a paragraph
     * - [MID_SENTENCE_PAGE_PAUSE_MS] (`0 ms`) at the end of the page when the page ends mid-sentence.
     */
    fun segments(rawText: String): List<SpeechSegment> {
        val blocks = extractBlocks(rawText)
        if (blocks.isEmpty()) return emptyList()
        val out = ArrayList<SpeechSegment>(blocks.size * 2)
        for ((blockIndex, block) in blocks.withIndex()) {
            val isLastBlock = blockIndex == blocks.lastIndex
            appendBlockSegments(block, isLastBlock, out)
        }
        return out
    }

    /**
     * Stitches a word hyphenated across the boundary between [currentPageRaw] and [nextPageRaw] so the word is
     * spoken whole on the current page and not repeated at the top of the next page.
     */
    @Suppress("ReturnCount")
    fun stitchCrossPage(currentPageRaw: String, nextPageRaw: String?): Pair<String, String?> {
        if (nextPageRaw.isNullOrBlank()) return currentPageRaw to nextPageRaw
        val curLines = stripEdgeFolios(cleanQuotesAndDashes(currentPageRaw).lines().map { it.trim() })
        val nextLines = stripEdgeFolios(cleanQuotesAndDashes(nextPageRaw).lines().map { it.trim() })
        val lastLine = curLines.lastOrNull { it.isNotEmpty() } ?: return currentPageRaw to nextPageRaw
        val firstNextLine = nextLines.firstOrNull { it.isNotEmpty() } ?: return currentPageRaw to nextPageRaw
        val match = TRAILING_HYPHEN_WORD.find(lastLine) ?: return currentPageRaw to nextPageRaw
        val contMatch = LEADING_CONTINUATION_WORD.find(firstNextLine) ?: return currentPageRaw to nextPageRaw
        val stem = match.groupValues[1]
        val suffix = contMatch.groupValues[1]
        val updatedCur = curLines.toMutableList()
        val lastIdx = updatedCur.indexOfLast { it.isNotEmpty() }
        updatedCur[lastIdx] = lastLine.substring(0, match.range.first) + stem + suffix
        val updatedNext = nextLines.toMutableList()
        val firstIdx = updatedNext.indexOfFirst { it.isNotEmpty() }
        updatedNext[firstIdx] = firstNextLine.substring(contMatch.range.last + 1).trimStart()
        return updatedCur.joinToString("\n") to updatedNext.joinToString("\n")
    }

    /**
     * [pageRaw] as it is read when narration *starts* on it: a word the previous page began ("informa-" / "tion")
     * is said whole at the top ("information"), not as a fragment.
     */
    fun startingOn(pageRaw: String, previousPageRaw: String?): String {
        val (previous, page) = stitchCrossPage(previousPageRaw.orEmpty(), pageRaw)
        if (page == null || page == pageRaw) return pageRaw
        val word = previous.trimEnd().split(' ', '\n').last()
        return "$word $page"
    }

    /**
     * [pageRaw] as it is read when narration *continues* onto it from the previous page, which already said the
     * word hyphenated across the break: the fragment at the top is left out, not said a second time.
     */
    fun continuingOnto(pageRaw: String, previousPageRaw: String?): String =
        stitchCrossPage(previousPageRaw.orEmpty(), pageRaw).second ?: pageRaw

    private fun cleanQuotesAndDashes(text: String): String = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace('\u2018', '\'')
        .replace('\u2019', '\'')
        .replace("\u201C", "")
        .replace("\u201D", "")
        .replace("\"", "")
        .replace('\u2014', ' ')
        .replace('\u00A0', ' ')
        .replace("\u2026", "...")

    private fun joinHyphenatedWords(text: String): String = text
        .replace(SOFT_OR_PDF_HYPHEN_BREAK, "$1$2")
        .replace("\u0002", "")
        .replace("\u00AD", "")
        .replace(LINE_BREAK_HYPHEN, "$1$2")
        .replace('\u2013', '-')
        .replace('\u2010', '-')
        .replace('\u2011', '-')
        .replace('\u2012', '-')
        .replace(SPACED_HYPHEN_WORD, "$1$2")

    internal fun extractBlocks(rawText: String): List<StructuredBlock> {
        val joined = joinHyphenatedWords(cleanQuotesAndDashes(rawText))
        val rawLines = stripEdgeFolios(joined.split('\n').map { it.replace(INLINE_SPACES, " ").trim() })
        val maxLineLen = rawLines.maxOfOrNull { it.length } ?: 0
        val blocks = ArrayList<StructuredBlock>()
        val paragraph = StringBuilder()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                val pText = paragraph.toString().trim()
                val ends = TERMINAL_PUNCT.containsMatchIn(pText)
                blocks += StructuredBlock(pText, BlockKind.PARAGRAPH, endsSentence = ends)
                paragraph.setLength(0)
            }
        }

        for (i in rawLines.indices) {
            val line = rawLines[i]
            if (line.isEmpty()) {
                flushParagraph()
                continue
            }
            val nextNonEmpty = rawLines.subList(i + 1, rawLines.size).firstOrNull { it.isNotEmpty() }
            val heading = if (paragraph.isEmpty()) {
                headingKind(line, blocks.isEmpty(), blocks.lastOrNull()?.kind, nextNonEmpty)
            } else {
                null
            }
            if (heading != null) {
                blocks += StructuredBlock(line, heading, endsSentence = true)
            } else {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line)
                if (endsParagraphLine(line, nextNonEmpty, maxLineLen)) flushParagraph()
            }
        }
        flushParagraph()
        return blocks
    }

    private fun headingKind(line: String, isFirstBlock: Boolean, prevKind: BlockKind?, nextLine: String?): BlockKind? =
        when {
            isTitleLine(line, isFirstBlock, nextLine) ->
                if (prevKind == BlockKind.TITLE) BlockKind.SUBTITLE else BlockKind.TITLE

            isSubtitleLine(line, prevKind, nextLine) -> BlockKind.SUBTITLE

            else -> null
        }

    private fun stripEdgeFolios(lines: List<String>): List<String> {
        val nonBlankCount = lines.count { it.isNotEmpty() }
        if (nonBlankCount <= 1) return lines
        val out = lines.toMutableList()
        val firstIdx = out.indexOfFirst { it.isNotEmpty() }
        if (firstIdx >= 0 && isRunningHeaderOrFolio(out[firstIdx])) {
            out[firstIdx] = ""
        }
        val secondIdx = out.indexOfFirst { it.isNotEmpty() }
        if (secondIdx > firstIdx && out.count { it.isNotEmpty() } > 1 && isBareFolio(out[secondIdx])) {
            out[secondIdx] = ""
        }
        val lastIdx = out.indexOfLast { it.isNotEmpty() }
        if (lastIdx > secondIdx && isRunningHeaderOrFolio(out[lastIdx])) {
            out[lastIdx] = ""
        }
        return out
    }

    private fun isBareFolio(line: String): Boolean = BARE_FOLIO.matches(line)

    private fun isRunningHeaderOrFolio(line: String): Boolean {
        val eligibleHeader = line.length <= MAX_HEADER_CHARS &&
            !TERMINAL_SENTENCE_PUNCT.containsMatchIn(line) &&
            !TITLE_PREFIX.containsMatchIn(line)
        return BARE_FOLIO.matches(line) ||
            (eligibleHeader && (LEADING_FOLIO_HEADER.matches(line) || TRAILING_FOLIO_HEADER.matches(line)))
    }

    private fun isHeadingCandidate(line: String): Boolean = line.length in MIN_HEADING_CHARS..MAX_HEADING_CHARS &&
        line.any { it.isLetter() } &&
        !CONTINUATION_END.containsMatchIn(line) &&
        !endsWithFunctionWord(line)

    private fun isTitleLine(line: String, isFirstBlock: Boolean, nextLine: String?): Boolean {
        if (!isHeadingCandidate(line)) return false
        val prefixed = TITLE_PREFIX.containsMatchIn(line) && !line.endsWith('.')
        val letters = line.filter { it.isLetter() }
        val allCaps = letters.length >= MIN_CAPS_LETTERS && letters.all { it.isUpperCase() }
        val shortFirst = isFirstBlock && !TERMINAL_PUNCT.containsMatchIn(line) && line[0].isUpperCase() &&
            line.length <= MAX_SHORT_TITLE_CHARS && contrastsWithNext(line, nextLine, HEADING_CONTRAST_CHARS)
        return prefixed || allCaps || shortFirst
    }

    private fun isSubtitleLine(line: String, prevKind: BlockKind?, nextLine: String?): Boolean {
        if (!isHeadingCandidate(line) || TERMINAL_PUNCT.containsMatchIn(line)) return false
        val numberedSection = SECTION_NUMBER_PREFIX.containsMatchIn(line)
        val startsHeading = line[0].isUpperCase() || numberedSection
        val precedesLong = line.length <= MAX_SHORT_TITLE_CHARS &&
            contrastsWithNext(line, nextLine, SUBTITLE_CONTRAST_CHARS)
        return startsHeading && (prevKind == BlockKind.TITLE || numberedSection || precedesLong)
    }

    private fun contrastsWithNext(line: String, nextLine: String?, minExtraChars: Int): Boolean =
        nextLine != null && nextLine[0].isUpperCase() && nextLine.length >= line.length + minExtraChars

    private fun endsParagraphLine(line: String, nextLine: String?, maxLineLen: Int): Boolean {
        val nextOpens = nextLine?.firstOrNull()?.let { it.isUpperCase() || it.isDigit() } == true
        if (!TERMINAL_PUNCT.containsMatchIn(line) || nextLine == null || !nextOpens) return false
        val epubBlockStyle = line.length > EPUB_PARAGRAPH_MIN_CHARS || nextLine.length > EPUB_PARAGRAPH_MIN_CHARS
        val shortPdfTail = maxLineLen >= MIN_PDF_LINE_CHARS &&
            line.length <= (maxLineLen * SHORT_TAIL_RATIO).toInt()
        return epubBlockStyle || shortPdfTail
    }

    private fun endsWithFunctionWord(line: String): Boolean {
        val lastWord = line.substringAfterLast(' ').lowercase().trim('.', ',', ';', ':', '-', '\'')
        return lastWord in TRAILING_FUNCTION_WORDS
    }

    private fun appendBlockSegments(block: StructuredBlock, isLastBlock: Boolean, out: MutableList<SpeechSegment>) {
        if (block.kind == BlockKind.TITLE || block.kind == BlockKind.SUBTITLE) {
            val cleaned = cleanForLexicon(block.text)
            if (cleaned.any { it.isLetterOrDigit() }) {
                val pause = if (block.kind == BlockKind.TITLE) TITLE_PAUSE_MS else SUBTITLE_PAUSE_MS
                out += SpeechSegment(cleaned, pause)
            }
            return
        }
        val rawSentences = splitSentences(expandAbbreviations(block.text))
        for ((sIdx, sentence) in rawSentences.withIndex()) {
            val isLastSentenceInBlock = sIdx == rawSentences.lastIndex
            val sentenceEndPause = when {
                isLastBlock && isLastSentenceInBlock && !block.endsSentence -> MID_SENTENCE_PAGE_PAUSE_MS
                isLastSentenceInBlock -> PARAGRAPH_PAUSE_MS
                else -> SENTENCE_PAUSE_MS
            }
            appendSentenceSegments(sentence, sentenceEndPause, out)
        }
    }

    private fun appendSentenceSegments(sentence: String, sentenceEndPause: Int, out: MutableList<SpeechSegment>) {
        val clauses = if (sentence.length > MAX_SEGMENT_CHARS) {
            splitClauses(sentence, MAX_SEGMENT_CHARS)
        } else {
            listOf(sentence)
        }
        for ((clauseIdx, clause) in clauses.withIndex()) {
            val clauseEndPause = if (clauseIdx == clauses.lastIndex) sentenceEndPause else CLAUSE_PAUSE_MS
            appendClausePhrases(clause, clauseEndPause, out)
        }
    }

    private fun appendClausePhrases(clause: String, clauseEndPause: Int, out: MutableList<SpeechSegment>) {
        val phrases = if (clause.length > MAX_SEGMENT_CHARS) {
            splitLongClause(clause, MAX_SEGMENT_CHARS)
        } else {
            listOf(clause)
        }
        for ((phraseIdx, phrase) in phrases.withIndex()) {
            val cleaned = cleanForLexicon(phrase)
            if (cleaned.any { it.isLetterOrDigit() }) {
                val pause = if (phraseIdx < phrases.lastIndex) CLAUSE_PAUSE_MS else clauseEndPause
                out += SpeechSegment(cleaned, pause)
            }
        }
    }

    /** "e.g." is "for example", "Dr." is "Doctor": said in full, and never taken for the end of a sentence. */
    internal fun expandAbbreviations(text: String): String =
        ABBREVIATIONS.fold(text) { acc, (pattern, words) -> pattern.replace(acc, words) }

    private fun splitSentences(text: String): List<String> =
        SENTENCE_SPLIT.split(text).map { it.trim() }.filter { it.isNotEmpty() }

    private fun splitClauses(sentence: String, maxChars: Int): List<String> {
        val rawParts = CLAUSE_SPLIT.split(sentence).map { it.trim() }.filter { it.isNotEmpty() }
        if (rawParts.size <= 1) return listOf(sentence)
        val merged = ArrayList<String>(rawParts.size)
        var current = ""
        for (part in rawParts) {
            current = when {
                current.isEmpty() -> part

                current.length + 2 + part.length <= maxChars -> "$current, $part"

                else -> {
                    merged += current
                    part
                }
            }
        }
        if (current.isNotEmpty()) merged += current
        return merged
    }

    private fun splitLongClause(clause: String, maxChars: Int): List<String> {
        val words = clause.split(' ').filter { it.isNotEmpty() }
        val result = ArrayList<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidateLen = if (current.isEmpty()) word.length else current.length + 1 + word.length
            val isNaturalBreak = word.lowercase() in PHRASE_BREAK_WORDS && current.length >= TARGET_PHRASE_CHARS
            val shouldBreak = current.length >= MIN_PHRASE_CHARS && (candidateLen > maxChars || isNaturalBreak)
            if (shouldBreak) {
                result += current.toString()
                current = StringBuilder(word)
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(word)
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    internal fun cleanForLexicon(text: String): String = SpeechNumbers.words(
        text.replace(REPLACEMENT_BETWEEN_LETTERS, "$1 $2")
    )
        .replace(NON_LEXICON_PUNCT, " ")
        .replace(STRAY_APOSTROPHE, " ")
        .replace(WHITESPACE, " ")
        .trim()

    private const val MAX_SEGMENT_CHARS = 150
    private const val TARGET_PHRASE_CHARS = 95
    private const val MIN_PHRASE_CHARS = 60
    private const val MIN_HEADING_CHARS = 2
    private const val MAX_HEADING_CHARS = 85
    private const val MAX_SHORT_TITLE_CHARS = 65
    private const val MIN_CAPS_LETTERS = 3
    private const val HEADING_CONTRAST_CHARS = 15
    private const val SUBTITLE_CONTRAST_CHARS = 25
    private const val MAX_HEADER_CHARS = 85
    private const val EPUB_PARAGRAPH_MIN_CHARS = 95
    private const val MIN_PDF_LINE_CHARS = 48
    private const val SHORT_TAIL_RATIO = 0.84

    private val PHRASE_BREAK_WORDS = setOf(
        "and", "or", "but", "because", "which", "that", "while", "when", "where",
        "whether", "although", "however", "therefore"
    )

    private val TRAILING_FUNCTION_WORDS = setOf(
        "the", "a", "an", "to", "of", "in", "on", "for", "with", "and", "or", "but",
        "that", "by", "from", "as", "at", "into", "is", "was", "are", "were", "be",
        "been", "have", "has", "had", "can", "will", "would", "should", "could", "not"
    )

    private val SOFT_OR_PDF_HYPHEN_BREAK = Regex("(\\p{L})\\s*[\\u0002\\u00AD]\\s*(?:\\n\\s*)?(\\p{L})")
    private val LINE_BREAK_HYPHEN = Regex("(\\p{L})\\s*[-\\u2010\\u2011\\u2012\\u2013]\\s*\\n\\s*(\\p{L})")
    private val SPACED_HYPHEN_WORD = Regex("(\\p{L}{2,})[-\\u2010\\u2011]\\s+(?!(?:and|or|to|nor)\\b)(\\p{Ll}+)")
    private val TRAILING_HYPHEN_WORD = Regex("(\\p{L}{2,})[-\\u2010\\u2011\\u00AD\\u0002]$")
    private val LEADING_CONTINUATION_WORD = Regex("^(\\p{Ll}+)\\b")
    private val BARE_FOLIO = Regex("^([0-9]{1,4}|[ivxlcdm]{1,7})$", RegexOption.IGNORE_CASE)
    private val LEADING_FOLIO_HEADER = Regex("^\\d{1,4}\\s+\\p{Lu}.*$")
    private val TRAILING_FOLIO_HEADER = Regex("^.*\\p{L}\\S*\\s+\\d{1,4}$")
    private val TITLE_PREFIX = Regex(
        "^(?:chapter|part|book|section|appendix|prologue|epilogue|preface|foreword)\\b",
        RegexOption.IGNORE_CASE
    )
    private val SECTION_NUMBER_PREFIX = Regex("^\\d+(?:\\.\\d+)+\\s+\\p{Lu}")
    private val TERMINAL_PUNCT = Regex("[.!?:][\"'”’)]?$")
    private val TERMINAL_SENTENCE_PUNCT = Regex("[.!?][\"'”’)]?$")
    private val CONTINUATION_END = Regex("[,;:\\-]$")
    private val INLINE_SPACES = Regex("[ \\t]+")
    private val WHITESPACE = Regex("\\s+")
    private val SENTENCE_SPLIT = Regex("(?<=[.!?])(?<!(?:^|\\s)\\p{Lu}\\.)\\s+|\\s*[;:]\\s+")

    private val ABBREVIATIONS: List<Pair<Regex, String>> = listOf(
        Regex("\\be\\.\\s?g\\.", RegexOption.IGNORE_CASE) to "for example",
        Regex("\\bi\\.\\s?e\\.", RegexOption.IGNORE_CASE) to "that is",
        Regex("\\betc\\.(?=\\s+\\p{Lu}|\\s*$)") to "et cetera.",
        Regex("\\betc\\.") to "et cetera",
        Regex("\\bvs\\.") to "versus",
        Regex("\\bDr\\.(?=\\s)") to "Doctor",
        Regex("\\bMrs\\.(?=\\s)") to "Missus",
        Regex("\\bMr\\.(?=\\s)") to "Mister",
        Regex("\\bMs\\.(?=\\s)") to "Miz",
        Regex("\\bProf\\.(?=\\s)") to "Professor",
        Regex("\\bJr\\.") to "Junior",
        Regex("\\bapprox\\.", RegexOption.IGNORE_CASE) to "approximately",
        Regex("\\bcf\\.") to "compare",
        Regex("\\bFig\\.(?=\\s*\\d)") to "Figure",
        Regex("\\bNo\\.(?=\\s*\\d)") to "Number",
        Regex("\\bpp\\.(?=\\s*\\d)") to "pages",
        Regex("\\bp\\.(?=\\s*\\d)") to "page",
        Regex("\\bch\\.(?=\\s*\\d)", RegexOption.IGNORE_CASE) to "chapter",
        Regex("\\bvol\\.(?=\\s*\\d)", RegexOption.IGNORE_CASE) to "volume"
    )
    private val CLAUSE_SPLIT = Regex(",\\s+")
    private val REPLACEMENT_BETWEEN_LETTERS = Regex("(\\p{L})\uFFFD(\\p{L})")
    private val NON_LEXICON_PUNCT = Regex("[^\\p{L}\\p{N}'.,!?;:\\s]+")
    private val STRAY_APOSTROPHE = Regex("(?<!\\p{L})'|'(?!\\p{L})")
}
