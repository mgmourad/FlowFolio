package com.flowfolio.core.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** What an address turns out to be, which decides whether it can be played or has to be read first. */
internal enum class StreamKind {
    /** Something a player can open: a file, an HLS or DASH manifest, an Icecast mount. */
    MEDIA,

    /** A list of addresses: `.m3u`, `.pls`. */
    PLAYLIST,

    /** Text that may name a sound somewhere inside it: a web page, a podcast feed, a player's JSON. */
    PAGE,

    /** An image, a PDF, anything else: not a sound and not worth reading. */
    OTHER
}

/**
 * What the resolver needs of the network. Split out so the reading of pages can be tested without one.
 *
 * Both calls block, and are called from [StreamResolver] on a worker thread.
 */
interface MediaProbe {
    /** What [address] serves, lowercased and without parameters ("audio/mpeg"); null when it could not be asked. */
    @Throws(IOException::class)
    fun contentType(address: String): String?

    /** [address]'s body as text, capped; null for anything but a 200. */
    @Throws(IOException::class)
    fun text(address: String): String?
}

/** A `HEAD` for the type and a `GET` for the body, with timeouts and no cookies; redirects are followed. */
class UrlConnectionProbe @Inject constructor() : MediaProbe {
    override fun contentType(address: String): String? = open(address, head = true) { connection ->
        connection.contentType?.substringBefore(';')?.trim()?.lowercase()
    }

    override fun text(address: String): String? = open(address, head = false) { connection ->
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            connection.inputStream.bufferedReader().use { it.readText().take(MAX_BYTES) }
        } else {
            null
        }
    }

    private fun <T> open(address: String, head: Boolean, read: (HttpURLConnection) -> T?): T? {
        val connection = URL(address).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = if (head) "HEAD" else "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "*/*")
            read(connection)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 12_000
        const val MAX_BYTES = 1_500_000

        /** A browser's name, because a page that only serves its player to browsers has nothing to give us. */
        const val USER_AGENT = "Mozilla/5.0 (Android 15; Mobile) FlowFolio"
    }
}

/**
 * Turns an address a reader typed into one a player can open (owner, M5 round).
 *
 * A direct link to a sound is handed straight on. Anything else is looked at: a playlist gives up its first entry, and
 * a page is read for the sound it carries -- an `og:audio` tag, a podcast enclosure, an `<audio>` element, or a media
 * address inside the player's own data, which is how a site that streams gives its audio away. Each find may itself be
 * a playlist or another page, so the trail is followed a few hops.
 *
 * This part is deliberately generic rather than written against any one site: nothing here knows a hostname. That
 * makes it work for a great many sites at once -- radio stations, archives, podcast feeds, anything with an HTML5
 * player -- and it keeps working when any one of them changes its pages. A site that hands its player a *scrambled*
 * address instead, to be unscrambled by its own code, has nothing here to find; that is the extractor's job.
 *
 * When none of that finds anything, an [AudioExtractor] is asked (ADR-0047): that is where a video page's audio
 * comes from, since such a page publishes no address of its own for us to read.
 *
 * The last answer is remembered, so dragging the volume slider does not fetch the page again on every step.
 */
@Singleton
class StreamResolver @Inject constructor(
    private val probe: MediaProbe,
    /** Asked only when nothing open was found; [NoAudioExtractor] in builds without one. */
    private val extractor: AudioExtractor = NoAudioExtractor
) {
    private var askedFor: String? = null
    private var answer: String? = null

    /**
     * The address to hand the player for [address], or null when none could be found.
     *
     * Blocks on the network; call it from a worker thread.
     */
    @Suppress("ReturnCount") // a file of the reader's own and a remembered answer are guards, not branches
    fun playable(address: String): String? {
        val typed = address.trim()
        if (typed.isEmpty()) return null
        // A file the reader picked is already openable; only the web needs resolving.
        if (!typed.startsWith("http://", ignoreCase = true) && !typed.startsWith("https://", ignoreCase = true)) {
            return typed
        }
        synchronized(this) { if (askedFor == typed) return answer }
        // What the site publishes openly first, since it is cheaper and covers radio, podcasts and live HLS; only
        // then the extractor, for a video page that keeps its audio behind its own player (ADR-0047).
        val found = follow(typed, hop = 0) ?: extractor.audioUrl(typed)?.takeIf { it != typed }
        synchronized(this) {
            askedFor = typed
            answer = found
        }
        return found
    }

    /** Forgets the remembered answer, so a retried address is looked up afresh. */
    @Synchronized
    fun forget() {
        askedFor = null
        answer = null
    }

    private fun follow(address: String, hop: Int): String? {
        if (hop > HOPS) return null
        val type = runCatching { probe.contentType(address) }.getOrNull()
        return when (StreamProbe.kind(address, type)) {
            StreamKind.MEDIA -> address
            StreamKind.PLAYLIST -> inside(address, hop, StreamProbe::fromPlaylist)
            StreamKind.PAGE -> inside(address, hop, StreamProbe::fromPage)
            StreamKind.OTHER -> null
        }
    }

    /** Reads [address] and follows whatever [pick] finds in it; null when there is nothing to follow. */
    private fun inside(address: String, hop: Int, pick: (String, String) -> String?): String? {
        val body = runCatching { probe.text(address) }.getOrNull() ?: return null
        val next = pick(body, address)
        return if (next == null || next == address) null else follow(next, hop + 1)
    }

    private companion object {
        /** How many times a find may itself be a list or a page before we give up. */
        const val HOPS = 3
    }
}

