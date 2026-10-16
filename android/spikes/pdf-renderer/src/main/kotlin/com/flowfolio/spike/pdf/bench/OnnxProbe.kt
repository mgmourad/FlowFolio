package com.flowfolio.spike.pdf.bench

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.os.Debug
import android.os.SystemClock
import java.io.File
import java.nio.LongBuffer
import kotlin.math.roundToInt
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

/**
 * Times a small transformer (all-MiniLM-L6-v2) with ONNX Runtime: session load, then inference at
 * batch 1 and batch 8, sequence length 128, on the default CPU provider and on XNNPACK.
 * Inputs are synthetic token ids; tokenization cost is negligible next to inference.
 */
class OnnxProbe(private val modelsDir: File, private val log: (String) -> Unit) {
    fun run(): JSONObject {
        val env = OrtEnvironment.getEnvironment()
        val result = JSONObject()
        // Known names: an adb-created folder can't be listed by the app on Android 11+, but its files can be opened.
        val models = listOf("model_qint8_arm64.onnx", "model.onnx").map { File(modelsDir, it) }.filter { it.exists() }
        require(models.isNotEmpty()) { "no models in $modelsDir" }
        for (model in models) {
            for (provider in listOf("cpu", "xnnpack")) {
                val key = "${model.nameWithoutExtension}/$provider"
                result.put(key, runOne(env, model, provider))
                log("onnx $key done")
            }
        }
        return result
    }

    private fun runOne(env: OrtEnvironment, model: File, provider: String): JSONObject {
        val pssBefore = Debug.getPss()
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            if (provider == "xnnpack") addXnnpack(mapOf("intra_op_num_threads" to "4"))
        }
        val t0 = SystemClock.elapsedRealtimeNanos()
        val session = env.createSession(model.absolutePath, options)
        val loadMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
        val out = JSONObject().put("modelBytes", model.length()).put("loadMs", loadMs.roundToInt())
        session.use { s ->
            for (batch in listOf(1, 8)) {
                val times = ArrayList<Double>()
                repeat(3 + 30) { i ->
                    val ms = infer(env, s, batch, 128)
                    if (i >= 3) times += ms // first 3 are warm-up
                }
                times.sort()
                out.put(
                    "batch$batch",
                    JSONObject()
                        .put("p50Ms", "%.1f".format(times[times.size / 2]).toDouble())
                        .put(
                            "p95Ms",
                            "%.1f".format(times[(times.size * 0.95).toInt().coerceAtMost(times.size - 1)]).toDouble()
                        )
                        .put("sequencesPerSec", "%.1f".format(batch * 1000.0 / times[times.size / 2]).toDouble())
                )
            }
        }
        out.put("pssDeltaKb", Debug.getPss() - pssBefore)
        out.put("inputs", JSONArray(listOf("input_ids", "attention_mask", "token_type_ids")))
        return out
    }

    private fun infer(env: OrtEnvironment, session: OrtSession, batch: Int, seq: Int): Double {
        val shape = longArrayOf(batch.toLong(), seq.toLong())
        val rnd = Random(7)
        val ids = LongArray(batch * seq) { if (it % seq == 0) 101L else rnd.nextLong(1000, 30000) }
        val ones = LongArray(batch * seq) { 1L }
        val zeros = LongArray(batch * seq)
        OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape).use { tIds ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(ones), shape).use { tMask ->
                OnnxTensor.createTensor(env, LongBuffer.wrap(zeros), shape).use { tType ->
                    val inputs = mapOf("input_ids" to tIds, "attention_mask" to tMask, "token_type_ids" to tType)
                    val t0 = SystemClock.elapsedRealtimeNanos()
                    session.run(inputs).use { }
                    return (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
                }
            }
        }
    }
}
