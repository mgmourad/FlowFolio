package com.flowfolio.core.voice

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** FlowFolio's lexicon only uses symbols Kokoro's model knows; an unknown one would be dropped from the word. */
class FlowFolioLexiconTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun everyPhonemeIsAKokoroToken() {
        for ((word, phonemes) in FlowFolioLexicon.entries()) {
            for (symbol in phonemes.split(' ')) {
                assertTrue("'$symbol' in '$word' is not a Kokoro token", symbol in KOKORO_TOKENS)
            }
        }
    }

    @Test
    fun theArticleIsASchwaAndTheLetterKeepsItsName() {
        val entries = FlowFolioLexicon.entries().toMap()
        assertEquals("ə", entries["a"])
        assertEquals("ˈ A", entries[SpeechWords.LETTER_A])
    }

    @Test
    fun namesHaveTheirPluralsAndLettersTheirs() {
        val entries = FlowFolioLexicon.entries().toMap()
        assertEquals("k ˌ u b ə ɹ n ˈ ɛ t i z ᵻ z", entries["kuberneteses"])
        assertEquals("ˈ ɛ m z", entries["${SpeechWords.PLURAL_LETTER}m"])
        assertEquals("ˈ I z", entries["${SpeechWords.PLURAL_LETTER}i"])
    }

    @Test
    fun theFileIsWrittenOnceAndReadable() {
        val file = FlowFolioLexicon.write(tmp.root)
        assertEquals(FlowFolioLexicon.FILE_NAME, file.name)
        assertTrue(Vocabulary.read(listOf(file)).knows("Kubernetes"))
        assertEquals(file.length(), FlowFolioLexicon.write(tmp.root).length())
        assertTrue(File(tmp.root, "${FlowFolioLexicon.FILE_NAME}.tmp").exists().not())
    }

    private companion object {
        /** `kokoro-int8-multi-lang-v1_0/tokens.txt`, every symbol but the space. */
        val KOKORO_TOKENS = (
            "; : , . ! ? — … \" ( ) “ ” ̃ ʣ ʥ ʦ ʨ ᵝ ꭧ A I O Q S T W Y ᵊ " +
                "a b c d e f h i j k l m n o p q r s t u v w x " +
                "y z ɑ ɐ ɒ æ β ɔ ɕ ç ɖ ð ʤ ə ɚ ɛ ɜ ɟ ɡ ɥ ɨ ɪ ʝ ɯ ɰ ŋ ɳ ɲ ɴ ø ɸ θ œ ɹ ɾ ɻ ʁ ɽ ʂ ʃ ʈ ʧ ʊ ʋ ʌ ɣ ɤ " +
                "χ ʎ ʒ ʔ ˈ ˌ ː ʰ ʲ ↓ → ↗ ↘ ᵻ"
            ).split(' ').toSet()
    }
}
