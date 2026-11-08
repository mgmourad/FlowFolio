package com.flowfolio.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** A WordNet sense: its part of speech and gloss (Princeton WordNet, WordNet License). */
internal data class WordNetSense(val pos: String, val definition: String)

/**
 * On-phone WordNet (owner, 2026-11-07): the full Princeton WordNet 3.1 database, built by
 * `tools/dictionary/build_wordnet.py` into a compact SQLite file and bundled gzipped. It is decompressed into
 * `filesDir/dictionary/wordnet.db` on first use and queried read-only, so definitions stay offline and memory stays
 * bounded (the database is never loaded whole).
 */
@Singleton
class WordNetStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    private val dir: File get() = File(context.filesDir, "dictionary").apply { mkdirs() }
    private val dbFile: File get() = File(dir, "wordnet.db")

    @Volatile
    private var db: SQLiteDatabase? = null

    private fun ensureDb(): SQLiteDatabase? = db ?: synchronized(this) {
        db ?: runCatching {
            if (!dbFile.exists()) {
                context.assets.open(ASSET).use { input ->
                    GZIPInputStream(input).use { gz -> dbFile.outputStream().use { gz.copyTo(it) } }
                }
            }
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
        }.getOrNull()?.also { db = it }
    }

    /** The senses of [rawWord], morphologically reduced; empty when the word is not in WordNet. */
    internal suspend fun lookup(rawWord: String): List<WordNetSense> = withContext(io) {
        val db = ensureDb() ?: return@withContext emptyList()
        val clean = WiktionaryStore.normalize(rawWord)
        if (clean.isEmpty()) return@withContext emptyList()
        for (candidate in WiktionaryStore.candidates(clean)) {
            val found = ArrayList<WordNetSense>()
            db.rawQuery("SELECT pos, definition FROM entries WHERE lemma = ?", arrayOf(candidate)).use { c ->
                while (c.moveToNext()) found += WordNetSense(c.getString(0), c.getString(1))
            }
            if (found.isNotEmpty()) return@withContext found
        }
        emptyList()
    }

    private companion object {
        const val ASSET = "wordnet.db.gz"
    }
}
