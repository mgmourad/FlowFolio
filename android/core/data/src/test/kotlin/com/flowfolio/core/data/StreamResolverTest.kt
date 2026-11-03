package com.flowfolio.core.data

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a page serves and what it says, with no network: the resolver's two inputs. */
private class FakeProbe(
    private val types: Map<String, String?> = emptyMap(),
    private val bodies: Map<String, String> = emptyMap()
) : MediaProbe {
    var asked = 0
        private set

    override fun contentType(address: String): String? =
        if (address in types) types.getValue(address) else throw IOException("not asked for")

    override fun text(address: String): String? {
        asked++
        return bodies[address]
    }
}

class StreamProbeTest {
    @Test
    fun `an ending names a sound`() {
        assertEquals(StreamKind.MEDIA, StreamProbe.kind("https://h/x.mp3", null))
        assertEquals(StreamKind.MEDIA, StreamProbe.kind("https://h/live/index.m3u8?t=9", null))
        assertEquals(StreamKind.MEDIA, StreamProbe.kind("https://h/a.opus", null))
        assertEquals(StreamKind.PLAYLIST, StreamProbe.kind("https://h/radio.pls", null))
    }

    @Test
    fun `a type names a sound when the ending does not`() {
        assertEquals(StreamKind.MEDIA, StreamProbe.kind("https://ice.h/groovesalad", "audio/mpeg"))
        assertEquals(StreamKind.MEDIA, StreamProbe.kind("https://h/live", "application/vnd.apple.mpegurl"))
        assertEquals(StreamKind.OTHER, StreamProbe.kind("https://h/cover", "image/png"))
        assertEquals(StreamKind.PAGE, StreamProbe.kind("https://h/track", "text/html"))
    }

    /** A `.m3u` is served as an audio type but holds addresses, so it must be read rather than played. */
    @Test
    fun `a list is told from a sound`() {
        assertEquals(StreamKind.PLAYLIST, StreamProbe.kind("https://h/listen", "audio/x-mpegurl"))
        assertEquals(StreamKind.PLAYLIST, StreamProbe.kind("https://h/listen", "audio/x-scpls"))
    }

    /** Nothing said at all is still worth a look: many servers refuse a HEAD. */
    @Test
    fun `silence from the server means read the page`() {
        assertEquals(StreamKind.PAGE, StreamProbe.kind("https://h/some/thing", null))
    }

    @Test
    fun `a sharing tag is read`() {
        val page = """<html><head><meta property="og:audio" content="https://h/a/b.mp3"></head></html>"""
        assertEquals("https://h/a/b.mp3", StreamProbe.fromPage(page, "https://h/track/1"))
    }

    @Test
    fun `a sharing tag is read with its attributes the other way round`() {
        val page = """<meta content="https://h/b.m4a" property="og:audio" />"""
        assertEquals("https://h/b.m4a", StreamProbe.fromPage(page, "https://h/t"))
    }

    @Test
    fun `a podcast enclosure is read`() {
        val feed = """<item><enclosure url="https://h/ep/4.mp3" length="9" type="audio/mpeg"/></item>"""
        assertEquals("https://h/ep/4.mp3", StreamProbe.fromPage(feed, "https://h/feed.xml"))
    }

    @Test
    fun `an audio element is read, relative to the page`() {
        val page = """<audio controls src="/media/take.ogg"></audio>"""
        assertEquals("https://h/media/take.ogg", StreamProbe.fromPage(page, "https://h/songs/take"))
    }

    /** How a player's own data carries the address: JSON-escaped slashes and an escaped query. */
    @Test
    fun `a sound inside the player's data is found`() {
        // Built from escapes rather than written out, so the backslashes under test survive the file.
        val escaped = "https:\\/\\/h\\/live\\/720.m3u8?a=1\\u0026sig=abc"
        val page = """{"title":"x","streams":[{"hls":"$escaped"}]}"""
        assertEquals("https://h/live/720.m3u8?a=1&sig=abc", StreamProbe.fromPage(page, "https://h/watch"))
    }

    @Test
    fun `an entity-escaped query is put back together`() {
        val page = """<audio src="https://h/a.mp3?one=1&amp;two=2">"""
        assertEquals("https://h/a.mp3?one=1&two=2", StreamProbe.fromPage(page, "https://h/t"))
    }

    @Test
    fun `a page with no sound in it gives nothing`() {
        assertNull(StreamProbe.fromPage("<html><body><p>No sound here.</p></body></html>", "https://h/t"))
    }

