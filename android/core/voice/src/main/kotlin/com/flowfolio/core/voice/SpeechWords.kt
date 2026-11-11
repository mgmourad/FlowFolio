package com.flowfolio.core.voice

/**
 * Rephrases every word a voice does not know into words it does, so Narrator Mode never skips one (owner,
 * 2026-11-10). sherpa-onnx 2.0.1 looks each word up in the voice's lexicon and silently drops what it cannot find:
 * "Kubernetes", "CSA", "GitHub", "Docker's", about one word in twenty-two of a technical book.
 *
 * For each word the voice does not know, in order:
 * - a word with an apostrophe: a possessive sounds like its plural ("Docker's" as "Dockers"), "that's" is "that is";
 * - letters and digits are said apart ("IPv6": "I P v six");
 * - CamelCase is split ("GitHub": "Git Hub", "macOS": "mac O S");
 * - capitals are an acronym: spelled ("CSA": "C S A"), with its plural kept ("VMs"); a few are said as words;
 * - a compound is split into words the voice knows ("Dockerfile": "docker file");
 * - anything else is sounded out from the shortest run of known pieces, and spelled only as a last resort.
 *
 * Text in capitals throughout (a heading) is read as words, not as a row of acronyms.
 */
// One function per kind of word the voice cannot say; together they are the rule list in the class comment.
@Suppress("TooManyFunctions")
object SpeechWords {
    fun expand(text: String, vocab: Vocabulary): String {
        val shouting = isShouting(text)
        return TOKEN.replace(text) { match -> say(match.value, vocab, shouting) }
    }

    private fun isShouting(text: String): Boolean {
        val words = WORD.findAll(text).map { it.value }.filter { it.length > 1 }.toList()
        if (words.size < SHOUT_MIN_WORDS) return false
        return words.count { w -> w.all { !it.isLetter() || it.isUpperCase() } } >= words.size * SHOUT_SHARE
    }

    // The rules in the order the class comment lists them: an early return per rule reads more plainly than nesting.
    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    private fun say(token: String, vocab: Vocabulary, shouting: Boolean): String {
        if (token.any { it == '\'' || it == '’' }) return possessive(token.replace('’', '\''), vocab, shouting)
        if (token.any { it.isDigit() } && token.any { it.isLetter() }) {
            return LETTERS_OR_DIGITS.findAll(token).joinToString(" ") { part ->
                if (part.value[0].isDigit()) digits(part.value) else say(part.value, vocab, shouting)
            }
        }
        if (token.all { it.isDigit() }) return digits(token)
        val acronym = isAcronym(token)
        if (acronym && !shouting) return acronym(token, vocab)
        if (vocab.knows(token)) return token
        val parts = CAMEL.split(token).filter { it.isNotEmpty() }
        if (parts.size > 1) return parts.joinToString(" ") { say(it, vocab, shouting) }
        if (acronym) return acronym(token, vocab)
        // No vowels ("npm") or three letters or fewer ("cli"): letters, not a word.
        val letters = token.none { it.lowercaseChar() in VOWELS } || token.length <= MAX_SPELLED
        if (token.length > 1 && letters) return spell(token, vocab)
        return compound(token.lowercase(), vocab) ?: soundOut(token, vocab)
    }

    private fun possessive(token: String, vocab: Vocabulary, shouting: Boolean): String {
        val base = token.substringBefore('\'')
        return when {
            vocab.knows(token) -> token
            base.isEmpty() -> say(token.trim('\''), vocab, shouting)
            else -> contraction(base, token.substringAfter('\'').lowercase(), vocab, shouting)
        }
    }

    private fun contraction(base: String, suffix: String, vocab: Vocabulary, shouting: Boolean): String =
        when (suffix) {
            "s" -> when {
                base.lowercase() in IS_CONTRACTIONS -> "${say(base, vocab, shouting)} is"
                vocab.knows(base + "s") -> base + "s"
                else -> say(base, vocab, shouting)
            }

            "re" -> "${say(base, vocab, shouting)} are"

            "ll" -> "${say(base, vocab, shouting)} will"

            "ve" -> "${say(base, vocab, shouting)} have"

            "d" -> "${say(base, vocab, shouting)} would"

            "m" -> "${say(base, vocab, shouting)} am"

            else -> say(base, vocab, shouting)
        }

    /** All capitals (two to six letters), optionally with a plural `s`: "CSA", "VMs". */
    private fun isAcronym(token: String): Boolean {
        val core = if (token.length > 2 && token.endsWith('s')) token.dropLast(1) else token
        return core.length in 2..MAX_ACRONYM && core.all { it in 'A'..'Z' }
    }

    private fun acronym(token: String, vocab: Vocabulary): String {
        val plural = token.length > 2 && token.endsWith('s') && token.dropLast(1).all { it in 'A'..'Z' }
        val core = if (plural) token.dropLast(1) else token
        val lower = core.lowercase()
        // Said as a word: a known word of four letters or more ("NIST", "NASA") or a listed one ("RAM").
        val asWord = vocab.knows(lower) && (core.length >= SAID_AS_WORD_MIN || lower in SAID_AS_WORDS)
        if (asWord) return if (plural && vocab.knows(lower + "s")) core + "s" else core
        val letters = core.map { letter(it, vocab) }.toMutableList()
        if (plural) {
            val key = PLURAL_LETTER + core.last().lowercaseChar()
            if (vocab.knows(key)) letters[letters.lastIndex] = key
        }
        return letters.joinToString(" ")
    }

