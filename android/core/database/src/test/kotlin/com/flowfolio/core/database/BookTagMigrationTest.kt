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
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schema v21 to v22 (owner, 2026-11-10): a real v21 database, built from the committed schema, opens under v22 with
 * its books intact and no tags; tags then add, ignore duplicates, list by book and go with their book.
 */
@RunWith(AndroidJUnit4::class)
class BookTagMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun v21OpensAsV22WithBooksKeptAndNoTags() = runTest {
        val name = "migrate-21.db"
        context.deleteDatabase(name)
        createV21(context.getDatabasePath(name))
        val db = Room.databaseBuilder(context, FlowFolioDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            assertEquals("Moby-Dick", db.bookDao().observe("b1").first()?.title)
            val tags = db.bookTagDao()
            assertEquals(emptyList<String>(), tags.observe("b1").first())
            tags.add(BookTagEntity("b1", "whales", 1L))
            tags.add(BookTagEntity("b1", "sea", 2L))
            tags.add(BookTagEntity("b1", "whales", 3L))
            assertEquals(listOf("whales", "sea"), tags.observe("b1").first())
            tags.remove("b1", "sea")
            assertEquals(listOf("whales"), tags.observeAll().first())
            db.bookDao().delete("b1")
            assertEquals(emptyList<String>(), tags.observeAll().first())
        } finally {
            db.close()
        }
    }

    /** Lays out schema v21 exactly as exported, with one book in it, as an install from before the overhaul has. */
    private fun createV21(file: File) {
        val schema = JSONObject(File("schemas/com.flowfolio.core.database.FlowFolioDatabase/21.json").readText())
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
        sql.version = V21
        sql.close()
    }

    /** One book, with a value for every column v21 requires. */
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
        const val V21 = 21
    }
}
