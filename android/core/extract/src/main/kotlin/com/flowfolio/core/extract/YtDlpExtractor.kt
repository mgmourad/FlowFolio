package com.flowfolio.core.extract

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.flowfolio.core.data.AudioExtractor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The audio of a video page, through yt-dlp running in the app (ADR-0047).
 *
 * yt-dlp is the work of a great many people who keep up with how sites change; reimplementing its extractors in
 * Kotlin would be both enormous and permanently out of date, so it is carried as what it is -- Python -- on
 * Chaquopy's embedded interpreter. Only the *address* is asked for: the sound is then played by Android's own
 * MediaPlayer, so nothing is downloaded, no ffmpeg is needed, and the app stays clear of GPL code (ADR-0003).
 *
 * Everything here is forgiving. The interpreter is missing on an ABI it was not built for, a site may have changed
 * under yt-dlp, and a page may simply have no audio; each of those answers null, and [StreamResolver] then carries
 * on with what it found itself. Starting Python costs a second or so the first time, which is why it is left until
 * something is actually asked of it rather than done when the app starts.
 */
@Singleton
class YtDlpExtractor @Inject constructor(@ApplicationContext private val context: Context) : AudioExtractor {
    override fun audioUrl(address: String): String? = runCatching {
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        extractedUrl(Python.getInstance().getModule(MODULE).callAttr("audio_url", address)?.toString())
    }.getOrNull()

    private companion object {
        const val MODULE = "flowfolio_extract"
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ExtractBindings {
    @Binds
    abstract fun extractor(impl: YtDlpExtractor): AudioExtractor
}

/**
 * What the Python side actually said, or null.
 *
 * Python's `None` reaches Java either as null or as an object that prints "None"; handed on unchecked, that string
 * would reach the player as though it were an address, so it is caught here rather than anywhere further on.
 */
internal fun extractedUrl(raw: String?): String? =
    raw?.trim()?.takeIf { it.isNotEmpty() && it != "None" && it.startsWith("http", ignoreCase = true) }
