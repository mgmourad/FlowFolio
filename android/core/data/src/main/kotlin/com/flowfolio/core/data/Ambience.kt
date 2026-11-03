package com.flowfolio.core.data

import com.flowfolio.core.model.Ambient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The Reading Atmosphere's sound, resolved and played (owner, M5).
 *
 * [AmbientPlayer] knows how to play an address and [StreamResolver] how to find one; putting them together here keeps
 * that pairing in one place instead of in each screen that wants a sound, and keeps both off the main thread, since
 * resolving an address and opening a stream are both network work.
 */
@Singleton
class Ambience @Inject constructor(
    private val player: AmbientPlayer,
    private val streams: StreamResolver,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    /**
     * Plays an atmosphere's sound: an address of the reader's own is resolved first, a bundled ambience plays
     * straight away. Answers whether anything is playing now.
     *
     * Asking for what is already playing only moves the volume, so a slider does not restart the sound.
     */
    suspend fun play(ambient: Ambient, volume: Float, soundUri: String?): Boolean = withContext(io) {
        player.play(ambient, volume, soundUri?.let(streams::playable))
    }

    /**
     * Tries [address] afresh and leaves it playing if it works, which is the only honest way to tell a reader
     * whether what they typed will play: nothing about the text itself can promise it.
     *
     * The remembered answer is dropped first, so correcting a typo and listening again really does try again.
     */
    suspend fun audition(address: String, volume: Float): Boolean = withContext(io) {
        streams.forget()
        val resolved = streams.playable(address.trim())
        resolved != null && player.play(Ambient.SILENCE, volume, resolved)
    }

    /** Falls silent. */
    fun stop() = player.stop()
}
