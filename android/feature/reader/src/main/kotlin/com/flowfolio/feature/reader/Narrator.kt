package com.flowfolio.feature.reader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * Offline text-to-speech for Narrator Mode (owner, 2026-11-07). Android's own `TextToSpeech`, so it is offline and
 * dependency-free; the device's voices are offered best-first so the most natural one is the default.
 */
internal class Narrator(context: Context, private val onReady: (List<Voice>) -> Unit) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var onDone: (() -> Unit)? = null

    /** The device's voices, best first (the Google engine, then highest quality, English voices preferred). */
    val voices: List<Voice> get() = bestFirst(tts.voices?.toList().orEmpty())

    var rate: Float = 1f
        set(value) {
            field = value
            if (ready) tts.setSpeechRate(value)
        }

    /** The voice in use, or null for the default. */
    var voice: Voice?
        get() = tts.voice
        set(value) {
            if (ready && value != null) tts.voice = value
        }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        ready = true
        tts.setSpeechRate(rate)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                val done = onDone
                onDone = null
                done?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val done = onDone
                onDone = null
                done?.invoke()
            }
        })
        onReady(voices)
    }

    /** Speaks [text], calling [done] when it finishes (or fails); flushes anything already reading. */
    fun speak(text: String, done: () -> Unit) {
        onDone = done
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE)
    }

    /** Stops whatever is reading (the reader can start again from the current page). */
    fun stop() {
        onDone = null
        if (ready) tts.stop()
    }

    val isReady: Boolean get() = ready

    fun release() = tts.shutdown()

    companion object {
        private const val UTTERANCE = "flowfolio-narrator"
        private const val GOOGLE = "com.google.android.tts"

        /** Prefer the Google engine, then the highest quality, then English; stable order for the picker. */
        private fun bestFirst(all: List<Voice>): List<Voice> {
            val byLocale = all.filter { it.locale.language == Locale.ENGLISH.language }.ifEmpty { all }
            return byLocale.sortedWith(
                compareByDescending<Voice> { it.name.startsWith(GOOGLE) }
                    .thenByDescending { it.quality }
                    .thenBy { it.name }
            )
        }
    }
}