/**
 * The reading of addresses and of the text they serve, with no network in it so it can be tested directly.
 *
 * The patterns are the ones sites agree on without meaning to: the tags that let a page be shared or syndicated, and
 * the shape of a media address inside whatever data the page hands its player.
 */
internal object StreamProbe {
    private val IGNORE = RegexOption.IGNORE_CASE

    /** What [address] is, given what the server said it serves; the address's own ending is trusted first. */
    fun kind(address: String, contentType: String?): StreamKind =
        byEnding(address) ?: contentType?.let(::byType) ?: StreamKind.PAGE

    /** The first sound named in [body], made absolute against [base]. */
    fun fromPage(body: String, base: String): String? {
        val tagged = TAGS.firstNotNullOfOrNull { it.find(body)?.groupValues?.getOrNull(1) }
        return tagged?.let { absolute(unescape(it), base) } ?: inText(body, base)
    }

    /** The first entry of an `.m3u` or `.pls` playlist, made absolute against [base]. */
    fun fromPlaylist(body: String, base: String): String? {
        val entry = body.lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                when {
                    // .pls writes its entries as File1=..., File2=...
                    line.startsWith("File", ignoreCase = true) && '=' in line -> line.substringAfter('=').trim()

                    // Its own headers: "[playlist]", "NumberOfEntries=2", "Title1=...".
                    line.startsWith("[") || '=' in line -> null

                    else -> line
                }
            }
            .firstOrNull { it.isNotEmpty() }
        return entry?.let { absolute(it, base) }
    }

    /** The ending of the address's path, where it says plainly what it is. */
    private fun byEnding(address: String): StreamKind? {
        val bare = address.substringBefore('#').substringBefore('?')
        val path = runCatching { URI(bare).path }.getOrNull().orEmpty().ifEmpty { bare }
        val ending = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()
        return when (ending) {
            in MEDIA_ENDINGS -> StreamKind.MEDIA
            in PLAYLIST_ENDINGS -> StreamKind.PLAYLIST
            else -> null
        }
    }

    private fun byType(contentType: String): StreamKind = when {
        // Asked for before the audio types: a .m3u is served as audio/x-mpegurl but holds addresses, not sound.
        contentType in PLAYLIST_TYPES -> StreamKind.PLAYLIST

        contentType in MEDIA_TYPES || contentType.startsWith("audio/") -> StreamKind.MEDIA

        contentType.startsWith("text/") || contentType.endsWith("xml") || contentType.endsWith("json") ->
            StreamKind.PAGE

        else -> StreamKind.OTHER
    }

    /**
     * Any media address inside the text, which is where a player's own data keeps it: `"url":"https://.../x.m3u8"`,
     * a JSON-escaped `https:\/\/...`, an entity-escaped query. The first one whose ending says it is a sound wins.
     */
    private fun inText(body: String, base: String): String? = IN_TEXT.findAll(body)
        .map { unescape(it.value) }
        .firstOrNull { byEnding(it) == StreamKind.MEDIA }
        ?.let { absolute(it, base) }

    /** JSON and HTML escaping of an address, undone. */
    private fun unescape(raw: String): String = raw
        .replace("\\/", "/")
        .replace("\\u0026", "&")
        .replace("\\u003d", "=")
        .replace("&amp;", "&")
        .trim()

    private fun absolute(found: String, base: String): String? = runCatching {
        URI(base).resolve(URI(found.replace(" ", "%20"))).toString()
    }.getOrNull()?.takeIf { it.startsWith("http", ignoreCase = true) }

    /** The tags a page uses to say where its sound is, in the order they can be trusted. */
    private val TAGS = listOf(
        // Sharing and syndication tags: put there to be read, so they are the best answer when present.
        Regex("""<meta[^>]+(?:property|name)=["']og:audio(?::secure_url)?["'][^>]+content=["']([^"']+)["']""", IGNORE),
        Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']og:audio(?::secure_url)?["']""", IGNORE),
        // A podcast feed's episode.
        Regex("""<enclosure[^>]+url=["']([^"']+)["'][^>]*type=["']audio/[^"']*["']""", IGNORE),
        Regex("""<enclosure[^>]+type=["']audio/[^"']*["'][^>]*url=["']([^"']+)["']""", IGNORE),
        // An HTML5 player.
        Regex("""<audio[^>]+src=["']([^"']+)["']""", IGNORE),
        Regex("""<source[^>]+src=["']([^"']+)["']""", IGNORE)
    )

    /** An http address, as it appears in markup or in escaped JSON, up to whatever quotes or closes it. */
    private val IN_TEXT = Regex("""https?:(?:\\?/\\?/|//)(?:[^"'\\\s<>()\[\]]|\\/|\\u0026)+""")

    private val MEDIA_ENDINGS = setOf(
        "mp3", "m4a", "m4b", "aac", "aacp", "ogg", "oga", "opus", "flac", "wav", "weba", "mp4", "webm", "m3u8", "mpd"
    )
    private val PLAYLIST_ENDINGS = setOf("m3u", "pls", "asx", "xspf")
    private val MEDIA_TYPES = setOf(
        "application/ogg",
        "application/vnd.apple.mpegurl",
        "application/dash+xml",
        "video/mp4",
        "video/webm",
        "video/mp2t"
    )
    private val PLAYLIST_TYPES = setOf(
        "audio/x-mpegurl",
        "audio/mpegurl",
        "application/x-mpegurl",
        "audio/x-scpls",
        "application/pls+xml",
        "application/xspf+xml"
    )
}
