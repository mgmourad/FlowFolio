package com.flowfolio.core.conversion.ocr

import java.text.Normalizer

/**
 * Character error rate between a proofread reference and an OCR reading, the way the spike measured it
 * (`rescore.py`): NFKC, line-end hyphens joined, dotted leaders dropped, Arabic vowel marks and letter forms unified,
 * and the words compared without spaces. A normalized Levenshtein distance over the longer string.
 */
object Cer {
    fun of(truth: String, read: String): Float {
        val a = words(truth).joinToString("")
        val b = words(read).joinToString("")
        if (a.isEmpty() && b.isEmpty()) return 0f
        return levenshtein(a, b).toFloat() / maxOf(a.length, b.length)
    }

    /** Words as [Recall] has them, but with the spike's Arabic letter-form unification (for CER the letters matter). */
    private fun words(t: String): List<String> = WORD.findAll(normalise(t)).map { it.value }.toList()

    private fun normalise(t: String): String = Normalizer.normalize(t, Normalizer.Form.NFKC)
        .replace(Regex("([\\p{L}\\p{N}])-\\s*\\n\\s*([\\p{L}\\p{N}])"), "$1$2")
        .replace(Regex("([\\p{L}\\p{N}])- ([\\p{L}\\p{N}])"), "$1$2")
        .replace(Regex("\\.{3,}|(?:\\. ){3,}|_{3,}"), " ")
        .replace(MARKS, "")
        .replace(ALEFS, "ا")
        .replace('ى', 'ي')
        .replace('ة', 'ه')

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                current[j] = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                )
            }
            val tmp = previous
            previous = current
            current = tmp
        }
        return previous[b.length]
    }

    private val MARKS = Regex("[\\u064B-\\u065F\\u0670\\u0640]")
    private val ALEFS = Regex("[إأآٱ]")
    private val WORD = Regex("[\\p{L}\\p{N}]+")
}
