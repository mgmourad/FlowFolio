package com.flowfolio.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schema v20 to v21 (ADR-0052): a real v20 database, built from the committed schema, opens under v21 with its books
 * intact and an empty `book_text`, so every book follows the default text settings until the reader changes one.
 * Written without room-testing: the committed schema JSON is replayed into SQLite directly.
 */
@RunWith(AndroidJUnit4::class)
class BookTextMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun v20OpensAsV21WithBooksKeptAndNoTextOverrides() = runTest {
        val name = "migrate-20.db"
        context.deleteDatabase(name)
        createV20(context.getDatabasePath(name))
        val db = Room.databaseBuilder(context, FlowFolioDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            val book = db.bookDao().observe("b1").first()
            assertEquals("Moby-Dick", book?.title)
            assertNull(db.bookTextDao().observe("b1").first())
            val text = BookTextEntity("b1", "CHARTER", 20, "RELAXED", "WIDE", justify = false, hyphenate = true)
            db.bookTextDao().upsert(text)
            assertEquals(text, db.bookTextDao().observe("b1").first())
            db.bookTextDao().delete("b1")
            assertNull(db.bookTextDao().observe("b1").first())
        } finally {
            db.close()
        }
    }

    /** Lays out schema v20 exactly as exported, with one book in it, as an install from before the overhaul has. */
    private fun createV20(file: File) {
        val schema = JSONObject(File("schemas/com.flowfolio.core.database.FlowFolioDatabase/20.json").readText())
            .getJSONObject("database")
        file.parentFile?.mkdirs()
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = e.optJSONArray("indices")
            if (indices != null) {
                for (j in 0 until indices.length()) {
                    sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            if (table == "books") insertBook(sql, e)
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) sql.execSQL(setup.getString(i))
        sql.version = V20
        sql.close()
    }

    /** One book, with a value for every column v20 requires. */
    private fun insertBook(sql: SQLiteDatabase, books: JSONObject) {
        val fields = books.getJSONArray("fields")
        val columns = ArrayList<String>()
        val values = ArrayList<Any>()
        for (i in 0 until fields.length()) {
            val f = fields.getJSONObject(i)
            if (!f.optBoolean("notNull")) continue
            val column = f.getString("columnName")
            columns += column
            values += when {
                column == "id" -> "b1"
                column == "title" -> "Moby-Dick"
                f.getString("affinity") == "TEXT" -> ""
                else -> 0
            }
        }
        val marks = columns.joinToString(",") { "?" }
        sql.execSQL("INSERT INTO books (${columns.joinToString(",")}) VALUES ($marks)", values.toTypedArray())
    }

    private companion object {
        const val V20 = 20
    }
}
