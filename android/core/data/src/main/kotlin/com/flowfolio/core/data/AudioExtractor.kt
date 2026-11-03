package com.flowfolio.core.data

/**
 * Finds the audio in a page that only a site's own player knows how to read (ADR-0047).
 *
 * [StreamResolver] handles everything a site publishes openly -- a file, a playlist, an `og:audio` tag, a podcast
 * enclosure, an HLS manifest in a player's data. What it cannot do is work out an address a site deliberately keeps
 * behind its own code; that is what an extractor is for, and it is asked only once the open ones have come up empty.
 *
 * Implementations answer null freely: no audio, an unknown site, or no extractor on this build.
 */
fun interface AudioExtractor {
    /** The address of [address]'s audio, or null. Blocks on the network; call it from a worker thread. */
    fun audioUrl(address: String): String?
}

/** What a build without an extractor uses: it finds nothing, and the open web still plays. */
object NoAudioExtractor : AudioExtractor {
    override fun audioUrl(address: String): String? = null
}
