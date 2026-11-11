package com.flowfolio.feature.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What Narrator Mode is reading, for the lock screen and the notifications ([NarratorService]). */
internal data class NowPlaying(
    val bookId: String,
    val title: String,
    val author: String?,
    val chapter: String?,
    val coverPath: String?,
    val playing: Boolean,
    val rate: Float
) {
    /** The playback's title (owner, 2026-11-10): "FlowFolio: <Book Title> - <Author> - <Chapter>", gaps left out. */
    val displayTitle: String
        get() = "FlowFolio: " +
            listOfNotNull(title, author?.takeIf { it.isNotBlank() }, chapter?.takeIf { it.isNotBlank() })
                .joinToString(" - ")
}

/** What the lock screen, the notifications and a headset can ask of the narrator. */
internal interface NarrationCommands {
    fun play()

    fun pause()

    fun next()

    fun previous()

    fun cycleSpeed()

    fun close()
}

/**
 * Between the reader, which narrates (its view model holds the book and the voice), and [NarratorService], which keeps
 * narration alive in the background and shows it on the lock screen and in the notifications. One narrator at a
 * time, in the app's own process.
 */
internal object NarrationHub {
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)

    /** What is being read, or null when nothing is (the service then stops). */
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _refreshes = MutableStateFlow(0)

    /** Bumped to show the players again: the notification permission was just granted, say. */
    val refreshes: StateFlow<Int> = _refreshes.asStateFlow()

    @Volatile
    var commands: NarrationCommands? = null
        private set

    fun refresh() {
        _refreshes.value++
    }

    fun attach(handler: NarrationCommands, playing: NowPlaying) {
        commands = handler
        _nowPlaying.value = playing
    }

    /** Updates what is shown, only for the reader that [attach]ed. */
    fun update(handler: NarrationCommands, playing: NowPlaying) {
        if (commands === handler) _nowPlaying.value = playing
    }

    fun detach(handler: NarrationCommands) {
        if (commands !== handler) return
        commands = null
        _nowPlaying.value = null
    }
}
