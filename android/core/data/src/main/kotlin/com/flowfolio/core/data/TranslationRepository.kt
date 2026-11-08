package com.flowfolio.core.data

import com.flowfolio.core.database.DefinitionDao
import com.flowfolio.core.database.TranslationDao
import com.flowfolio.core.database.TranslationEntity
import com.flowfolio.core.model.ReadingPlace
import com.flowfolio.core.model.Translation
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation as MlTranslation
import com.google.mlkit.nl.translate.TranslatorOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine

/** Translation on the phone (Google ML Kit, ADR-0018): nothing is sent to a translation service. */
interface Translator {
    /** The languages it can translate between, as BCP-47 codes. */
    val languages: List<String>

    /** The language of [text], or null when it cannot tell. */
    suspend fun detect(text: String): String?

    /** Whether the model for [language] is on the phone (else the first translation downloads it, about 30 MB). */
    suspend fun isDownloaded(language: String): Boolean

    /** [text] from [from] into [to]; downloads the models it needs first. */
    suspend fun translate(text: String, from: String, to: String): String
}

class MlKitTranslator @Inject constructor() : Translator {
    override val languages: List<String> = TranslateLanguage.getAllLanguages().sorted()

    /**
     * A word or two says little about its language ("resultant" reads as Catalan): short passages need a confident
     * answer, else null (the panel then assumes the phone's language).
     */
    override suspend fun detect(text: String): String? {
        val words = text.split(WHITESPACE).count { it.isNotBlank() }
        val threshold = if (words < SHORT_WORDS) SHORT_CONFIDENCE else NORMAL_CONFIDENCE
        val client = LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder().setConfidenceThreshold(threshold).build()
        )
        return try {
            client.identifyLanguage(text).await().takeIf { it != UNDETERMINED }
                ?.let { TranslateLanguage.fromLanguageTag(it) }
        } finally {
            client.close()
        }
    }

    override suspend fun isDownloaded(language: String): Boolean = language == TranslateLanguage.ENGLISH ||
        RemoteModelManager.getInstance().isModelDownloaded(TranslateRemoteModel.Builder(language).build()).await()

    @Suppress("TooGenericExceptionCaught")
    override suspend fun translate(text: String, from: String, to: String): String {
        if (from == to) return text
        val client = MlTranslation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build()
        )
        return try {
            client.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            client.translate(text).await()
        } catch (e: Exception) {
            android.util.Log.w("FlowFolioTranslate", "translate $from->$to failed", e)
            throw e
        } finally {
            client.close()
        }
    }

    private companion object {
        const val UNDETERMINED = "und"
        const val SHORT_WORDS = 4
        const val SHORT_CONFIDENCE = 0.9f
        const val NORMAL_CONFIDENCE = 0.5f
        val WHITESPACE = Regex("""\s+""")
    }
}

/** A Play Services task as a coroutine (avoids another dependency for Task.await). */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

/** Translations and their history. */
interface TranslationRepository {
    val translator: Translator

    /** The most recent translations, newest first. */
    fun history(limit: Int = HISTORY_LIMIT): Flow<List<Translation>>

    /** How many different words and phrases were translated: the dictionary's size (owner, M3T2). */
    fun wordCount(): Flow<Int> = flowOf(0)

    /** How many of a book's translations there are (owner, M3T2). */
    fun observeDictionaryCount(bookId: String): Flow<Int> = flowOf(0)

    /** Translates and remembers it. */
    suspend fun translate(text: String, from: String, to: String, bookId: String?, at: ReadingPlace): Translation

    /** Forgets one translation (owner, M5 round); the passage it was made from is untouched. */
    suspend fun remove(id: Long) {}

    suspend fun clearHistory()

    companion object {
        const val HISTORY_LIMIT = 50
    }
}

@Singleton
class DefaultTranslationRepository(
    override val translator: Translator,
    private val dao: TranslationDao,
    private val clock: Clock,
    private val definitions: DefinitionDao? = null
) : TranslationRepository {
    @Inject
    constructor(
        translator: Translator,
        dao: TranslationDao,
        definitions: DefinitionDao,
        clock: Clock
    ) : this(translator, dao, clock, definitions)

    override fun history(limit: Int): Flow<List<Translation>> = dao.observeRecent(limit).map { list ->
        list.map {
            Translation(
                it.id,
                it.source,
                it.translated,
                it.from,
                it.to,
                it.bookId,
                it.createdAt,
                it.page,
                it.locator
            )
        }
    }

    override fun wordCount(): Flow<Int> = definitions?.let { defs ->
        combine(dao.observeWordCount(), defs.observeWordCount()) { a, b -> a + b }
    } ?: dao.observeWordCount()

    override fun observeDictionaryCount(bookId: String): Flow<Int> = definitions?.let { defs ->
        combine(dao.observeCount(bookId), defs.observeCount(bookId)) { a, b -> a + b }
    } ?: dao.observeCount(bookId)

    override suspend fun translate(
        text: String,
        from: String,
        to: String,
        bookId: String?,
        at: ReadingPlace
    ): Translation {
        val result = translator.translate(text, from, to)
        val now = clock.now()
        val id = dao.insert(
            TranslationEntity(
                source = text,
                translated = result,
                from = from,
                to = to,
                bookId = bookId,
                createdAt = now,
                page = at.page,
                locator = at.locator
            )
        )
        return Translation(id, text, result, from, to, bookId, now, at.page, at.locator)
    }

    override suspend fun remove(id: Long) = dao.delete(id)

    override suspend fun clearHistory() = dao.clear()
}
