package com.flowfolio.feature.reader

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * What the rest of the app (the home-screen Narrator widget, TM2) may see of Narrator Mode and ask of it. Narration
 * itself stays in the reader (ADR-0053); this only reads [NarrationHub] and passes on its commands.
 */
object Narration {
    /** The book being narrated, and how. */
    data class Playing(
        val bookId: String,
        val title: String,
        val author: String?,
        val chapter: String?,
        val coverPath: String?,
        val playing: Boolean,
        val rate: Float
    )

    /** A player button. */
    enum class Command { PLAY_PAUSE, PREVIOUS, NEXT, SPEED }

    /** What is being narrated, or null when nothing is. */
    val playing: Flow<Playing?> = NarrationHub.nowPlaying.map { it?.toPlaying() }

    /** The same, now. */
    val current: Playing? get() = NarrationHub.nowPlaying.value?.toPlaying()

    /** Sends [command] to the reader that is narrating, if one is; call on the main thread. */
    fun send(command: Command) {
        val commands = NarrationHub.commands ?: return
        when (command) {
            Command.PLAY_PAUSE -> if (NarrationHub.nowPlaying.value?.playing ==
                true
            ) {
                commands.pause()
            } else {
                commands.play()
            }

            Command.PREVIOUS -> commands.previous()

            Command.NEXT -> commands.next()

            Command.SPEED -> commands.cycleSpeed()
        }
    }

    /** The next reader opened on [bookId] starts narrating once its pages are ready (Play with nothing playing). */
    fun startWhenOpened(bookId: String) {
        NarrationHub.startOnOpen = bookId
    }

    private fun NowPlaying.toPlaying() = Playing(bookId, title, author, chapter, coverPath, playing, rate)
}
