package com.flowfolio.core.voice

import java.io.File

/**
 * FlowFolio's own additions to Kokoro's lexicon (owner, 2026-11-10), loaded **last** so they win: sherpa-onnx keeps
 * the last lexicon's entry when two define the same word.
 *
 * - "a" is the article (/ə/): Kokoro's lexicon has only the letter name, so every "a book" was read "ay book".
 * - Technical names that books use and Kokoro's lexicon lacks, which no rule can sound out well ("Kubernetes").
 * - The letter A by name ([SpeechWords.LETTER_A]), and each letter's plural ([SpeechWords.PLURAL_LETTER]), for
 *   spelling acronyms ("VMs").
 *
 * Phonemes are Kokoro's (misaki) symbols, space-separated; every symbol must be in the model's `tokens.txt`
 * (`FlowFolioLexiconTest`). Plurals and possessives of the names are generated.
 */
object FlowFolioLexicon {
    const val FILE_NAME = "lexicon-flowfolio.txt"

    /** Writes the lexicon into [dir] when it is missing or differs (a few kilobytes) and returns it. */
    fun write(dir: File): File {
        val file = File(dir, FILE_NAME)
        val text = entries().joinToString("") { (word, phonemes) -> "$word $phonemes\n" }
        if (file.isFile && file.readText() == text) return file
        val tmp = File(dir, "$FILE_NAME.tmp")
        tmp.writeText(text)
        tmp.renameTo(file)
        return file
    }

    /** Every entry: the article, the names and their plurals, the letter A and the letters' plurals. */
    fun entries(): List<Pair<String, String>> = buildList {
        add("a" to ARTICLE_A)
        add(SpeechWords.LETTER_A to LETTERS.getValue('a'))
        for ((word, phonemes) in NAMES) {
            add(word to phonemes)
            add(plural(word) to "$phonemes ${pluralEnding(phonemes)}")
        }
        for ((letter, phonemes) in LETTERS) {
            add("${SpeechWords.PLURAL_LETTER}$letter" to "$phonemes ${pluralEnding(phonemes)}")
        }
    }

    private fun plural(word: String): String = if (word.endsWith('s') || word.endsWith('x')) "${word}es" else "${word}s"

    private fun pluralEnding(phonemes: String): String = when (phonemes.substringAfterLast(' ')) {
        "s", "z", "ʃ", "ʒ", "ʧ", "ʤ" -> "ᵻ z"
        "p", "t", "k", "f", "θ" -> "s"
        else -> "z"
    }

    private const val ARTICLE_A = "ə"

    /** The letters as Kokoro's lexicon names them (its single-letter entries). */
    private val LETTERS: Map<Char, String> = linkedMapOf(
        'a' to "ˈ A", 'b' to "b ˈ i", 'c' to "s ˈ i", 'd' to "d ˈ i", 'e' to "ˈ i", 'f' to "ˈ ɛ f", 'g' to "ʤ ˈ i",
        'h' to "ˈ A ʧ", 'i' to "ˈ I", 'j' to "ʤ ˈ A", 'k' to "k ˈ A", 'l' to "ˈ ɛ l", 'm' to "ˈ ɛ m", 'n' to "ˈ ɛ n",
        'o' to "ˈ O", 'p' to "p ˈ i", 'q' to "k j ˈ u", 'r' to "ˈ ɑ ɹ", 's' to "ˈ ɛ s", 't' to "t ˈ i",
        'u' to "j ˈ u", 'v' to "v ˈ i", 'w' to "d ˈ ʌ b ᵊ l j u", 'x' to "ˈ ɛ k s", 'y' to "w ˈ I", 'z' to "z ˈ i"
    )

