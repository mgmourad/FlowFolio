package com.flowfolio.feature.reader.riffle

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * The scrubber's paper sounds (ADR-0025): the owner's recordings, prepared by
 * `tools/sounds/prepare_recorded_sounds.py`. While the ribbon drags, the riffle forward or backward recording loops,
 * louder and quicker the faster pages pass; it fades out when the pages stop or the ribbon is let go. Each landing
 * plays a page flip in its direction. The reader's own switches and sliders are the only control: the sounds play
 * whatever the phone's ringer or Do Not Disturb mode (owner, 2026-11-07). [release] when the reader closes.
 *
 * The sounds play on the media stream (ADR-0032), which the volume keys control in the reader, rather than as system
 * sounds, which some phones route elsewhere or mute separately (a flip that went silent until the phone restarted,
 * owner 2026-10-22). A pool that stops playing (the audio service restarted) is built again.
 */
class RiffleSounds(context: Context) {
    private val app = context.applicationContext
    private var pool = newPool()
    private val loaded = HashSet<Int>()
    private var riffleForward = 0
    private var riffleBackward = 0
    private var flipForward = 0
    private var flipBackward = 0
    private val handler = Handler(Looper.getMainLooper())

    init {
        loadAll()
    }

    private fun newPool(): SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
        .also {
            it.setOnLoadCompleteListener { _, id, status ->
                if (status ==
                    0
                ) {
                    synchronized(loaded) { loaded += id }
                }
            }
        }

    private fun loadAll() {
        riffleForward = pool.load(app, R.raw.riffle_forward, 1)
        riffleBackward = pool.load(app, R.raw.riffle_backward, 1)
        flipForward = pool.load(app, R.raw.flip_forward, 1)
        flipBackward = pool.load(app, R.raw.flip_backward, 1)
    }

    /**
     * Plays [sound]; a pool that refuses a loaded sound is broken (its audio tracks died with the audio service), so it
     * is built again, and the sound plays once the new one has loaded.
     */
    private fun start(sound: Int, volume: Float, priority: Int, loop: Int, rate: Float): Int {
        val id = pool.play(sound, volume, volume, priority, loop, rate)
        if (id == 0 && synchronized(loaded) { sound in loaded }) {
            Log.w(TAG, "the sound pool stopped playing; building it again")
            rebuild()
        }
        return id
    }

    private fun rebuild() {
        stopNow()
        pool.release()
        synchronized(loaded) { loaded.clear() }
        pool = newPool()
        loadAll()
    }

    /** The riffle's and the flips' loudness, 0 to 1 (the reader's settings), applied on top of the speed. */
    var riffleVolume = 1f
    var flipVolume = 1f

    /** The looping riffle (0 when none), which way it runs, and its loudness and speed in units a second. */
    private var stream = 0
    private var streamForward = true
    private var volume = 0f
    private var speed = 0f
    private var lastStep = 0L

    private val fade = object : Runnable {
        override fun run() {
            volume *= FADE_FACTOR
            if (volume < SILENT || stream == 0) {
                stopNow()
            } else {
                pool.setVolume(stream, volume, volume)
                handler.postDelayed(this, FADE_STEP_MS)
            }
        }
    }

    /** One step of a drag: keeps the riffle going in the step's direction, at a loudness and pitch for its speed. */
    fun play(feedback: RiffleFeedback) {
        if (feedback.units == 0) return
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastStep).coerceIn(MIN_STEP_MS, MAX_STEP_MS) / MS_PER_S
        lastStep = now
        val instant = feedback.units / dt
        speed = if (stream == 0) instant else speed + (instant - speed) * SMOOTHING
        if (stream == 0 || streamForward != feedback.forward) {
            stopNow()
            streamForward = feedback.forward
            stream = start(if (streamForward) riffleForward else riffleBackward, 0f, 1, -1, 1f)
        }
        volume = (MIN_VOLUME + speed / LOUD_SPEED).coerceAtMost(MAX_VOLUME) * riffleVolume
        pool.setVolume(stream, volume, volume)
        pool.setRate(stream, (speed / NATURAL_SPEED).coerceIn(MIN_RATE, MAX_RATE))
        // No step for a moment: the pages have stopped, so the riffle fades.
        handler.removeCallbacks(fade)
        handler.postDelayed(fade, IDLE_MS)
    }

    /** The ribbon was let go: the riffle fades out. */
    fun stopRiffle() {
        handler.removeCallbacks(fade)
        if (stream != 0) handler.post(fade)
    }

    /** A landing: a page flip toward the end of the book ([forward]) or back toward its start. */
    fun flip(forward: Boolean) {
        val v = FLIP_VOLUME * flipVolume
        start(if (forward) flipForward else flipBackward, v, 2, 0, 1f)
    }

    /** A moment of the riffle at its natural pace, so a volume slider can be heard. */
    fun previewRiffle() {
        stopNow()
        volume = PREVIEW_VOLUME * riffleVolume
        stream = start(riffleForward, volume, 1, -1, 1f)
        handler.postDelayed(fade, PREVIEW_MS)
    }

    private fun stopNow() {
        handler.removeCallbacks(fade)
        if (stream != 0) pool.stop(stream)
        stream = 0
        volume = 0f
    }

    fun release() {
        stopNow()
        pool.release()
    }

    private companion object {
        const val TAG = "FlowFolioSounds"
        const val MAX_STREAMS = 3
        const val MS_PER_S = 1_000f
        const val MIN_STEP_MS = 8L
        const val MAX_STEP_MS = 200L

        /** How quickly the measured speed follows the finger (per step). */
        const val SMOOTHING = 0.25f

        /** The speed (units a second) the recordings were made at: played faster or slower around it. */
        const val NATURAL_SPEED = 25f
        const val MIN_RATE = 0.75f
        const val MAX_RATE = 1.6f
        const val MIN_VOLUME = 0.35f
        const val LOUD_SPEED = 60f
        const val MAX_VOLUME = 0.9f
        const val IDLE_MS = 120L
        const val FADE_STEP_MS = 30L
        const val FADE_FACTOR = 0.55f
        const val SILENT = 0.04f
        const val FLIP_VOLUME = 0.8f
        const val PREVIEW_VOLUME = 0.6f
        const val PREVIEW_MS = 700L
    }
}
