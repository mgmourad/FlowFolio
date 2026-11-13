package com.flowfolio.core.data

import android.content.Context
import com.flowfolio.core.data.ocr.OcrPack
import com.flowfolio.core.data.ocr.OcrPackState
import com.flowfolio.core.voice.VoicePackDownloader
import com.flowfolio.core.voice.VoicePackState
import com.flowfolio.core.voice.VoicePacks
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The optional downloads the app offers after its first launch (TM2, ADR-0057). */
enum class Resource { SCANNED_BOOKS, VOICES, DICTIONARY }

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

    /** Where the reader keeps Narrator's default voices (Kokoro), so a download here is the reader's too. */
    private val voiceDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "voice")
    private val voices = VoicePackDownloader(VoicePacks.kokoroMultiLang, voiceDir)
    private val voicesReady = MutableStateFlow(VoicePacks.kokoroMultiLang.isReady(voiceDir))

    /** The Full edition carries every pack: nothing to offer. */
    val bundled: Boolean = BundledPacks.present(context)

    /** Puts the Full edition's packs in place (first start; seconds), then reports them ready. */
    fun installBundled(context: Context) {
        if (!bundled) return
        scope.launch {
            BundledPacks.install(context, voiceDir)
            ocr.recheck()
            wordNet.recheck()
            voicesReady.value = VoicePacks.kokoroMultiLang.isReady(voiceDir)
        }
    }

    /** Download sizes in bytes. */
    fun sizeOf(resource: Resource): Long = when (resource) {
        Resource.SCANNED_BOOKS -> ocr.sizeBytes
        Resource.VOICES -> VOICES_BYTES
        Resource.DICTIONARY -> WordNetPack.SIZE
    }

    fun state(resource: Resource): StateFlow<ResourceState> = when (resource) {
        Resource.SCANNED_BOOKS -> ocr.state.map {
            it.asResource()
        }.stateIn(scope, SharingStarted.Eagerly, ocr.state.value.asResource())

        // Also follows voicesReady, which the Full edition's first start sets without any download.
        Resource.VOICES -> kotlinx.coroutines.flow.combine(voices.state, voicesReady) { s, _ ->
            voiceState(s)
        }.stateIn(scope, SharingStarted.Eagerly, voiceState(voices.state.value))

        Resource.DICTIONARY -> wordNet.state
    }

    /**
     * Downloads [chosen] in the background, side by side (2026-11-12): one after another, the 8 MB dictionary waited
     * behind 180 MB of the rest.
     */
    fun download(chosen: Set<Resource>) {
        for (r in Resource.entries.filter { it in chosen }) {
            scope.launch {
                when (r) {
                    Resource.SCANNED_BOOKS -> ocr.download()

                    Resource.VOICES -> voices.ensureDownloaded().also {
                        voicesReady.value = VoicePacks.kokoroMultiLang.isReady(voiceDir)
                    }

                    Resource.DICTIONARY -> wordNet.download()
                }
            }
        }
    }

    private fun voiceState(s: VoicePackState): ResourceState = when (s) {
        is VoicePackState.Downloading -> ResourceState.Downloading(s.progress)
        is VoicePackState.Installing -> ResourceState.Downloading(1f)
        VoicePackState.Ready -> ResourceState.Ready
        is VoicePackState.Failed -> ResourceState.Failed
        VoicePackState.Idle -> if (voicesReady.value) ResourceState.Ready else ResourceState.Missing
    }

    private fun OcrPackState.asResource(): ResourceState = when (this) {
        OcrPackState.Missing -> ResourceState.Missing
        is OcrPackState.Downloading -> ResourceState.Downloading(fraction)
        OcrPackState.Ready -> ResourceState.Ready
        OcrPackState.Failed -> ResourceState.Failed
    }

    private companion object {
        /** Kokoro multi-language int8, as served by sherpa-onnx's `tts-models` release. */
        const val VOICES_BYTES = 117_142_263L
    }
}
