package com.flowfolio.core.voice

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * How many words of a real book Narrator Mode would still skip: the book's text through [SpeechText] and
 * [SpeechWords] with Kokoro's real vocabulary. Skipped unless pointed at a book and a voice pack, so it never runs
 * in CI:
 *
 *     FLOWFOLIO_SPEECH_BOOK=book.epub FLOWFOLIO_KOKORO_DIR=kokoro-int8-multi-lang-v1_0 \
 *       ./gradlew :core:voice:testDebugUnitTest --tests '*SpeechCoverageTest*' -i
 */
class SpeechCoverageTest {
    @Test
    fun reportsWhatIsStillSkipped() {
        val book = System.getenv("FLOWFOLIO_SPEECH_BOOK")?.let(::File)
        val pack = System.getenv("FLOWFOLIO_KOKORO_DIR")?.let(::File)
        assumeTrue(book?.isFile == true && pack?.isDirectory == true)
        val inflected = VoiceEngine.ensureInflectedLexicon(pack!!)
        val own = FlowFolioLexicon.write(pack)
        val vocab = Vocabulary.read(listOfNotNull(File(pack, "lexicon-us-en.txt"), inflected, own))
        val before = Vocabulary.read(listOfNotNull(File(pack, "lexicon-us-en.txt"), inflected))

        var words = 0
        var skippedBefore = 0
        val skipped = HashMap<String, Int>()
        val rewrites = HashMap<Pair<String, String>, Int>()
        val samples = ArrayList<String>()
        for (page in pages(book!!)) {
            for (segment in SpeechText.segments(page)) {
                skippedBefore += TOKEN.findAll(segment.text).count { !before.knows(it.value) }
                val unknown = TOKEN.findAll(segment.text).map { it.value }.filter { !before.knows(it) }.toList()
                unknown.forEach { word -> rewrites.merge(word to SpeechWords.expand(word, vocab), 1, Int::plus) }
                val spoken = SpeechWords.expand(segment.text, vocab)
                if (unknown.isNotEmpty()) samples += spoken
                val tokens = TOKEN.findAll(spoken).map { it.value }.toList()
                words += tokens.size
                tokens.filter { !vocab.knows(it) }.forEach { skipped.merge(it, 1, Int::plus) }
            }
        }
        System.getenv("FLOWFOLIO_SPEECH_DUMP")?.let { path ->
            val owner = SpeechText.segments(OWNER_EXAMPLES).map { SpeechWords.expand(it.text, vocab) }
            File(path).writeText((owner + samples.take(SAMPLES_DUMPED)).joinToString("\n"))
        }
        val missed = skipped.values.sum()
        println("SPEECH words=$words skippedBefore=$skippedBefore skippedNow=$missed")
        println(
            "SPEECH rewrites: " +
                rewrites.entries.sortedByDescending { it.value }.take(REWRITES_SHOWN)
                    .joinToString(" | ") { "${it.key.first} -> ${it.key.second}" }
        )
        println("SPEECH still skipped: " + skipped.entries.sortedByDescending { it.value }.take(40))
    }

    /** The text of each chapter, paragraphs on their own lines, code left out (Narrator reads prose). */
    private fun pages(book: File): List<String> = ZipFile(book).use { zip ->
        zip.entries().toList().filter { it.name.endsWith(".xhtml") || it.name.endsWith(".html") }.map { entry ->
            val html = zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
            html.replace(CODE, " ")
                .replace(BLOCK_END, "\n\n")
                .replace(TAG, " ")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'")
                .replace("&quot;", "\"").replace("&nbsp;", " ")
        }
    }

    private companion object {
        const val REWRITES_SHOWN = 120
        const val SAMPLES_DUMPED = 30
        const val OWNER_EXAMPLES =
            "We deploy Kubernetes clusters to fight cybercrime. The CSA and NIST publish guidance. " +
                "Costs rose 45% to $10,000 in 2023, e.g. on GitHub's APIs. It is a book about DevOps."
        val TOKEN = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'’]*[\\p{L}\\p{N}]|[\\p{L}\\p{N}]")
        val CODE = Regex("<(pre|code)[^>]*>.*?</\\1>", RegexOption.DOT_MATCHES_ALL)
        val BLOCK_END = Regex("</(p|li|h[1-6]|div|aside)>")
        val TAG = Regex("<[^>]+>")
    }
}
