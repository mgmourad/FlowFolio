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
        // Pick the most natural voice the device offers before the first word (owner, 2026-11-07).
        tts.voice = voices.firstOrNull()
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

        /**
         * The device's voices, best first: offline only (the owner wants Narrator Mode offline forever), then the
         * highest quality (neural = `QUALITY_VERY_HIGH`), US English, and Google's offline neural voices (whose names
         * end in `-local`). The list stays in a stable order for the picker.
         */
        private fun bestFirst(all: List<Voice>): List<Voice> {
            val offline = all.filter { !it.isNetworkConnectionRequired }.ifEmpty { all }
            val english = offline.filter { it.locale.language == Locale.ENGLISH.language }.ifEmpty { offline }
            return english.sortedWith(
                compareByDescending<Voice> { it.quality }
                    .thenByDescending { it.locale.country == "US" }
                    .thenByDescending { it.name.contains("-local") }
                    .thenBy { it.name }
            )
        }

        /** A friendly label for the picker: "English (United States)" with a neural marker when it applies. */
        fun label(voice: Voice): String {
            val language = voice.locale.getDisplayLanguage(Locale.ENGLISH).replaceFirstChar { it.uppercase() }
            val country = voice.locale.getDisplayCountry(Locale.ENGLISH)
            val name = if (country.isNotBlank()) "$language ($country)" else language
            return if (voice.quality == Voice.QUALITY_VERY_HIGH) "$name · Neural" else name
        }
    }
}
