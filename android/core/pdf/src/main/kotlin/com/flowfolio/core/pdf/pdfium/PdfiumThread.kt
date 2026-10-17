package com.flowfolio.core.pdf.pdfium

import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * PDFium is not thread-safe, process-wide. Every native call runs on this one thread, for every document.
 * Rendering one page takes ~10-25 ms on the reference device, so a single thread keeps up with page turns.
 */
internal object PdfiumThread {
    @Volatile
    private var thread: Thread? = null

    val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "pdfium").also {
            it.isDaemon = true
            thread = it
        }
    }.asCoroutineDispatcher()

    fun isCurrent(): Boolean = Thread.currentThread() === thread
}
