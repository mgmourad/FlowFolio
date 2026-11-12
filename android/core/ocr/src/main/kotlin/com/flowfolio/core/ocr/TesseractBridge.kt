package com.flowfolio.core.ocr

/**
 * The C API bridge to Tesseract (`src/main/cpp`, ADR-0054): init an engine on a tessdata directory, read a page of
 * raw pixels, and get its text lines with boxes and confidence. Not thread-safe: one caller at a time.
 */
class TesseractBridge(datapath: String, lang: String) : AutoCloseable {
    private var handle: Long = nativeCreate(datapath, lang)

    /** Reads [pixels] (ARGB, row by row, [width] x [height]); returns text[], boxes[], confidences[]. */
    fun read(pixels: IntArray, width: Int, height: Int): Triple<Array<String>, IntArray, FloatArray> {
        check(handle != 0L) { "engine closed" }
        val result = nativeRead(handle, pixels, width, height)
        return Triple(result[0] as Array<String>, result[1] as IntArray, result[2] as FloatArray)
    }

    override fun close() {
        if (handle != 0L) nativeClose(handle)
        handle = 0L
    }

    private companion object {
        init {
            System.loadLibrary("leptonica")
            System.loadLibrary("tesseract")
            System.loadLibrary("flowfolio_tesseract")
        }

        @JvmStatic
        external fun nativeCreate(datapath: String, lang: String): Long

        @JvmStatic
        external fun nativeRead(handle: Long, pixels: IntArray, width: Int, height: Int): Array<Any>

        @JvmStatic
        external fun nativeClose(handle: Long)
    }
}
