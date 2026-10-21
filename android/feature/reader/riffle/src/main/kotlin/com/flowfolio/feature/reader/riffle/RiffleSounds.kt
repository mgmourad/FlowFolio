package com.flowfolio.feature.reader.riffle

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * The scrubber's paper sounds (ADR-0025): the owner's recordings, prepared by
 * `tools/sounds/prepare_recorded_sounds.py`. While the ribbon drags, the riffle forward or backward recording loops,
 * louder and quicker the faster pages pass; it fades out when the pages stop or the ribbon is let go. Each landing
 * plays a page flip in its direction. Silent in silent and vibrate ringer modes. [release] when the reader closes.
 */
class RiffleSounds(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val riffleForward = pool.load(context, R.raw.riffle_forward, 1)
    private val riffleBackward = pool.load(context, R.raw.riffle_backward, 1)
    private val flipForward = pool.load(context, R.raw.flip_forward, 1)
    private val flipBackward = pool.load(context, R.raw.flip_backward, 1)
    private val handler = Handler(Looper.getMainLooper())

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
        if (!audible()) {
            stopNow()
            return
        }
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastStep).coerceIn(MIN_STEP_MS, MAX_STEP_MS) / MS_PER_S
        lastStep = now
        val instant = feedback.units / dt
        speed = if (stream == 0) instant else speed + (instant - speed) * SMOOTHING
        if (stream == 0 || streamForward != feedback.forward) {
            stopNow()
            streamForward = feedback.forward
            stream = pool.play(if (streamForward) riffleForward else riffleBackward, 0f, 0f, 1, -1, 1f)
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
        if (!audible()) return
        val v = FLIP_VOLUME * flipVolume
        pool.play(if (forward) flipForward else flipBackward, v, v, 2, 0, 1f)
    }

    /** A moment of the riffle at its natural pace, so a volume slider can be heard. */
    fun previewRiffle() {
        if (!audible()) return
        stopNow()
        volume = PREVIEW_VOLUME * riffleVolume
        stream = pool.play(riffleForward, volume, volume, 1, -1, 1f)
        handler.postDelayed(fade, PREVIEW_MS)
    }

    private fun stopNow() {
        handler.removeCallbacks(fade)
        if (stream != 0) pool.stop(stream)
        stream = 0
        volume = 0f
    }

    private fun audible() = audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL

    fun release() {
        stopNow()
        pool.release()
    }

    private companion object {
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
