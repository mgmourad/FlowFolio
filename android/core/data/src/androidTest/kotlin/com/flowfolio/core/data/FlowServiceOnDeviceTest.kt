package com.flowfolio.core.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flowfolio.core.data.flow.FlowFiles
import com.flowfolio.core.data.flow.FlowProtocol
import com.flowfolio.core.data.flow.FlowService
import com.flowfolio.core.model.FlowFailure
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Dynamic Flow's conversion service (ADR-0045) for real: started like the app starts it, converting in its own process
 * (`:convert`), and answering with the broadcasts the app listens to. A good PDF leaves a whole bundle; every kind of
 * bad one says why and leaves nothing behind.
 */
@RunWith(AndroidJUnit4::class)
class FlowServiceOnDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = FlowFiles(context.filesDir)
    private lateinit var root: File
    private val done = ConcurrentHashMap<String, String>()
    private val progress = ConcurrentHashMap<String, MutableList<Float>>()
    private val latches = ConcurrentHashMap<String, CountDownLatch>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getStringExtra(FlowProtocol.EXTRA_BOOK_ID) ?: return
            when (intent.action) {
                FlowProtocol.ACTION_PROGRESS ->
                    progress.getOrPut(id) { java.util.Collections.synchronizedList(ArrayList()) } +=
                        intent.getFloatExtra(FlowProtocol.EXTRA_FRACTION, 0f)

                FlowProtocol.ACTION_DONE -> {
                    done[id] = intent.getStringExtra(FlowProtocol.EXTRA_FAILURE) ?: OK
                    latches[id]?.countDown()
                }
            }
        }
    }

    @Before
    fun setUp() {
        root = File(context.cacheDir, "flow-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val filter = IntentFilter().apply {
            addAction(FlowProtocol.ACTION_PROGRESS)
            addAction(FlowProtocol.ACTION_DONE)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    @After
    fun tearDown() {
        context.unregisterReceiver(receiver)
        root.deleteRecursively()
        BOOKS.keys.forEach(files::delete)
        files.delete(MISSING)
    }

    @Test
    fun aPdfBecomesAWholeBundleAndEveryFailureIsReportedAndCleanedUp() {
        // Started together, as when the reader turns Dynamic Flow on for several books: they convert one at a time.
        for ((id, asset) in BOOKS) start(id, fixture(asset).absolutePath)
        start(MISSING, File(root, "gone.pdf").absolutePath)
        for (id in BOOKS.keys + MISSING) {
            assertTrue("$id finished", latches.getValue(id).await(TIMEOUT_S, TimeUnit.SECONDS))
        }

        assertEquals(OK, done[GOOD])
        assertTrue(files.ready(GOOD))
        assertTrue(files.sidecar(GOOD).length() > 0)
        assertFalse("no partial file left", files.partial(GOOD).exists())
        ZipFile(files.epub(GOOD)).use { zip ->
            assertTrue(zip.getEntry("mimetype") != null)
            assertTrue(zip.entries().toList().any { it.name.endsWith(".xhtml") })
        }
        // The progress the app shows only ever moves forward.
        val steps = progress[GOOD].orEmpty().filter { it >= 0f }
        assertEquals(steps.sorted(), steps)

        assertEquals(FlowFailure.DAMAGED.name, done["not-a-pdf"])
        assertEquals(FlowFailure.PASSWORD.name, done["encrypted"])
        assertEquals(FlowFailure.NO_PAGES.name, done["zero-pages"])
        assertEquals(FlowFailure.MISSING.name, done[MISSING])
        (BOOKS.keys - GOOD + MISSING).forEach { assertFalse("$it left a bundle", files.dir(it).exists()) }
    }

    private fun start(id: String, file: String) {
        latches[id] = CountDownLatch(1)
        FlowService.start(context, id, id, file, null)
    }

    private fun fixture(name: String): File {
        val file = File(root, name)
        InstrumentationRegistry.getInstrumentation().context.assets.open("pdfs/$name").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return file
    }

    private companion object {
        const val OK = "ok"
        const val GOOD = "mixed-sizes"
        const val MISSING = "missing"
        const val TIMEOUT_S = 120L
        val BOOKS = mapOf(
            GOOD to "mixed-sizes.pdf",
            "not-a-pdf" to "not-a-pdf.pdf",
            "encrypted" to "encrypted-rc4.pdf",
            "zero-pages" to "zero-pages.pdf"
        )
    }
}
