package com.flowfolio.core.pdf.pdfium

import android.graphics.Bitmap

/** JNI entry points in pdfium_jni.cpp. Call only from [PdfiumThread]. */
@Suppress("TooManyFunctions")
internal // one per native entry point
object PdfiumNative {
    init {
        System.loadLibrary("pdfium")
        System.loadLibrary("flowfolio_pdfium")
    }

    external fun nativeOpen(fd: Int, password: String?, errOut: IntArray): Long

    external fun nativeClose(handle: Long)

    external fun nativeReload(handle: Long): Boolean

    external fun nativePageCount(handle: Long): Int

    external fun nativePageSize(handle: Long, index: Int): FloatArray?

    @Suppress("LongParameterList") // mirrors the C++ signature one-to-one
    external fun nativeRender(
        handle: Long,
        index: Int,
        bitmap: Bitmap,
        pageWidth: Int,
        pageHeight: Int,
        offsetX: Int,
        offsetY: Int,
        /** 0: as printed; 1: tint; 2: invert lightness, then tint. */
        mode: Int,
        paper: Int,
        ink: Int
    ): Boolean

    external fun nativeOutline(handle: Long): Array<String>

    external fun nativePageLabel(handle: Long, index: Int): String?

    external fun nativeMetaText(handle: Long, tag: String): String?

    external fun nativeLinks(handle: Long, index: Int): Array<String>

    /** {String text, FloatArray boxes} for page [index] (see pdfium_jni.cpp), or null when unreadable. */
    external fun nativePageText(handle: Long, index: Int): Array<Any>?

    /** What the converter reads from page [index] (see pdfium_jni.cpp), or null when unreadable. */
    external fun nativeConversionPage(handle: Long, index: Int): Array<Any>?
}
