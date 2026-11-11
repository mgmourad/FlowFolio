package com.flowfolio.feature.reader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.core.content.ContextCompat

/**
 * Narrator Mode as a good audio citizen (owner, 2026-11-10): it asks for audio focus before speaking, so music and
 * other apps pause for it, and it stops when it loses focus (a call, another app starting to play) or when the
 * headphones come out, rather than carrying on aloud through the speaker. A notification only ducks it.
 */
internal class NarratorAudioFocus(private val context: Context, private val onLost: () -> Unit) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null
    private var listening = false

    private val unplugged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onLost()
        }
    }

    /** Takes audio focus; false when Android refuses it (a call is on), and the narrator should not start. */
    fun acquire(): Boolean = request != null || take()

    private fun take(): Boolean {
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    // The narrator's own track uses MUSIC (see Narrator.createAudioTrack); the focus request matches.
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    onLost()
                }
            }
            .build()
        if (audio?.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        request = focus
        if (!listening) {
            ContextCompat.registerReceiver(
                context,
                unplugged,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            listening = true
        }
        return true
    }

    /** Gives focus back and stops listening for the headphones; safe to call when nothing is held. */
    fun release() {
        request?.let { audio?.abandonAudioFocusRequest(it) }
        request = null
        if (listening) {
            runCatching { context.unregisterReceiver(unplugged) }
            listening = false
        }
    }
}
