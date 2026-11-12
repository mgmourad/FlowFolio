package com.flowfolio.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.ReadingGoalRepository
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.model.GoalPeriod
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.feature.reader.Narration
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Redraws the home-screen widgets when what they show changes (TM2): the library (the book to continue, its page),
 * the reading goal and time read, the narration, and the app's theme. The goal widget also redraws every 30 minutes
 * by itself, so a new day starts at zero with the app closed. The app's start redraws them too (an update may
 * have changed how they look).
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: LibraryRepository,
    private val goals: ReadingGoalRepository,
    private val settings: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    fun start() {
        val books = library.observeBooks(LibrarySort.RECENT)
            .map { list -> list.map { Triple(it.id, it.lastPage, it.lastOpenedAtMillis to it.finished) } }
        val look = settings.settings.map { Triple(it.theme, it.appStyle, it.materialSeed) }
        val goal = combine(goals.observeLibraryGoal(), goals.observePeriodReadMillis(GoalPeriod.DAY)) { g, read ->
            g to read / MINUTE_MS
        }
        scope.launch {
            combine(books, look, goal, Narration.playing) { a, b, c, d -> listOf(a, b, c, d) }
                .distinctUntilChanged()
                .debounce(DEBOUNCE_MS)
                .collect { updateAll() }
        }
    }

    private suspend fun updateAll() {
        runCatching {
            ContinueReadingWidget().updateAll(context)
            ReadingGoalWidget().updateAll(context)
            NarratorWidget().updateAll(context)
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 500L
        const val MINUTE_MS = 60_000L
    }
}