    /** Names as they are said, for words Kokoro's lexicon lacks (from technical books; the owner's examples first). */
    private val NAMES: Map<String, String> = linkedMapOf(
        "kubernetes" to "k ˌ u b ə ɹ n ˈ ɛ t i z",
        "ansible" to "ˈ æ n s ə b ᵊ l",
        "jenkins" to "ʤ ˈ ɛ ŋ k ɪ n z",
        "kubectl" to "k j ˈ u b k ə n t ɹ ˌ O l",
        "nginx" to "ˈ ɛ n ʤ ɪ n ˌ ɛ k s",
        "yaml" to "j ˈ æ m ᵊ l",
        "json" to "ʤ ˈ A s ə n",
        "owasp" to "ˈ O w ɑ s p",
        "linux" to "l ˈ ɪ n ə k s",
        "ubuntu" to "ʊ b ˈ ʊ n t u",
        "grafana" to "ɡ ɹ ə f ˈ ɑ n ə",
        "chocolatey" to "ʧ ˈ ɑ k l ə t i",
        "packt" to "p ˈ æ k t",
        "oauth" to "ˈ O ˌ ɔ θ",
        "postgres" to "p ˈ O s t ɡ ɹ ˌ ɛ s",
        "postgresql" to "p ˈ O s t ɡ ɹ ˌ ɛ s k j u ˈ ɛ l",
        "mysql" to "m ˌ I ɛ s k j u ˈ ɛ l",
        "sqlite" to "ˌ ɛ s k j u ˈ ɛ l ˌ I t",
        "kafka" to "k ˈ ɑ f k ə",
        "redis" to "ɹ ˈ ɛ d ɪ s",
        "github" to "ɡ ˈ ɪ t h ˌ ʌ b",
        "gitlab" to "ɡ ˈ ɪ t l ˌ æ b",
        "devops" to "d ˈ ɛ v ˌ ɑ p s",
        "devsecops" to "d ˈ ɛ v s ˌ ɛ k ˌ ɑ p s",
        "gitops" to "ɡ ˈ ɪ t ˌ ɑ p s",
        "qube" to "k j ˈ u b",
        "saas" to "s ˈ æ s",
        "paas" to "p ˈ æ s",
        "iaas" to "ˈ I æ s",
        "nuget" to "n ˈ u ɡ ɛ t",
        "hashicorp" to "h ˈ æ ʃ i k ˌ ɔ ɹ p",
        "jira" to "ʤ ˈ ɪ ɹ ə",
        "kotlin" to "k ˈ ɑ t l ɪ n",
        "gradle" to "ɡ ɹ ˈ A d ᵊ l",
        "gui" to "ɡ ˈ u i",
        "sudo" to "s ˈ u d u",
        "wifi" to "w ˈ I f ˌ I",
        "ajax" to "ˈ A ʤ æ k s",
        "regex" to "ɹ ˈ ɛ ʤ ˌ ɛ k s",
        "async" to "ˈ A s ˌ ɪ ŋ k",
        "boolean" to "b ˈ u l i ə n",
        "enum" to "ˈ i n ə m",
        "cybersecurity" to "s ˈ I b ə ɹ s ə k j ˌ ʊ ɹ ə ɾ i",
        "terraform" to "t ˈ ɛ ɹ ə f ˌ ɔ ɹ m",
        "podman" to "p ˈ ɑ d m ə n",
        "helm" to "h ˈ ɛ l m",
        "istio" to "ˈ ɪ s t i ˌ O",
        "prometheus" to "p ɹ ə m ˈ i θ i ə s",
        "dev" to "d ˈ ɛ v",
        "plugin" to "p l ˈ ʌ ɡ ˌ ɪ n",
        "artifactory" to "ˌ ɑ ɹ t ɪ f ˈ æ k t ə ɹ i",
        "travis" to "t ɹ ˈ æ v ɪ s",
        "webhook" to "w ˈ ɛ b h ˌ ʊ k",
        "backend" to "b ˈ æ k ˌ ɛ n d",
        "frontend" to "f ɹ ˈ ʌ n t ˌ ɛ n d"
    )
}
