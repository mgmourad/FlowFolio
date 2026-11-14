package com.flowfolio.feature.reader

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.flowfolio.core.voice.SpeechSegment
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred

/**
 * Narrator Mode's voice (ADR-0059): the phone's own speech engine, Google's where it is installed, as ReadEra uses it.
 * It starts at once (nothing to download), reads numbers, acronyms and words spelled alike but said differently from
 * the sentence around them, and offers its online voices and its offline ones.
 *
 * A page is queued sentence by sentence, each sentence one utterance, so the engine says each as a whole. The
 * reader learns which sentence is under way ([speak]'s `onSegment`) and which word within it (`onWord`, from the
 * engine's word ranges), which is how the spoken text is highlighted on the page. The next page is queued behind the
 * current one ([setNextPage]), so a page turn leaves no gap.
 */
internal class Narrator(context: Context) {
    private val ready = CompletableDeferred<Boolean>()
    private val engine: String? = preferredEngine(context)
    private val tts = TextToSpeech(context.applicationContext, { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }, engine)

    /** Each utterance's sample rate and when it started playing, to show its words when they are heard. */
    private val sampleRates = ConcurrentHashMap<String, Int>()
    private val startedAt = ConcurrentHashMap<String, Long>()

    /** Each page's sentences as queued, for starting again from the sentence under way. */
    private val pages = ConcurrentHashMap<Int, List<SpeechSegment>>()

    private class Run(
        val id: Int,
        val onSegment: (Int, Int) -> Unit,
        val onWord: (Int, Int, Int, Int) -> Unit,
        val onPageAdvance: (Int) -> Unit,
        val done: (Int) -> Unit
    ) {
        /** The pages queued, in order, and the id of the last utterance queued (the run ends when it does). */
        val queued = ArrayList<Int>()
        var lastId: String? = null
    }

    @Volatile
    private var run: Run? = null
    private var runs = 0

    @Volatile
    private var playingPage = 0

    @Volatile
    private var playingIndex = 0

    private var rate = 1f
    private var voiceName: String? = null

    /** Waits for the engine; false when the phone has none that works. */
    suspend fun awaitReady(): Boolean {
        val ok = ready.await()
        if (ok) {
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            tts.setOnUtteranceProgressListener(listener)
        }
        return ok
    }

    /** The voices this engine has installed or can stream, for the voice picker. */
    fun voices(): List<android.speech.tts.Voice> = runCatching {
        tts.voices.orEmpty().filterNot { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in it.features }
    }.getOrDefault(emptyList())

    /** The engine's own choice, for when the reader has made none. */
    fun defaultVoice(): String? = runCatching { tts.defaultVoice?.name ?: tts.voice?.name }.getOrNull()

    fun setRate(value: Float) {
        rate = value
        tts.setSpeechRate(value)
    }

    /** Uses the voice named [name]; an unknown name leaves the engine's default. */
    fun setVoice(name: String?) {
        voiceName = name
        val voice = voices().firstOrNull { it.name == name } ?: return
        tts.voice = voice
    }

