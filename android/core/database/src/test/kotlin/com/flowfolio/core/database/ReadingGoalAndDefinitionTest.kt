package com.flowfolio.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Reading goals and definitions (schema v17, M3T3). */
@RunWith(AndroidJUnit4::class)
class ReadingGoalAndDefinitionTest {
    private lateinit var db: FlowFolioDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FlowFolioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun readingGoalUpsertsAndResets() = runTest {
        val dao = db.readingGoalDao()
        assertNull(dao.observe("").first())
        val goal = ReadingGoalEntity(
            scope = "",
            targetMinutes = 45,
            period = "WEEK",
            remindEnabled = true,
            remindHour = 21,
            remindMinute = 30,
            remindDaysMask = 0b0011111,
            updatedAt = 100L
        )
        dao.upsert(goal)
        assertEquals(goal, dao.observe("").first())
        dao.delete("")
        assertNull(dao.observe("").first())
    }

    @Test
    fun definitionsAreStoredAndCounted() = runTest {
        val dao = db.definitionDao()
        dao.insert(
            DefinitionEntity(
                word = "Ephemeral",
                partOfSpeech = "adjective",
                definition = "Lasting for a very short time.",
                bookId = "b1",
                page = 12,
                createdAt = 10L
            )
        )
        dao.insert(
            DefinitionEntity(
                word = "ephemeral",
                partOfSpeech = "adjective",
                definition = "Short-lived; transitory.",
                bookId = "b2",
                page = 4,
                createdAt = 20L
            )
        )
        assertEquals(1, dao.observeWordCount().first())
        assertEquals(1, dao.observeCount("b1").first())
        assertEquals(2, dao.observeAll().first().size)
    }
}
