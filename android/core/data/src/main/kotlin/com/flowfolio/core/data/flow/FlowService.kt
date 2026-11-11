package com.flowfolio.core.data.flow

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.BatteryManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.flowfolio.core.conversion.ConversionException
import com.flowfolio.core.conversion.Converter
import com.flowfolio.core.conversion.epub.SourceInfo
import com.flowfolio.core.data.R
import com.flowfolio.core.model.FlowFailure
import com.flowfolio.core.model.FlowPause
import com.flowfolio.core.pdf.PdfException
import com.flowfolio.core.pdf.pdfium.PdfiumConversionSource
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import kotlinx.serialization.json.Json

/**
 * Converts PDFs to their Dynamic Flow bundles (ADR-0045), in its own process (`:convert`, so PDFium parsing an
 * untrusted file can't take the app down) as a foreground service with a progress notification. One book at a time; the
 * others wait in order. It pauses while the phone is hot or the battery is low and not charging, and tells the app how
 * far it is with broadcasts only the app receives ([FlowProtocol]). If the process is stopped, the book converts again
 * from the start the next time it is asked for.
 */
class FlowService : Service() {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "flow-convert") }

    /** Books started and not finished, in order, with their titles. */
    private val queued = LinkedHashMap<String, String>()
    private lateinit var notifications: FlowNotifications
    private lateinit var files: FlowFiles

    /** The newest start request: stopping by it never stops a service a later book has just been sent to. */
    @Volatile private var lastStartId = 0

    override fun onCreate() {
        super.onCreate()
        notifications = FlowNotifications(this)
        files = FlowFiles(filesDir)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val bookId = intent?.getStringExtra(FlowProtocol.EXTRA_BOOK_ID)
        if (intent?.action != FlowProtocol.ACTION_CONVERT || bookId == null) {
            if (synchronized(queued) { queued.isEmpty() }) stopSelf(startId)
            return START_NOT_STICKY
        }
        val title = intent.getStringExtra(FlowProtocol.EXTRA_TITLE).orEmpty()
        lastStartId = startId
        // Foreground straight away (Android requires it within seconds of starting), on the first book's notification.
        startForeground(
            notifications.idOf(bookId),
            notifications.progress(bookId, title, 0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        val fresh = synchronized(queued) { queued.put(bookId, title) == null }
        if (fresh) {
            if (synchronized(queued) { queued.size } > 1) send(FlowProtocol.progress(packageName, bookId, -1f, null))
            val job =
                Job(
                    bookId,
                    title,
                    intent.getStringExtra(FlowProtocol.EXTRA_FILE),
                    intent.getStringExtra(FlowProtocol.EXTRA_URI)
                )
            worker.execute { run(job) }
        }
        return START_NOT_STICKY
    }

    private class Job(val bookId: String, val title: String, val file: String?, val uri: String?)

    // Whatever stops a conversion, the app hears how it ended and the partial bundle goes: the last catch is that net.
    @Suppress("TooGenericExceptionCaught")
    private fun run(job: Job) {
        val started = SystemClock.elapsedRealtime()
        val failure = try {
            convert(job)
            null
        } catch (_: PdfException.PasswordRequired) {
            FlowFailure.PASSWORD
        } catch (_: FileNotFoundException) {
            FlowFailure.MISSING
        } catch (e: ConversionException) {
            if (e.reason == ConversionException.Reason.NO_PAGES) FlowFailure.NO_PAGES else FlowFailure.OTHER
        } catch (_: PdfException) {
            FlowFailure.DAMAGED
        } catch (e: VirtualMachineError) {
            // Out of memory, or a stack overflow on a deeply nested PDF: either way the thread must still report.
            Log.w(TAG, "${e.javaClass.simpleName} converting ${job.bookId}", e)
            FlowFailure.OTHER
        } catch (_: InterruptedException) {
            // The service is going away (onDestroy reports every unfinished book).
            return
        } catch (e: RuntimeException) {
            Log.w(TAG, "failed to convert ${job.bookId}", e)
            FlowFailure.OTHER
        }
        Log.i(TAG, "FF_FLOW ${job.bookId} ${failure ?: "ok"} in ${SystemClock.elapsedRealtime() - started} ms")
        if (failure != null) files.delete(job.bookId)
        send(FlowProtocol.done(packageName, job.bookId, failure))
        leaveForeground(job)
        // Posted after leaving the foreground: the result shares the progress notification's id, and removing the
        // foreground notification would take it away too.
        when (failure) {
            null -> notifications.showDone(job.bookId, job.title)
            else -> notifications.showFailed(job.bookId, job.title)
        }
    }

    /** [job] is done: the next book's progress takes over the foreground notification, or the service stops. */
    private fun leaveForeground(job: Job) {
        val empty = synchronized(queued) {
            queued.remove(job.bookId)
            queued.isEmpty()
        }
        if (empty) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(lastStartId)
        } else {
            // The next book's progress takes over the foreground notification.
            val (next, nextTitle) = synchronized(queued) { queued.entries.first().toPair() }
            startForeground(
                notifications.idOf(next),
                notifications.progress(next, nextTitle, 0),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }
    }

    private fun convert(job: Job) {
        val fd = open(job) ?: throw FileNotFoundException("no file for ${job.bookId}")
        files.dir(job.bookId).mkdirs()
        val progress = Progress(job)
        val result = fd.use { pfd ->
            PdfiumConversionSource.open(pfd).use { pdf ->
                Converter().convertProgressively(
                    pdf,
                    SourceInfo(job.title.ifBlank { job.bookId }, job.bookId, pdf.pageCount, pdf.metadata("Title")),
                    Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
                    progress::on
                ) { epub, _, finished, total ->
                    progress.chapter(finished, total)
                    writeAtomically(files.partial(job.bookId), epub)
                }
            }
        }
        // The sidecar is written only once the EPUB is in place: a re-converting book whose old bundle is still on
        // disk must not read as `ready()` on the strength of a partial sidecar carrying the new engine version.
        if (!files.partial(job.bookId).renameTo(files.epub(job.bookId))) {
            throw FileNotFoundException("could not finish ${job.bookId}")
        }
        writeAtomically(files.sidecar(job.bookId), JSON.encodeToString(result.sidecar).toByteArray())
    }

    /** Writes [bytes] to [target] without leaving it half-written: a sibling temp, then an atomic rename over it. */
    private fun writeAtomically(target: File, bytes: ByteArray) {
        val tmp = File(target.path + ".tmp")
        tmp.writeBytes(bytes)
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun open(job: Job): ParcelFileDescriptor? = when {
        job.file != null -> File(job.file).takeIf { it.canRead() }
            ?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }

        job.uri != null -> runCatching { contentResolver.openFileDescriptor(Uri.parse(job.uri), "r") }.getOrNull()

        else -> null
    }

    /** Turns the converter's stages into one fraction, tells the app and the notification, and pauses when it must. */
    private inner class Progress(private val job: Job) {
        private var lastSent = 0L
        private var lastPercent = -1
        private var lastFraction = 0f
        private var chapters = 0
        private var totalChapters = 0

        fun on(p: Converter.Progress) {
            var pause = pauseReason()
            while (pause != null) {
                report(fraction(p), pause, force = true)
                Thread.sleep(PAUSE_CHECK_MS)
                pause = pauseReason()
            }
            report(fraction(p), null, force = false)
        }

        /** A chapter finished: the book is readable through [finished] of [total]; tell the app now. */
        fun chapter(finished: Int, total: Int) {
            chapters = finished
            totalChapters = total
            report(lastFraction, null, force = true)
        }

        private fun report(fraction: Float, pause: FlowPause?, force: Boolean) {
            val now = SystemClock.elapsedRealtime()
            val percent = (fraction * PERCENT).toInt()
            if (!force && (percent == lastPercent || now - lastSent < REPORT_MS)) return
            lastSent = now
            lastPercent = percent
            lastFraction = fraction
            send(FlowProtocol.progress(packageName, job.bookId, fraction, pause, chapters, totalChapters))
            val pausedText = pause?.let {
                getString(
                    if (it ==
                        FlowPause.HOT
                    ) {
                        R.string.flow_paused_hot
                    } else {
                        R.string.flow_paused_battery
                    }
                )
            }
            notifications.showProgress(job.bookId, job.title, percent, pausedText)
        }
    }

    /** Hot (severe thermal status or worse) or a low battery that isn't charging: wait before going on. */
    private fun pauseReason(): FlowPause? {
        val power = getSystemService(PowerManager::class.java)
        if (power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) return FlowPause.HOT
        val battery = getSystemService(BatteryManager::class.java)
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (level in 0..LOW_BATTERY && !battery.isCharging) FlowPause.BATTERY else null
    }

    private fun send(intent: Intent) = sendBroadcast(intent)

    /** Android 15's limit on data-sync foreground services ran out: stop now, as it asks (onDestroy reports). */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "FF_FLOW foreground time ran out")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        // Books still waiting or converting will not finish here: the app hears so and can ask again.
        val unfinished = synchronized(queued) { queued.keys.toList().also { queued.clear() } }
        unfinished.forEach { send(FlowProtocol.done(packageName, it, FlowFailure.INTERRUPTED)) }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FlowFolioFlow"
        private const val REPORT_MS = 400L
        private const val PAUSE_CHECK_MS = 5_000L
        private const val LOW_BATTERY = 15
        private const val PERCENT = 100
        private val JSON = Json { prettyPrint = false }

        /**
         * Where each stage ends, as a share of the whole: measured on the textbook (reading 41%, layout 5%, images 52%,
         * writing 2%).
         */
        private val STAGE_END = mapOf("reading" to 0.41f, "layout" to 0.46f, "images" to 0.98f, "writing" to 1f)
        private val STAGE_START = mapOf("reading" to 0f, "layout" to 0.41f, "images" to 0.46f, "writing" to 0.98f)

        internal fun fraction(p: Converter.Progress): Float {
            val start = STAGE_START[p.stage] ?: 0f
            val end = STAGE_END[p.stage] ?: 1f
            val within = if (p.total > 0) p.done.toFloat() / p.total else 0f
            return (start + (end - start) * within).coerceIn(0f, 1f)
        }

        /** Starts converting [bookId] (read from [file] or [uri]); it waits its turn behind books already started. */
        fun start(context: Context, bookId: String, title: String, file: String?, uri: String?) {
            val intent = Intent(context, FlowService::class.java)
                .setAction(FlowProtocol.ACTION_CONVERT)
                .putExtra(FlowProtocol.EXTRA_BOOK_ID, bookId)
                .putExtra(FlowProtocol.EXTRA_TITLE, title)
                .putExtra(FlowProtocol.EXTRA_FILE, file)
                .putExtra(FlowProtocol.EXTRA_URI, uri)
            context.startForegroundService(intent)
        }
    }
}
