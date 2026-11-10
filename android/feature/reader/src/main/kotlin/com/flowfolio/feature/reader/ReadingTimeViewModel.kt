package com.flowfolio.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.Clock
import com.flowfolio.core.data.ReadingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Counts time spent reading the open book as reading sessions (schema v5). A session runs while the reader is in front
 * and in use: page turns and taps keep it going; after [IDLE_MS] without either it ends where use stopped, and the
 * next turn starts a new one. Sessions shorter than [MIN_SESSION_MS] (a glance) are not kept.
 */
@HiltViewModel
class ReadingTimeViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val reading: ReadingRepository,
    private val clock: Clock
) : ViewModel() {
    private val bookId: String = checkNotNull(saved[ReaderViewModel.ARG_BOOK_ID]) { "reader needs a bookId" }
    private val tracker = SessionTracker { start, end ->
        viewModelScope.launch { withContext(NonCancellable) { reading.record(bookId, start, end) } }
    }

    /** Time read in this book so far, for the title pill's time left (ADR-0052). */
    val readMillis: StateFlow<Long> =
        reading.observeReadMillis(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), 0L)

    /** The reader came to the front. */
    fun onResume() = tracker.resume(clock.now())

    /** A page turn or a tap on the controls. */
    fun onActivity() = tracker.activity(clock.now())

    /** The reader left the front (another app, the screen off, or closing the book). */
    fun onPause() = tracker.pause(clock.now())

    override fun onCleared() = tracker.pause(clock.now())
}

/** The session rules, kept apart from Android so they can be tested; [save] receives finished sessions. */
class SessionTracker(private val save: (Long, Long) -> Unit) {
    private var start: Long? = null
    private var lastUse = 0L

    fun resume(now: Long) {
        start = now
        lastUse = now
    }

    fun activity(now: Long) {
        val begun = start
        if (begun == null) {
            resume(now)
            return
        }
        if (now - lastUse > IDLE_MS) {
            finish(begun, lastUse + IDLE_MS)
            start = now
        }
        lastUse = now
    }

    fun pause(now: Long) {
        val begun = start ?: return
        finish(begun, minOf(now, lastUse + IDLE_MS))
        start = null
    }

    private fun finish(from: Long, to: Long) {
        if (to - from >= MIN_SESSION_MS) save(from, to)
    }

    companion object {
        /** Five minutes on one page still counts as reading; longer is a break. */
        const val IDLE_MS = 5 * 60_000L
        const val MIN_SESSION_MS = 10_000L
    }
}

private const val STOP_MS = 5_000L
