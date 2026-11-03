package com.flowfolio.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfolio.core.model.GoalPeriod
import com.flowfolio.core.model.ReadingGoal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingGoalAndDictionaryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `reading goal progress and exceeded`() {
        val goal = ReadingGoal(
            scope = ReadingGoal.LIBRARY_SCOPE,
            targetMinutes = 30,
            period = GoalPeriod.DAY
        )
        assertEquals(0.5f, goal.progress(15 * 60_000L), 0.001f)
        assertFalse(goal.exceeded(30 * 60_000L))
        assertTrue(goal.exceeded(31 * 60_000L))
    }

    @Test
    fun `reading quotes has at least 60 entries with authors`() {
        assertTrue(ReadingQuotes.all.size >= 60)
        val q = ReadingQuotes.pick(42)
        assertTrue(q.quote.isNotBlank())
        assertTrue(q.author.isNotBlank())
    }

    @Test
    fun `single word detection and wiktionary lookup with stemming`() = runTest {
        assertTrue(DefaultDictionaryRepository.isSingleWord("ephemeral"))
        assertTrue(DefaultDictionaryRepository.isSingleWord("“ephemeral,”"))
        assertFalse(DefaultDictionaryRepository.isSingleWord("two words"))
        assertFalse(DefaultDictionaryRepository.isSingleWord("   "))

        val store = WiktionaryStore(context, { null }, kotlinx.coroutines.Dispatchers.Unconfined)
        val exact = store.lookup("ephemeral")
        assertNotNull(exact)
        assertEquals("adjective", exact?.partOfSpeech)

        val stemmed = store.lookup("algorithms")
        assertNotNull(stemmed)
        assertTrue(stemmed!!.definition.contains("step-by-step", ignoreCase = true))
    }
}