    /** Says [text] once in the current voice, for trying a voice out. */
    fun preview(text: String) {
        stop()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, PREVIEW_ID)
    }

    /**
     * Reads [segments] of [page] from [startIndex]. [onSegment] and [onWord] follow the voice (the page, the
     * sentence's index, and for a word its start and end in that sentence's text), [onPageAdvance] tells when it
     * moves on to a page queued by [setNextPage], and [done] when everything queued has been said.
     */
    @Suppress("LongParameterList")
    fun speak(
        page: Int,
        segments: List<SpeechSegment>,
        startIndex: Int = 0,
        onSegment: (Int, Int) -> Unit,
        onWord: (Int, Int, Int, Int) -> Unit,
        onPageAdvance: (Int) -> Unit,
        done: (Int) -> Unit
    ) {
        run?.let { main.removeCallbacksAndMessages(it) }
        tts.stop()
        sampleRates.clear()
        startedAt.clear()
        pages.clear()
        pages[page] = segments
        playingPage = page
        playingIndex = startIndex.coerceIn(0, (segments.size - 1).coerceAtLeast(0))
        val r = Run(++runs, onSegment, onWord, onPageAdvance, done)
        run = r
        tts.setSpeechRate(rate)
        enqueue(r, page, segments, playingIndex)
    }

    /**
     * Queues [page]'s [segments] behind the page being read, so the voice runs straight on; null takes back a page
     * queued earlier (continuous reading turned off).
     */
    @Suppress("ReturnCount")
    fun setNextPage(page: Int?, segments: List<SpeechSegment> = emptyList()) {
        val r = run ?: return
        if (page == null || segments.isEmpty()) {
            if (r.queued.size > 1) restartHere(keepNext = false)
            return
        }
        if (page in r.queued) return
        pages[page] = segments
        enqueue(r, page, segments, 0)
    }

    /** Says again from the sentence under way, after the speed or the voice changed. */
    fun refreshConfig() = restartHere(keepNext = true)

    fun stop() {
        run?.let { main.removeCallbacksAndMessages(it) }
        run = null
        tts.stop()
    }

    fun release() {
        stop()
        tts.shutdown()
    }

    private fun restartHere(keepNext: Boolean) {
        val r = run ?: return
        val page = playingPage
        val segments = pages[page] ?: return
        val next = r.queued.dropWhile { it != page }.drop(1).firstOrNull()?.takeIf { keepNext }
        val nextSegments = next?.let { pages[it] }
        speak(page, segments, playingIndex, r.onSegment, r.onWord, r.onPageAdvance, r.done)
        if (next != null && nextSegments != null) setNextPage(next, nextSegments)
    }

    private fun enqueue(r: Run, page: Int, segments: List<SpeechSegment>, from: Int) {
        r.queued += page
        for (i in from until segments.size) {
            val id = "${r.id}:$page:$i"
            tts.speak(segments[i].text, TextToSpeech.QUEUE_ADD, null, id)
            r.lastId = id
            val pause = segments[i].pauseMs
            if (pause > 0) {
                val silence = "$id:$SILENCE"
                tts.playSilentUtterance(pause.toLong(), TextToSpeech.QUEUE_ADD, silence)
                r.lastId = silence
            }
        }
    }

    /** The run, page and sentence an utterance id names, for the current run only. */
    @Suppress("ReturnCount") // each check is one way an id is not this run's
    private fun place(id: String?): Triple<Run, Int, Int>? {
        val parts = id?.split(':') ?: return null
        val r = run ?: return null
        if (parts.size < PLACE_PARTS || parts[0].toIntOrNull() != r.id) return null
        val page = parts[1].toIntOrNull() ?: return null
        val index = parts[2].toIntOrNull() ?: return null
        return Triple(r, page, index)
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            if (utteranceId != null) startedAt[utteranceId] = SystemClock.uptimeMillis()
            if (utteranceId?.endsWith(SILENCE) == true) return
            val (r, page, index) = place(utteranceId) ?: return
            val turned = page != playingPage
            playingPage = page
            playingIndex = index
            main.post {
                if (run !== r) return@post
                if (turned) r.onPageAdvance(page)
                r.onSegment(page, index)
            }
        }

        override fun onBeginSynthesis(utteranceId: String?, sampleRateInHz: Int, audioFormat: Int, channelCount: Int) {
            if (utteranceId != null && sampleRateInHz > 0) sampleRates[utteranceId] = sampleRateInHz
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            val (r, page, index) = place(utteranceId) ?: return
            // Online voices report a word when its audio arrives, which can be seconds before it is heard: the word
            // is shown at its place in the audio (frame / sample rate) after the sentence began to play.
            val rate = sampleRates[utteranceId]
            val began = startedAt[utteranceId]
            val due = if (rate != null && began != null) began + frame * MILLIS / rate else 0L
            val word = Runnable {
                // A word whose sentence has already ended is not shown over the next one.
                if (run === r && playingPage == page && playingIndex == index) r.onWord(page, index, start, end)
            }
            main.postAtTime(word, r, maxOf(due, SystemClock.uptimeMillis()))
        }

        override fun onDone(utteranceId: String?) {
            val (r, _, _) = place(utteranceId) ?: return
            if (utteranceId != r.lastId) return
            main.post {
                if (run !== r) return@post
                run = null
                r.done(playingPage)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = onError(utteranceId, TextToSpeech.ERROR)

        override fun onError(utteranceId: String?, errorCode: Int) {
            val (r, _, _) = place(utteranceId) ?: return
            Log.w(TAG, "utterance $utteranceId failed: $errorCode")
            val network = errorCode == TextToSpeech.ERROR_NETWORK || errorCode == TextToSpeech.ERROR_NETWORK_TIMEOUT
            main.post {
                if (run !== r) return@post
                if (network && fallBackOffline()) {
                    restartHere(keepNext = true)
                } else if (utteranceId == r.lastId) {
                    run = null
                    r.done(playingPage)
                }
            }
        }
    }

    /**
     * An online voice lost its connection: carry on in the same language's offline voice, the same one where the
     * engine has it ("en-us-x-iob-network" to "en-us-x-iob-local"). False when there is none.
     */
    @Suppress("ReturnCount")
    private fun fallBackOffline(): Boolean {
        val current = tts.voice ?: return false
        if (!current.isNetworkConnectionRequired) return false
        val offline = voices().filter { !it.isNetworkConnectionRequired && it.locale == current.locale }
        val twin =
            offline.firstOrNull { it.name == current.name.replace("-network", "-local") } ?: offline.firstOrNull()
        twin ?: return false
        tts.voice = twin
        return true
    }

    companion object {
        private const val TAG = "Narrator"
        private const val SILENCE = "s"
        private const val PLACE_PARTS = 3
        private const val PREVIEW_ID = "preview"
        private const val MILLIS = 1000L
        const val GOOGLE_ENGINE = "com.google.android.tts"
        private val main = Handler(Looper.getMainLooper())

        /** Google's engine where the phone has it (its voices are the ones ReadEra reads with), else the default. */
        fun preferredEngine(context: Context): String? {
            val engines = runCatching {
                context.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
                    .map { it.serviceInfo.packageName }
            }.getOrDefault(emptyList())
            return GOOGLE_ENGINE.takeIf { it in engines }
        }

        /** Opens the phone's text-to-speech settings, where voices are installed and the engine chosen. */
        fun settingsIntent(): Intent =
            Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
