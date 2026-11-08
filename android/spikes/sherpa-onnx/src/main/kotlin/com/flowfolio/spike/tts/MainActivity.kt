package com.flowfolio.spike.tts

import android.app.Activity
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import java.io.File

/**
 * Confirms sherpa-onnx (Kokoro) loads from absolute file paths and synthesizes. The model files
 * (model.int8.onnx, voices.bin, tokens.txt, lexicon-en-us.txt) are adb-pushed to
 * `getExternalFilesDir(null)/kokoro/` before launch.
 */
class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        status = TextView(this).apply { text = "Idle" }
        layout.addView(status)
        layout.addView(Button(this).apply {
            text = "Synthesize Kokoro (am_michael + af)"
            setOnClickListener { runSynthesis() }
        })
        setContentView(layout)
    }

    private fun runSynthesis() {
        setStatus("Loading model…")
        Thread {
            try {
                synthesize()
            } catch (t: Throwable) {
                Log.e(TAG, "synthesis failed", t)
                setStatus("FAILED: ${t.javaClass.simpleName}: ${t.message}")
            }
        }.start()
    }

    private fun synthesize() {
        val modelDir = File(getExternalFilesDir(null), "kokoro").absolutePath
        setStatus("modelDir=$modelDir")

        val t0 = System.currentTimeMillis()
        val config = getOfflineTtsConfig(
            modelDir = modelDir,
            modelName = "model.int8.onnx",
            voices = "voices.bin",
            lexicon = "lexicon-en-us.txt",
        )
        val tts = OfflineTts(assetManager = null, config = config)
        val loadMs = System.currentTimeMillis() - t0
        Log.i(TAG, "loaded in ${loadMs}ms, sampleRate=${tts.sampleRate()}, speakers=${tts.numSpeakers()}, lang=${tts.lang()}")
        setStatus("Loaded (${loadMs}ms) rate=${tts.sampleRate()} speakers=${tts.numSpeakers()} lang=${tts.lang()}")

        val sentence = "The easiest thing in the world was to lose touch with someone, because life was changing so fast."

        for (sid in intArrayOf(6, 0)) { // am_michael, af
            val g = System.currentTimeMillis()
            val audio = tts.generate(sentence, sid = sid, speed = 1.0f)
            val genMs = System.currentTimeMillis() - g
            val durationMs = audio.samples.size * 1000L / audio.sampleRate
            val wav = File(getExternalFilesDir(null), "kokoro_sid$sid.wav")
            val saved = audio.save(wav.absolutePath)
            Log.i(TAG, "sid=$sid samples=${audio.samples.size} rate=${audio.sampleRate} dur=${durationMs}ms gen=${genMs}ms saved=$saved size=${wav.length()}")
            setStatus("sid=$sid dur=${durationMs}ms gen=${genMs}ms saved=$saved (${wav.length()} bytes)")
            play(audio.samples, audio.sampleRate)
        }

        setStatus("Done. am_michael + af synthesized.")
        tts.release()
    }

    private fun play(samples: FloatArray, sampleRate: Int) {
        val pcm = ShortArray(samples.size)
        for (i in samples.indices) {
            val v = samples[i].coerceIn(-1f, 1f)
            pcm[i] = (v * Short.MAX_VALUE).toInt().toShort()
        }
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
        track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
        track.stop()
        track.release()
    }

    private fun setStatus(text: String) {
        runOnUiThread { status.text = text }
    }

    companion object {
        private const val TAG = "SherpaSpike"
    }
}
