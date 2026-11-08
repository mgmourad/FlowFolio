package com.flowfolio.feature.reader

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.flowfolio.core.voice.PcmAudio
import com.flowfolio.core.voice.SpeechSegment
import com.flowfolio.core.voice.VoiceEngine
import com.flowfolio.core.voice.VoiceSpeakers
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private data class PageBatch(val page: Int, val segments: List<SpeechSegment>)

private class QueuedChunk(
    val page: Int = 0,
    val index: Int,
    val pcm: ShortArray,
    val flushBeforePlay: Boolean = false,
    val endOfStream: Boolean = false
)

/**
 * Narrator Mode's neural voice (Phase 6 M3): Kokoro or Piper via sherpa-onnx.
 *
 * [speak] decouples ONNX synthesis from [AudioTrack] playback using a single-thread synthesis executor
 * and a bounded producer-consumer queue: the synthesis worker stays 1–3 sentences ahead (including across
 * page boundaries via [setNextPage]) while the playback thread feeds [AudioTrack] continuously, eliminating
 * inter-sentence and cross-page pauses.
 */
internal class Narrator(private var engine: VoiceEngine, @Volatile var modelKey: String? = null) {
    private val synthExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "FlowFolio-Narrator-Synth").apply { isDaemon = true }
    }

    private val pageBatches = ConcurrentHashMap<Int, List<SpeechSegment>>()

    @Volatile
    private var nextBatch: PageBatch? = null

    @Volatile
    private var sampleRate: Int = engine.sampleRate

    @Volatile
    private var speakerId: Int = VoiceSpeakers.defaults.first().id

    @Volatile
    private var rate: Float = 1f

    @Volatile
    private var generation = 0

    @Volatile
    private var synthRevision = 0

    @Volatile
    private var flushRequested = false

    @Volatile
    private var playingPage = 0

    @Volatile
    private var playingIndex = 0

    @Volatile
    private var activeQueue: ArrayBlockingQueue<QueuedChunk>? = null

    @Volatile
    private var track: AudioTrack? = null

    @Volatile
    private var audioWorker: Thread? = null

    fun setRate(value: Float) {
        rate = value
    }

    fun setVoice(name: String) {
        VoiceSpeakers.voiceOf(name)?.let { speakerId = it.id }
    }

    /**
     * Supplies the next page's segments ahead of time so the synthesis worker can pre-generate the opening
     * sentence of [page] before the current page finishes playing, bridging mid-sentence page breaks with zero gap.
     */
    fun setNextPage(page: Int?, segments: List<SpeechSegment> = emptyList()) {
        if (page == null || segments.isEmpty()) {
            nextBatch = null
        } else {
            pageBatches[page] = segments
            nextBatch = PageBatch(page, segments)
        }
    }

    /**
     * Triggers an in-place re-synthesis from the currently playing phrase when speed or Kokoro speaker changes
     * mid-narration: existing audio continues until the first phrase at the new speed/voice is ready (~300 ms),
     * then flushes and switches seamlessly without dead air or lock contention.
     */
    fun refreshConfig() {
        synthRevision++
        activeQueue?.clear()
    }

    /** Swaps in a freshly loaded engine (Kokoro or a Piper voice), keeping the speed; the reader is paused first. */
    fun swapEngine(newEngine: VoiceEngine, newSpeakerId: Int, newModelKey: String?) {
        stop()
        speakerId = newSpeakerId
        modelKey = newModelKey
        sampleRate = newEngine.sampleRate
        synthExecutor.execute {
            runCatching { engine.release() }
            engine = newEngine
        }
    }

    @Suppress("TooGenericExceptionCaught", "LongParameterList")
    fun speak(
        page: Int = 0,
        segments: List<SpeechSegment>,
        startIndex: Int = 0,
        onSegment: (Int, Int) -> Unit = { _, _ -> },
        onPageAdvance: (Int) -> Unit = {},
        done: (Int) -> Unit
    ) {
        val gen = ++generation
        stopTrack()
        pageBatches.clear()
        pageBatches[page] = segments
        nextBatch = null
        val firstIndex = startIndex.coerceIn(0, (segments.size - 1).coerceAtLeast(0))
        playingPage = page
        playingIndex = firstIndex
        flushRequested = false
        val queue = ArrayBlockingQueue<QueuedChunk>(QUEUE_CAPACITY)
        activeQueue = queue
        synthExecutor.execute {
            produceSegments(page, segments, firstIndex, queue, gen)
        }
        val consumer = Thread({
            val rateHz = sampleRate
            val t = createAudioTrack(rateHz)
            track = t
            try {
                if (gen == generation) {
                    t.play()
                    consumeAudioQueue(t, rateHz, page, queue, onSegment, onPageAdvance, gen)
                }
            } catch (_: InterruptedException) {
                // Interrupted by stop() or a new speak() call.
            } catch (err: Throwable) {
                Log.e(TAG, "narrator playback failed", err)
            } finally {
                runCatching { t.stop() }
                t.release()
                if (track === t) track = null
                if (gen == generation) {
                    generation++
                    val finishedPage = playingPage
                    mainHandler.post { done(finishedPage) }
                }
            }
        }, "FlowFolio-Narrator-Audio")
        audioWorker = consumer
        consumer.start()
    }

    fun stop() {
        generation++
        nextBatch = null
        stopTrack()
    }

    private fun stopTrack() {
        activeQueue?.clear()
        val currentTrack = track
        runCatching { currentTrack?.pause() }
        runCatching { currentTrack?.flush() }
        runCatching { currentTrack?.stop() }
        audioWorker?.interrupt()
    }

    fun release() {
        stop()
        synthExecutor.execute {
            runCatching { engine.release() }
        }
        synthExecutor.shutdown()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun produceSegments(
        initialPage: Int,
        initialSegments: List<SpeechSegment>,
        startIndex: Int,
        queue: ArrayBlockingQueue<QueuedChunk>,
        gen: Int
    ) {
        try {
            var rev = synthRevision
            var curPage = initialPage
            var curSegments = initialSegments
            var i = startIndex
            var replacing = false
            var sentEos = false
            while (gen == generation) {
                val upcoming = nextBatch?.takeIf { it.page != curPage && it.segments.isNotEmpty() }
                when {
                    rev != synthRevision -> {
                        rev = synthRevision
                        curPage = playingPage
                        curSegments = pageBatches[curPage] ?: curSegments
                        i = playingIndex.coerceIn(0, (curSegments.size - 1).coerceAtLeast(0))
                        replacing = true
                        sentEos = false
                        queue.clear()
                    }

                    i < curSegments.size -> {
                        synthesizeAndOffer(curPage, curSegments[i], i, replacing, gen, rev)
                        replacing = false
                        i++
                    }

                    upcoming != null -> {
                        nextBatch = null
                        curPage = upcoming.page
                        curSegments = upcoming.segments
                        i = 0
                        sentEos = false
                    }

                    !sentEos -> {
                        enqueueChunk(QueuedChunk(index = -1, pcm = ShortArray(0), endOfStream = true), gen, rev)
                        sentEos = true
                    }

                    else -> Thread.sleep(QUEUE_POLL_MS)
                }
            }
        } catch (_: InterruptedException) {
            // Interrupted by stop() or a new speak() call.
        } catch (t: Throwable) {
            Log.e(TAG, "narrator synthesis failed", t)
        }
    }

    @Suppress("LongParameterList")
    private fun synthesizeAndOffer(
        page: Int,
        segment: SpeechSegment,
        index: Int,
        flushBeforePlay: Boolean,
        gen: Int,
        rev: Int
    ) {
        if (gen != generation || rev != synthRevision) return
        val audio = engine.generate(segment.text, speakerId, rate)
        sampleRate = audio.sampleRate
        val pcm = PcmAudio.toTrimmedPcm16(audio.samples, audio.sampleRate, segment.pauseMs)
        if (gen == generation && rev == synthRevision && pcm.isNotEmpty()) {
            if (flushBeforePlay) {
                activeQueue?.clear()
                flushRequested = true
            }
            val chunk = QueuedChunk(page = page, index = index, pcm = pcm, flushBeforePlay = flushBeforePlay)
            enqueueChunk(chunk, gen, rev)
        }
    }

    private fun enqueueChunk(chunk: QueuedChunk, gen: Int, rev: Int) {
        val queue = activeQueue ?: return
        while (gen == generation && rev == synthRevision) {
            if (queue.offer(chunk, QUEUE_POLL_MS, TimeUnit.MILLISECONDS)) return
        }
    }

    @Suppress("LongParameterList")
    private fun consumeAudioQueue(
        t: AudioTrack,
        rateHz: Int,
        initialPage: Int,
        queue: ArrayBlockingQueue<QueuedChunk>,
        onSegment: (Int, Int) -> Unit,
        onPageAdvance: (Int) -> Unit,
        gen: Int
    ) {
        var writtenFrames = 0L
        var activePage = initialPage
        var eos = false
        while (isActive(gen, generation) && !eos) {
            val chunk = queue.poll(QUEUE_POLL_MS, TimeUnit.MILLISECONDS)
            if (chunk != null && chunk.endOfStream) {
                drainTrack(t, writtenFrames, rateHz, gen)
                if (flushRequested) {
                    flushRequested = false
                    writtenFrames = flushAndRestartTrack(t)
                } else {
                    eos = true
                }
            } else if (chunk != null) {
                if (chunk.flushBeforePlay || flushRequested) {
                    flushRequested = false
                    writtenFrames = flushAndRestartTrack(t)
                }
                if (chunk.page != activePage) {
                    activePage = chunk.page
                    val newPage = chunk.page
                    mainHandler.post { onPageAdvance(newPage) }
                }
                playingPage = chunk.page
                playingIndex = chunk.index
                onSegment(chunk.page, chunk.index)
                writtenFrames += writeChunkSlices(t, chunk.pcm, gen)
            }
        }
    }

    private fun writeChunkSlices(t: AudioTrack, pcm: ShortArray, gen: Int): Long {
        var offset = 0
        var writtenTotal = 0L
        while (offset < pcm.size && isActive(gen, generation) && !flushRequested) {
            val count = (pcm.size - offset).coerceAtMost(WRITE_SLICE_SAMPLES)
            val written = t.write(pcm, offset, count, AudioTrack.WRITE_BLOCKING)
            if (written <= 0) break
            offset += written
            writtenTotal += written
        }
        return writtenTotal
    }

    private fun drainTrack(t: AudioTrack, writtenFrames: Long, rateHz: Int, gen: Int) {
        if (writtenFrames <= 0L) return
        val playedAtStart = playedFrames(t)
        val remainingFrames = (writtenFrames - playedAtStart).coerceAtLeast(0L)
        val maxWaitMs = (remainingFrames * MILLIS_PER_SECOND / rateHz + DRAIN_GRACE_MS)
            .coerceAtMost(MAX_DRAIN_WAIT_MS)
        val deadline = SystemClock.uptimeMillis() + maxWaitMs
        while (isActive(gen, generation) && !flushRequested && isStillDraining(t, writtenFrames, deadline)) {
            try {
                Thread.sleep(DRAIN_POLL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private companion object {
        const val TAG = "Narrator"
        const val QUEUE_CAPACITY = 4
        const val QUEUE_POLL_MS = 40L
        const val WRITE_SLICE_SAMPLES = 512
        const val MILLIS_PER_SECOND = 1000L
        const val DRAIN_POLL_MS = 20L
        const val DRAIN_GRACE_MS = 80L
        const val MAX_DRAIN_WAIT_MS = 8_000L

        val mainHandler = Handler(Looper.getMainLooper())
    }
}

private const val QUARTER_SECOND_DIVISOR = 4
private const val BYTES_PER_SAMPLE = 2
private const val MIN_BUFFER_FALLBACK = 4096
private const val HEAD_MASK = 0xFFFFFFFFL

private fun isActive(gen: Int, generation: Int): Boolean = gen == generation && !Thread.currentThread().isInterrupted

private fun playedFrames(t: AudioTrack): Long =
    runCatching { t.playbackHeadPosition.toLong() and HEAD_MASK }.getOrDefault(0L)

private fun isStillDraining(t: AudioTrack, writtenFrames: Long, deadline: Long): Boolean =
    SystemClock.uptimeMillis() < deadline && playedFrames(t) < writtenFrames

private fun flushAndRestartTrack(t: AudioTrack): Long {
    runCatching {
        t.pause()
        t.flush()
        t.play()
    }
    return playedFrames(t)
}

private fun createAudioTrack(sampleRate: Int): AudioTrack {
    val minBuffer = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(MIN_BUFFER_FALLBACK)
    val bufferBytes = (sampleRate / QUARTER_SECOND_DIVISOR * BYTES_PER_SAMPLE).coerceAtLeast(minBuffer * 2)
    return AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                // CONTENT_TYPE_MUSIC avoids OEM speech-call DSP effects (such as Samsung AudioVoiceBooster)
                // that aggressively boost 3–8 kHz and inject high-frequency hiss into neural TTS audio.
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(bufferBytes)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()
}