    /** Images and scripts are addresses too; only an ending that says sound is taken. */
    @Test
    fun `other addresses in the page are passed over`() {
        val page = """<img src="https://h/art.jpg"><script src="https://h/p.js"></script>
            |<div data-src="https://h/real.flac"></div>
        """.trimMargin()
        assertEquals("https://h/real.flac", StreamProbe.fromPage(page, "https://h/t"))
    }

    @Test
    fun `an m3u gives its first entry`() {
        val list = "#EXTM3U\n#EXTINF:-1,Station\nhttps://ice.h/mount\nhttps://ice.h/backup\n"
        assertEquals("https://ice.h/mount", StreamProbe.fromPlaylist(list, "https://h/radio.m3u"))
    }

    @Test
    fun `a pls gives its first entry and skips its headers`() {
        val list = "[playlist]\nNumberOfEntries=2\nFile1=https://ice.h/mount\nTitle1=Station\nFile2=https://ice.h/two\n"
        assertEquals("https://ice.h/mount", StreamProbe.fromPlaylist(list, "https://h/radio.pls"))
    }
}

class StreamResolverTest {
    @Test
    fun `a direct sound is handed straight on`() {
        val probe = FakeProbe(types = mapOf("https://h/a.mp3" to "audio/mpeg"))
        assertEquals("https://h/a.mp3", StreamResolver(probe).playable(" https://h/a.mp3 "))
        assertEquals("a direct sound should not be downloaded to check it", 0, probe.asked)
    }

    /** A file the reader picked is already openable, so it never reaches the network. */
    @Test
    fun `a picked file is handed straight on`() {
        val probe = FakeProbe()
        assertEquals("content://media/42", StreamResolver(probe).playable("content://media/42"))
    }

    @Test
    fun `an empty address gives nothing`() {
        assertNull(StreamResolver(FakeProbe()).playable("   "))
    }

    @Test
    fun `a page is read for the sound it carries`() {
        val probe = FakeProbe(
            types = mapOf("https://h/track/1" to "text/html", "https://h/a/b.mp3" to "audio/mpeg"),
            bodies = mapOf("https://h/track/1" to """<meta property="og:audio" content="/a/b.mp3">""")
        )
        assertEquals("https://h/a/b.mp3", StreamResolver(probe).playable("https://h/track/1"))
    }

    /** A station page naming a `.pls`, which names the mount: two hops, both followed. */
    @Test
    fun `a trail of a page and a list is followed`() {
        val probe = FakeProbe(
            types = mapOf(
                "https://h/station" to "text/html",
                "https://h/station/listen.pls" to "audio/x-scpls",
                "https://ice.h/mount" to "audio/mpeg"
            ),
            bodies = mapOf(
                "https://h/station" to """<audio src="/station/listen.pls">""",
                "https://h/station/listen.pls" to "[playlist]\nFile1=https://ice.h/mount\n"
            )
        )
        assertEquals("https://ice.h/mount", StreamResolver(probe).playable("https://h/station"))
    }

    @Test
    fun `a page with nothing in it gives nothing`() {
        val probe = FakeProbe(
            types = mapOf("https://h/p" to "text/html"),
            bodies = mapOf("https://h/p" to "<html><p>nothing</p></html>")
        )
        assertNull(StreamResolver(probe).playable("https://h/p"))
    }

    @Test
    fun `a server that cannot be reached gives nothing rather than throwing`() {
        assertNull(StreamResolver(FakeProbe()).playable("https://nowhere.invalid/x"))
    }

    /** A volume drag re-asks for the same address many times; the page must be fetched once. */
    @Test
    fun `the answer is remembered`() {
        val probe = FakeProbe(
            types = mapOf("https://h/p" to "text/html", "https://h/s.mp3" to "audio/mpeg"),
            bodies = mapOf("https://h/p" to """<audio src="https://h/s.mp3">""")
        )
        val resolver = StreamResolver(probe)
        repeat(4) { assertEquals("https://h/s.mp3", resolver.playable("https://h/p")) }
        assertEquals(1, probe.asked)
        resolver.forget()
        assertEquals("https://h/s.mp3", resolver.playable("https://h/p"))
        assertEquals(2, probe.asked)
    }

    /** A page that names itself would otherwise be followed for ever. */
    @Test
    fun `a page pointing at itself is not followed`() {
        val probe = FakeProbe(
            types = mapOf("https://h/p" to "text/html"),
            bodies = mapOf("https://h/p" to """<audio src="https://h/p">""")
        )
        assertNull(StreamResolver(probe).playable("https://h/p"))
    }
}
