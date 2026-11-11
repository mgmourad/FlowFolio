package com.flowfolio.core.voice

import java.io.File

/**
 * The words a voice can say: the keys of its lexicons, lowercase. sherpa-onnx's Kokoro and Piper look every word up in
 * their lexicons and silently skip any they don't find, so [SpeechWords] needs this list to rephrase the rest.
 *
 * Kokoro's lexicons hold about 520,000 words. As separate strings they would cost some 30 MB of the reader's memory
 * (ADR-0013), so they are packed: one sorted string of every word end to end, and where each one starts. A lookup is
 * a binary search over it that allocates nothing.
 */
class Vocabulary(words: Collection<String>) {
    private val text: String
    private val starts: IntArray

    init {
        val sorted = words.asSequence()
            .map { it.lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .toList()
        val builder = StringBuilder(sorted.sumOf { it.length })
        starts = IntArray(sorted.size + 1)
        sorted.forEachIndexed { i, word ->
            starts[i] = builder.length
            builder.append(word)
        }
        starts[sorted.size] = builder.length
        text = builder.toString()
    }

    val size: Int get() = starts.size - 1

    fun knows(word: String): Boolean {
        val key = word.lowercase()
        var low = 0
        var high = size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val order = compare(mid, key)
            when {
                order < 0 -> low = mid + 1
                order > 0 -> high = mid - 1
                else -> return true
            }
        }
        return false
    }

    /** The order of word [index] against [key], as [String.compareTo] would give it. */
    private fun compare(index: Int, key: String): Int {
        val start = starts[index]
        val length = starts[index + 1] - start
        val shared = minOf(length, key.length)
        for (i in 0 until shared) {
            val difference = text[start + i] - key[i]
            if (difference != 0) return difference
        }
        return length - key.length
    }

    companion object {
        /** Reads the words (the text before the first space of each line) of every lexicon file that exists. */
        fun read(files: List<File>): Vocabulary {
            val words = ArrayList<String>()
            for (file in files) {
                if (!file.isFile) continue
                file.useLines { lines ->
                    lines.forEach { line ->
                        val space = line.indexOf(' ')
                        if (space > 0) words += line.substring(0, space)
                    }
                }
            }
            return Vocabulary(words)
        }
    }
}
