package com.flowfolio.core.data

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A build published on the releases page: what it calls itself, and where its APK is. */
data class Release(
    val versionName: String,
    val versionCode: Long,
    val apkName: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val notes: String
)

/** Where checking for an update has got to. */
sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    /** This build is the newest published one. */
    data object UpToDate : UpdateState

    data class Available(val release: Release) : UpdateState

    data class Downloading(val release: Release, val fraction: Float) : UpdateState

    /** Downloaded and checked; [install] hands it to Android, which asks the reader before installing. */
    data class Ready(val release: Release, val file: File) : UpdateState

    /** Could not finish; [reason] is what the reader is told. */
    data class Failed(val reason: UpdateFailure) : UpdateState
}

/**
 * Why a check or a download could not finish.
 *
 * This was a single `offline` flag until a check against a repository with no public releases reported "could not
 * reach the releases page" -- when the page had answered perfectly promptly, with a 404. One flag cannot carry both
 * "your connection is down" and "there is nothing published there", and a reader can only act on the difference.
 */
enum class UpdateFailure {
    /** The request never got an answer at all. */
    OFFLINE,

    /** The releases page answered 404: nothing published there, or the repository is not public. */
    MISSING,

    /** GitHub is rate-limiting unauthenticated callers. */
    BUSY,

    /** Some other status, or a body that would not parse. */
    UNEXPECTED,

    /** The APK arrived short of the size the releases page gave. */
    DOWNLOAD
}

/** Carries [reason] from the connection up to the screen, which is the only place it is any use. */
class UpdateUnavailable(val reason: UpdateFailure) : IOException(reason.name)

/**
 * Finding and fetching a newer FlowFolio (ADR-0048).
 *
 * FlowFolio is not on an app store, so nothing else will tell a reader that a new build exists. This asks the
 * project's own releases page, downloads the APK it names, and hands it to Android.
 *
 * **Android does the installing, and asks first.** No app can install another silently without being the device's
 * owner, which FlowFolio is not and should not be. "Automatically" therefore means the checking and the downloading
 * happen on their own; the last step is always the system's own dialog, with the reader's finger on it.
 *
 * Only the releases list is ever requested: no identifier, no account, nothing about the library (ADR-0017).
 */
