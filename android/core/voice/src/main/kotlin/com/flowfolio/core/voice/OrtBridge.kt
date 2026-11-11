package com.flowfolio.core.voice

/**
 * One ONNX model on the runtime sherpa-onnx ships, through FlowFolio's own C API bridge (`src/main/cpp`, ADR-0054).
 * Used for OCR; Narrator Mode keeps going through sherpa-onnx. Not thread-safe: one caller at a time.
 */
class OrtBridge(path: String, threads: Int) : AutoCloseable {
    private var handle: Long = nativeCreate(path, threads)

    /** Runs the model on [input] shaped [shape]; returns its first output and that output's shape. */
    fun run(input: FloatArray, shape: LongArray): Pair<FloatArray, LongArray> {
        check(handle != 0L) { "model closed" }
        val shaped = LongArray(MAX_RANK + 1)
        val data = nativeRun(handle, input, shape, shaped)
        val rank = shaped[0].toInt()
        return data to shaped.copyOfRange(1, rank + 1)
    }

    override fun close() {
        if (handle != 0L) nativeClose(handle)
        handle = 0L
    }

    private companion object {
        const val MAX_RANK = 8

        init {
            System.loadLibrary("onnxruntime")
            System.loadLibrary("flowfolio_ort")
        }

        @JvmStatic
        external fun nativeCreate(path: String, threads: Int): Long

        @JvmStatic
        external fun nativeRun(handle: Long, input: FloatArray, shape: LongArray, shapeOut: LongArray): FloatArray

        @JvmStatic
        external fun nativeClose(handle: Long)
    }
}
