@file:Suppress("MagicNumber")

package com.flowfolio.core.data

import com.flowfolio.core.database.DefinitionDao
import com.flowfolio.core.database.DefinitionEntity
import com.flowfolio.core.database.TranslationDao
import com.flowfolio.core.database.TranslationEntity
import com.flowfolio.core.model.DictionaryWord
import com.flowfolio.core.model.WordDefinition
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Offline English Wiktionary definitions and the combined Dictionary (translations + definitions, owner, M3T3).
 */
interface DictionaryRepository {
    val isReady: Boolean get() = true

    fun observeDefinitions(): Flow<List<WordDefinition>> = flowOf(emptyList())

    fun observeWords(): Flow<List<DictionaryWord>> = flowOf(emptyList())

    /** Total number of definitions and translations per book id. */
    fun observeCountsByBook(): Flow<Map<String, Int>> = flowOf(emptyMap())

    suspend fun define(
        word: String,
        bookId: String? = null,
        page: Int? = null,
        locator: String? = null
    ): WordDefinition? = null

    /** Forgets one definition (owner, M5 round), leaving any others of the same word. */
    suspend fun removeDefinition(id: Long) {}

    suspend fun removeWord(word: DictionaryWord) {}

    suspend fun removeForBook(bookId: String) {}

    /** Forgets every definition (owner, M5 round 4: part of resetting the app). */
    suspend fun removeAll() {}
}

@Singleton
class DefaultDictionaryRepository @Inject constructor(
    private val definitions: DefinitionDao,
    private val translations: TranslationDao,
    private val wiktionary: WiktionaryStore,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher
) : DictionaryRepository {
    override val isReady: Boolean get() = wiktionary.isReady()

    override fun observeDefinitions(): Flow<List<WordDefinition>> =
        definitions.observeAll().map { list -> list.map { it.toModel() } }

    override fun observeWords(): Flow<List<DictionaryWord>> = combine(
        translations.observeRecent(MAX_WORDS),
        definitions.observeAll()
    ) { trans, defs -> mergeWords(trans, defs) }

    override fun observeCountsByBook(): Flow<Map<String, Int>> = combine(
        translations.observeRecent(MAX_WORDS),
        definitions.observeAll()
    ) { trans, defs ->
        val counts = HashMap<String, Int>()
        trans.forEach { t -> t.bookId?.let { id -> counts[id] = (counts[id] ?: 0) + 1 } }
        defs.forEach { d -> d.bookId?.let { id -> counts[id] = (counts[id] ?: 0) + 1 } }
        counts
    }

    override suspend fun define(word: String, bookId: String?, page: Int?, locator: String?): WordDefinition? =
        withContext(io) {
            wiktionary.ensureDownloaded()
            val entry = wiktionary.lookup(word) ?: return@withContext null
            val now = clock.now()
            val cleaned = word.trim().trim { !it.isLetterOrDigit() }.ifEmpty { entry.word }
            val id = definitions.insert(
                DefinitionEntity(
                    word = cleaned,
                    partOfSpeech = entry.partOfSpeech,
                    definition = entry.definition,
                    bookId = bookId,
                    page = page,
                    locator = locator,
                    createdAt = now
                )
            )
            WordDefinition(
                id = id,
                word = cleaned,
                partOfSpeech = entry.partOfSpeech,
                definition = entry.definition,
                bookId = bookId,
                page = page,
                locator = locator,
                createdAtMillis = now
            )
        }

    override suspend fun removeDefinition(id: Long) {
        withContext(io) { definitions.delete(id) }
    }

    override suspend fun removeWord(word: DictionaryWord) {
        withContext(io) {
            word.bookId?.let { definitions.deleteForBook(it) }
        }
    }

    override suspend fun removeForBook(bookId: String) {
        withContext(io) {
            definitions.deleteForBook(bookId)
        }
    }

    override suspend fun removeAll() {
        withContext(io) { definitions.clear() }
    }

    companion object {
        private const val MAX_WORDS = 2_000

        /**
         * Combines saved translations (`translations` table) and English definitions (`definitions` table) so each word
         * in a book shows its word, its translation, and its English definition on one card (owner, M3T3).
         */
        internal fun mergeWords(
            translations: List<TranslationEntity>,
            definitions: List<DefinitionEntity>
        ): List<DictionaryWord> {
            val map = LinkedHashMap<String, DictionaryWord>()
            translations.forEach { t ->
                val norm = t.source.trim().lowercase()
                val key = "${t.bookId.orEmpty()}:$norm"
                val prev = map[key]
                if (prev == null) {
                    map[key] = DictionaryWord(
                        key = key,
                        word = t.source.trim(),
                        bookId = t.bookId,
                        // Since Room v18 a translation remembers where it was asked for, so the
                        // Dictionary page can open the book there (owner, M4b).
                        page = t.page,
                        locator = t.locator,
                        translation = t.translated,
                        fromLang = t.from,
                        toLang = t.to,
                        updatedAtMillis = t.createdAt
                    )
                }
            }
            definitions.forEach { d ->
                val norm = d.word.trim().lowercase()
                val key = "${d.bookId.orEmpty()}:$norm"
                val prev = map[key]
                if (prev == null) {
                    map[key] = DictionaryWord(
                        key = key,
                        word = d.word.trim(),
                        bookId = d.bookId,
                        page = d.page,
                        locator = d.locator,
                        partOfSpeech = d.partOfSpeech,
                        definition = d.definition,
                        updatedAtMillis = d.createdAt
                    )
                } else {
                    map[key] = prev.copy(
                        page = prev.page ?: d.page,
                        locator = prev.locator ?: d.locator,
                        partOfSpeech = prev.partOfSpeech ?: d.partOfSpeech,
                        definition = prev.definition ?: d.definition,
                        updatedAtMillis = maxOf(prev.updatedAtMillis, d.createdAt)
                    )
                }
            }
            return map.values.sortedByDescending { it.updatedAtMillis }
        }

        /** Single-word selection check for showing Define in the reader's selection menu (owner, M3T3). */
        fun isSingleWord(text: String): Boolean {
            val trimmed = text.trim().trim { !it.isLetterOrDigit() }
            return trimmed.length in 2..40 && trimmed.none { it.isWhitespace() } && trimmed.any { it.isLetter() }
        }
    }
}

internal fun DefinitionEntity.toModel(): WordDefinition = WordDefinition(
    id = id,
    word = word,
    partOfSpeech = partOfSpeech,
    definition = definition,
    bookId = bookId,
    page = page,
    locator = locator,
    createdAtMillis = createdAt
)