@Singleton
class Updates @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    /**
     * The newest published build, or null when this one is already it.
     *
     * Pre-releases count: FlowFolio ships alphas and canaries on purpose (ADR-0042), and a reader on an alpha wants
     * the next alpha.
     */
    suspend fun latest(): Release? = withContext(io) {
        newest(JSONArray(get(RELEASES)))?.takeIf { it.versionCode > installed().second }
    }

    /** What this build calls itself, for the line in Settings that says which one you are on. */
    fun installedName(): String = installed().first

    /** The running build's number, to tell an update apart from a first install. */
    fun installedCode(): Long = installed().second

    /** The newest published build, whatever its number; its notes back the "what's new" popup after an update. */
    suspend fun latestRelease(): Release? = withContext(io) { newest(JSONArray(get(RELEASES))) }

    /** The running build's name and number, straight from the package manager rather than passed down. */
    private fun installed(): Pair<String, Long> = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        (info.versionName ?: "?") to info.longVersionCode
    }.getOrDefault("?" to 0L)

    /**
     * Downloads [release]'s APK into the cache, reporting progress as it goes, and answers the file.
     *
     * The size the releases page gave is checked against what arrived: a half-downloaded APK is worse than none,
     * because Android's installer would reject it with something unhelpful.
     */
    suspend fun download(release: Release, onProgress: (Float) -> Unit): File = withContext(io) {
        val dir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, release.apkName)
        val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", AGENT)
        }
        try {
            connection.inputStream.use { source ->
                target.outputStream().use { sink ->
                    val buffer = ByteArray(BUFFER)
                    var done = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        done += read
                        if (release.sizeBytes > 0) onProgress((done.toFloat() / release.sizeBytes).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (release.sizeBytes > 0 && target.length() != release.sizeBytes) {
            target.delete()
            throw IOException("download was ${target.length()} of ${release.sizeBytes} bytes")
        }
        target
    }

    /**
     * Hands [file] to Android's installer. What happens next is the system's dialog, not ours.
     *
     * Answers false when the phone has not been told FlowFolio may install packages -- the reader then has that
     * switch to find in system settings, and the APK is sitting in Downloads-like reach through the same dialog.
     */
    fun install(file: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    /**
     * Installs [file] through Android's package installer: silently when [silent] and Android allows FlowFolio to
     * update itself, else with Android's prompt. Falls back to [install] when no session can be made.
     */
    fun installSession(file: File, silent: Boolean): Boolean =
        UpdateInstaller.install(context, file, silent) || install(file)

    /** Opens the releases page, for a reader whose phone will not let the app install anything. */
    fun openReleasesPage(): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, RELEASES_PAGE.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    /** The highest build in [releases] that has an APK to offer. */
    private fun newest(releases: JSONArray): Release? = (0 until releases.length())
        .mapNotNull { release(releases.optJSONObject(it) ?: return@mapNotNull null) }
        .maxByOrNull { it.versionCode }

    private fun release(json: JSONObject): Release? {
        if (json.optBoolean("draft")) return null
        val apk = apkAsset(json.optJSONArray("assets"))
        val name = apk?.optString("name").orEmpty()
        // ADR-0042 names every APK `flowfolio-<version>-<versionCode>.apk`, so the build number is in the name --
        // which beats parsing a version string whose shape changes between channels.
        val code = APK_NAME.find(name)?.groupValues?.get(1)?.toLongOrNull()
        return if (apk == null || code == null) {
            null
        } else {
            Release(
                versionName = json.optString("tag_name").removePrefix("v"),
                versionCode = code,
                apkName = name,
                apkUrl = apk.optString("browser_download_url"),
                sizeBytes = apk.optLong("size"),
                notes = json.optString("body").trim()
            )
        }
    }

    /** The APK this phone should install from a release, or null when it has none (a notes-only release, say). */
    private fun apkAsset(assets: JSONArray?): JSONObject? = assets?.let { list ->
        val all = (0 until list.length()).mapNotNull { list.optJSONObject(it) }
        val chosen = pickApk(all.map { it.optString("name") }, Build.SUPPORTED_ABIS.toList())
        all.firstOrNull { it.optString("name") == chosen }
    }

    /**
     * The body of [url], or an [UpdateUnavailable] saying why there is none.
     *
     * Every non-200 used to collapse into null and then one bare `IOException`, which the screen could report only
     * as "could not reach the releases page" -- wrong, and nothing the reader could act on, when the page had
     * answered 404.
     */
    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", AGENT)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            val code = runCatching { connection.responseCode }
                .getOrElse { throw UpdateUnavailable(UpdateFailure.OFFLINE) }
            if (code != HttpURLConnection.HTTP_OK) throw UpdateUnavailable(failureFor(code))
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    internal companion object {
        const val RELEASES = "https://api.github.com/repos/mgmourad/FlowFolio/releases?per_page=10"
        const val RELEASES_PAGE = "https://github.com/mgmourad/FlowFolio/releases/latest"
        const val APK_TYPE = "application/vnd.android.package-archive"
        const val UPDATE_DIR = "updates"
        const val TIMEOUT_MS = 20_000
        const val BUFFER = 64 * 1024
        const val AGENT = "FlowFolio (Android; https://github.com/mgmourad/FlowFolio)"
        const val TOO_MANY_REQUESTS = 429

        /** `flowfolio-<version>-<versionCode>.apk`, or `...-<versionCode>-<abi>.apk` for one processor type (TM2). */
        val APK_NAME = Regex("""-(\d+)(?:-[a-z0-9_]+(?:-[a-z0-9_]+)?)?\.apk$""")

        /**
         * Which APK of a release to install (TM1): the one built for the first processor type in [abis] (the phone's
         * own, most preferred first), else the universal APK, else any APK. Releases from TM2 carry one APK per
         * processor type beside the universal one; older releases only the universal one.
         */
        fun pickApk(names: List<String>, abis: List<String>): String? {
            val apks = names.filter { it.endsWith(".apk") }
            for (abi in abis) apks.firstOrNull { it.endsWith("-$abi.apk") }?.let { return it }
            return apks.firstOrNull { UNIVERSAL.containsMatchIn(it) } ?: apks.firstOrNull()
        }

        private val UNIVERSAL = Regex("""-\d+\.apk$""")

        /** What an HTTP status means for a reader waiting on a check. */
        fun failureFor(code: Int): UpdateFailure = when (code) {
            HttpURLConnection.HTTP_NOT_FOUND -> UpdateFailure.MISSING
            HttpURLConnection.HTTP_FORBIDDEN, TOO_MANY_REQUESTS -> UpdateFailure.BUSY
            else -> UpdateFailure.UNEXPECTED
        }
    }
}
