package com.flowfolio.core.data

import android.content.Context
import com.flowfolio.core.data.ocr.OcrPack
import com.flowfolio.core.data.ocr.OcrPackState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The optional downloads the app offers after its first launch (TM2, ADR-0057). */
enum class Resource { SCANNED_BOOKS, DICTIONARY }

/**
 * The optional resources in one place, for the first-launch offer and Settings: what each costs, where it stands,
 * and downloading a chosen set in the background, one after another, so leaving the offer does not stop them.
 */
@Singleton
class ResourceDownloads @Inject constructor(
    @ApplicationContext context: Context,
    private val ocr: OcrPack,
    private val wordNet: WordNetPack
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Narrator Mode's own voices (Kokoro and Piper, up to 400 MB) were retired for the phone's speech engine
        // (ADR-0059): their files go. Only the external folder held them; VoiceMarks live in filesDir/voice.
        context.getExternalFilesDir(null)?.let { external ->
            scope.launch { File(external, "voice").takeIf { it.exists() }?.deleteRecursively() }
        }
    }

    /** The Full edition carries every pack: nothing to offer. */
    val bundled: Boolean = BundledPacks.present(context)

    /** Puts the Full edition's packs in place (first start; seconds), then reports them ready. */
    fun installBundled(context: Context) {
        if (!bundled) return
        scope.launch {
            BundledPacks.install(context)
            ocr.recheck()
            wordNet.recheck()
        }
    }

    /** Download sizes in bytes. */
    fun sizeOf(resource: Resource): Long = when (resource) {
        Resource.SCANNED_BOOKS -> ocr.sizeBytes
        Resource.DICTIONARY -> WordNetPack.SIZE
    }

    fun state(resource: Resource): StateFlow<ResourceState> = when (resource) {
        Resource.SCANNED_BOOKS -> ocr.state.map {
            it.asResource()
        }.stateIn(scope, SharingStarted.Eagerly, ocr.state.value.asResource())

        Resource.DICTIONARY -> wordNet.state
    }

    /**
     * Downloads [chosen] in the background, side by side (2026-11-12): one after another, the 8 MB dictionary waited
     * behind the rest.
     */
    fun download(chosen: Set<Resource>) {
        for (r in Resource.entries.filter { it in chosen }) {
            scope.launch {
                when (r) {
                    Resource.SCANNED_BOOKS -> ocr.download()
                    Resource.DICTIONARY -> wordNet.download()
                }
            }
        }
    }

    private fun OcrPackState.asResource(): ResourceState = when (this) {
        OcrPackState.Missing -> ResourceState.Missing
        is OcrPackState.Downloading -> ResourceState.Downloading(fraction)
        OcrPackState.Ready -> ResourceState.Ready
        OcrPackState.Failed -> ResourceState.Failed
    }
}
