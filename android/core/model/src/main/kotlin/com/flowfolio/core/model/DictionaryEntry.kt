package com.flowfolio.core.model

/**
 * An English definition looked up with Define in the reader (owner, M3T3; Room v17 `definitions`).
 */
data class WordDefinition(
    val id: Long = 0,
    val word: String,
    val partOfSpeech: String? = null,
    val definition: String,
    val bookId: String? = null,
    val page: Int? = null,
    val locator: String? = null,
    val createdAtMillis: Long = 0L
)

/** One definition from one dictionary source, for the Define popup (Wiktionary, WordNet, Wikipedia). */
data class DefineEntry(val source: String, val partOfSpeech: String?, val definition: String)

/**
 * One word on the Dictionary page (owner, M3T3): combines any saved translation (Room `translations`) and English
 * definition (Room `definitions`) for the same word and book.
 */
data class DictionaryWord(
    val key: String,
    val word: String,
    val bookId: String?,
    val page: Int? = null,
    val locator: String? = null,
    val translation: String? = null,
    val fromLang: String? = null,
    val toLang: String? = null,
    val partOfSpeech: String? = null,
    val definition: String? = null,
    val updatedAtMillis: Long = 0L
)
