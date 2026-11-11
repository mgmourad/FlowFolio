package com.flowfolio.core.voice

/**
 * Numbers, symbols and links as words (owner, 2026-11-10). The voice's lexicon has no digits, so "2023", "1st" and
 * "$10,000" were skipped or read as stray pieces ("ten, zero"). [words] runs before punctuation is stripped, so `%`,
 * `$` and `&` still carry their meaning.
 */
// One function per kind of number people say differently (cardinal, ordinal, year, decimal, version).
@Suppress("TooManyFunctions")
object SpeechNumbers {
    /** [text] with links, currency, percentages, ranges, ordinals, years, decimals, versions and symbols in words. */
    fun words(text: String): String = text
        .replace(URL) { link(it.groupValues[1]) }
        .replace(EMAIL) { "${it.groupValues[1]} at ${dotted(it.groupValues[2])}" }
        .replace(CURRENCY) { m ->
            val scale = m.groupValues[SCALE_GROUP].trim()
            val unit = CURRENCY_NAMES.getValue(m.groupValues[1])
            "${number(m.groupValues[2])}${if (scale.isEmpty()) "" else " $scale"} $unit"
        }
        .replace(PERCENT) { "${number(it.groupValues[1])} percent" }
        .replace(RANGE) { "${it.groupValues[1]} to ${it.groupValues[2]}" }
        .replace(ORDINAL) { m -> m.groupValues[1].toLongOrNull()?.let { ordinal(it) } ?: m.value }
        .replace(VERSION) { m -> m.value.split('.').joinToString(" point ") { digitsOf(it) } }
        .replace(NUMBER) { number(it.value) }
        .replace(AMPERSAND, " and ")
        .replace(PLUS, " plus ")
        .replace(HASH_NUMBER, "number ")
        .replace(DEGREES, " degrees")

    /** A whole number in words: 2,500,000 is "two million five hundred thousand". */
    fun cardinal(n: Long): String = when {
        n == 0L -> "zero"
        n < 0 -> "minus ${cardinal(-n)}"
        else -> positive(n)
    }

    private fun positive(n: Long): String {
        val parts = ArrayList<String>()
        var rest = n
        for ((scale, name) in SCALES) {
            if (rest >= scale) {
                parts += "${belowThousand((rest / scale).toInt())} $name"
                rest %= scale
            }
        }
        if (rest > 0) parts += belowThousand(rest.toInt())
        return parts.joinToString(" ")
    }

    fun ordinal(n: Long): String {
        val words = cardinal(n).split(' ').toMutableList()
        val last = words.last()
        words[words.lastIndex] = ORDINAL_WORDS[last] ?: when {
            last.endsWith("y") -> last.dropLast(1) + "ieth"
            else -> last + "th"
        }
        return words.joinToString(" ")
    }

    /** A year as people say it: 1999 "nineteen ninety nine", 2023 "twenty twenty three", 2005 "two thousand five". */
    fun year(n: Int): String {
        val high = n / HUNDRED
        val low = n % HUNDRED
        return when {
            n in TWO_THOUSANDS -> cardinal(n.toLong())
            low == 0 -> "${belowThousand(high)} hundred"
            low < TEN -> "${belowThousand(high)} oh ${belowThousand(low)}"
            else -> "${belowThousand(high)} ${belowThousand(low)}"
        }
    }

    private fun number(raw: String): String {
        val clean = raw.replace(",", "")
        return if ('.' in clean) decimal(clean) else whole(raw, clean)
    }

    private fun decimal(clean: String): String {
        val (whole, fraction) = clean.split('.', limit = 2)
        val head = whole.toLongOrNull()?.let { cardinal(it) } ?: whole
        return if (fraction.isEmpty()) head else "$head point ${digitsOf(fraction)}"
    }

    private fun whole(raw: String, clean: String): String {
        val value = clean.toLongOrNull()
        val isYear = value != null && raw.length == YEAR_DIGITS && ',' !in raw && value in YEARS
        return when {
            value == null -> raw
            isYear -> year(value.toInt())
            else -> cardinal(value)
        }
    }

    private fun digitsOf(run: String): String = if (run.length == 1 || run.startsWith('0')) {
        run.map { cardinal((it - '0').toLong()) }.joinToString(" ")
    } else {
        run.toLongOrNull()?.let { cardinal(it) } ?: run
    }

    private fun belowThousand(n: Int): String {
        val hundreds = n / HUNDRED
        val rest = n % HUNDRED
        val tail = when {
            rest == 0 -> ""
            rest < TWENTY -> ONES[rest]
            rest % TEN == 0 -> TENS[rest / TEN]
            else -> "${TENS[rest / TEN]} ${ONES[rest % TEN]}"
        }
        return when {
            hundreds == 0 -> tail
            tail.isEmpty() -> "${ONES[hundreds]} hundred"
            else -> "${ONES[hundreds]} hundred $tail"
        }
    }

    /** A link as its address: "https://learn.hashicorp.com/x" is "learn dot hashicorp dot com". */
    private fun link(host: String): String = dotted(host.removePrefix("www."))

    private fun dotted(host: String): String = host.trimEnd('.').split('.').joinToString(" dot ")

    private const val TEN = 10
    private const val TWENTY = 20
    private const val HUNDRED = 100
    private const val YEAR_DIGITS = 4
    private const val SCALE_GROUP = 3

    /** 2000 to 2009 are said "two thousand five", not "twenty oh five". */
    private val TWO_THOUSANDS = 2000..2009
    private val YEARS = 1100L..2099L

    private val SCALES = listOf(
        1_000_000_000_000L to "trillion",
        1_000_000_000L to "billion",
        1_000_000L to "million",
        1_000L to "thousand"
    )

    private val ONES = arrayOf(
        "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen"
    )
    private val TENS = arrayOf(
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    )
    private val ORDINAL_WORDS = mapOf(
        "one" to "first",
        "two" to "second",
        "three" to "third",
        "five" to "fifth",
        "eight" to "eighth",
        "nine" to "ninth",
        "twelve" to "twelfth"
    )
    private val CURRENCY_NAMES = mapOf("$" to "dollars", "€" to "euros", "£" to "pounds")

    private val URL = Regex("\\b(?:https?://|www\\.)([\\w-]+(?:\\.[\\w-]+)+)\\S*", RegexOption.IGNORE_CASE)
    private val EMAIL = Regex("\\b([\\w.+-]+)@([\\w-]+(?:\\.[\\w-]+)+)\\b")
    private val CURRENCY = Regex("([$€£])\\s?(\\d[\\d,]*(?:\\.\\d+)?)(\\s+(?:thousand|million|billion|trillion))?")
    private val PERCENT = Regex("(\\d[\\d,]*(?:\\.\\d+)?)\\s?%")
    private val RANGE = Regex("(?<![\\d.])(\\d+)\\s?-\\s?(\\d+)(?![\\d.])")
    private val ORDINAL = Regex("\\b(\\d+)(?:st|nd|rd|th)\\b", RegexOption.IGNORE_CASE)
    private val VERSION = Regex("\\b\\d+(?:\\.\\d+){2,}\\b")
    private val NUMBER =
        Regex("(?<![\\p{L}\\d])\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|(?<![\\p{L}\\d.])\\d+(?:\\.\\d+)?(?![\\p{L}\\d])")
    private val AMPERSAND = Regex("\\s*&\\s*")
    private val PLUS = Regex("(?<=\\S)\\+|\\s\\+\\s")
    private val HASH_NUMBER = Regex("#(?=\\d)")
    private val DEGREES = Regex("\\s?°")
}