    private fun spell(token: String, vocab: Vocabulary): String =
        token.filter { it.isLetter() }.map { letter(it, vocab) }.joinToString(" ")

    /** A letter's name: "a" alone is the article to the voice, so the letter A has its own entry when there is one. */
    private fun letter(c: Char, vocab: Vocabulary): String =
        if (c.lowercaseChar() == 'a' && vocab.knows(LETTER_A)) LETTER_A else c.toString()

    private fun digits(run: String): String {
        val value = run.toLongOrNull()
        return if (value != null && run.length <= MAX_CARDINAL_DIGITS) {
            SpeechNumbers.cardinal(value)
        } else {
            run.map { SpeechNumbers.cardinal((it - '0').toLong()) }.joinToString(" ")
        }
    }

    /** [word] as known words of at least three letters (two for a few short ones), fewest pieces first. */
    internal fun compound(word: String, vocab: Vocabulary): String? {
        if (word.length < MIN_COMPOUND || !word.all { it in 'a'..'z' }) return null
        val best = arrayOfNulls<List<String>>(word.length + 1)
        best[0] = emptyList()
        for (end in 1..word.length) best[end] = bestEndingAt(word, end, best, vocab)
        return best[word.length]?.joinToString(" ")
    }

    /** The best split of `word[0, end)` whose last piece is a known word, from the best splits before it. */
    private fun bestEndingAt(word: String, end: Int, best: Array<List<String>?>, vocab: Vocabulary): List<String>? {
        var chosen: List<String>? = null
        for (start in (end - MAX_PIECE).coerceAtLeast(0) until end) {
            val prefix = best[start]
            val piece = word.substring(start, end)
            val fits = (piece.length >= MIN_PIECE || piece in SHORT_PIECES) && vocab.knows(piece)
            if (prefix != null && fits && prefix.size < MAX_PIECES) {
                val candidate = prefix + piece
                if (better(candidate, chosen)) chosen = candidate
            }
        }
        return chosen
    }

    /**
     * The last resort: known pieces of any length of two letters or more, fewest first, with single letters spoken by
     * name where nothing else fits. "Krief": "kri ef". Never empty, so nothing is skipped.
     */
    private fun soundOut(token: String, vocab: Vocabulary): String {
        val word = token.lowercase().filter { it in 'a'..'z' }
        if (word.isEmpty()) return token
        val cost = IntArray(word.length + 1) { Int.MAX_VALUE }
        val from = IntArray(word.length + 1)
        cost[0] = 0
        for (end in 1..word.length) {
            for (start in (end - MAX_PIECE).coerceAtLeast(0) until end) {
                val piece = word.substring(start, end)
                val step = when {
                    cost[start] == Int.MAX_VALUE -> null
                    piece.length >= 2 && vocab.knows(piece) -> PIECE_COST
                    piece.length == 1 -> LETTER_COST
                    else -> null
                }
                if (step != null && cost[start] + step < cost[end]) {
                    cost[end] = cost[start] + step
                    from[end] = start
                }
            }
        }
        val pieces = ArrayList<String>()
        var end = word.length
        while (end > 0) {
            val start = from[end]
            val piece = word.substring(start, end)
            pieces += if (piece.length == 1) letter(piece[0], vocab) else piece
            end = start
        }
        return pieces.asReversed().joinToString(" ")
    }

    private fun better(candidate: List<String>, current: List<String>?): Boolean = when {
        current == null -> true
        candidate.size != current.size -> candidate.size < current.size
        else -> candidate.minOf { it.length } > current.minOf { it.length }
    }

    /** The key FlowFolio's own lexicon gives the letter A (the bare "a" is the article). */
    const val LETTER_A = "zzlettera"

    /** Prefix of FlowFolio's own lexicon keys for a letter's plural ("VMs": "V" then "ems"). */
    const val PLURAL_LETTER = "zzplural"

    private const val MAX_ACRONYM = 6
    private const val MAX_SPELLED = 3
    private const val SAID_AS_WORD_MIN = 4
    private const val MIN_COMPOUND = 5
    private const val MIN_PIECE = 3
    private const val MAX_PIECE = 14
    private const val MAX_PIECES = 4
    private const val PIECE_COST = 3
    private const val LETTER_COST = 5
    private const val MAX_CARDINAL_DIGITS = 12
    private const val SHOUT_MIN_WORDS = 3
    private const val SHOUT_SHARE = 0.6

    private const val VOWELS = "aeiouy"

    /** Three-letter capitals said as words, not letters. */
    private val SAID_AS_WORDS = setOf("ram", "rom", "lan", "wan", "pin", "sim", "gif")

    private val SHORT_PIECES = setOf("in", "up", "on", "of", "to", "at", "by", "go", "no", "so", "it", "is", "as", "an")

    /** Words whose `'s` is "is" rather than a possessive. */
    private val IS_CONTRACTIONS = setOf(
        "that", "it", "here", "there", "what", "where", "who", "how", "he", "she", "this"
    )

    private val TOKEN = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'’]*[\\p{L}\\p{N}]|[\\p{L}\\p{N}]")
    private val WORD = Regex("\\p{L}+")
    private val LETTERS_OR_DIGITS = Regex("\\p{L}+|\\p{N}+")

    /** Lower to upper ("GitHub"), and the end of a run of capitals before a word ("HTTPServer": "HTTP Server"). */
    private val CAMEL = Regex("(?<=\\p{Ll})(?=\\p{Lu})|(?<=\\p{Lu})(?=\\p{Lu}\\p{Ll})")
}
