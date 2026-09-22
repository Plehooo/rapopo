package com.bittv.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class M3uParserTest {
    @Test
    fun parsesRelativeStreamAndPreservesDrmAndHeaders() {
        val m3u = """#EXTM3U
#EXTINF:-1 tvg-id="gtv" tvg-name="GTV" group-title="Indonesia",GTV
#EXTVLCOPT:http-user-agent=BITTV-Test
#EXTVLCOPT:http-referrer=https://rctiplus.com/
#KODIPROP:inputstream.adaptive.license_type=clearkey
#KODIPROP:inputstream.adaptive.license_key=0011:2233
live/gtv.mpd
"""

        val channels = M3uParser.parse(m3u, "https://example.com/playlists/main.m3u")
        assertEquals(1, channels.size)
        val channel = channels.single()
        assertEquals("gtv", channel.id)
        assertEquals("https://example.com/playlists/live/gtv.mpd", channel.streamUrl)
        assertEquals("BITTV-Test", channel.headers["User-Agent"])
        assertEquals("https://rctiplus.com/", channel.headers["Referer"])
        assertEquals("clearkey", channel.drmScheme)
        assertEquals("0011:2233", channel.drmLicenseKey)
        assertEquals("gtv", channel.epgId)
    }

    @Test
    fun keepsTwoEntriesWithSameTvgIdWhenStreamsDiffer() {
        val m3u = """#EXTM3U
#EXTINF:-1 tvg-id="x" tvg-name="X",X
https://one.example/live
#EXTINF:-1 tvg-id="x" tvg-name="X Backup",X Backup
https://two.example/live
"""

        val channels = M3uParser.parse(m3u)
        assertEquals(2, channels.size)
    }
}
