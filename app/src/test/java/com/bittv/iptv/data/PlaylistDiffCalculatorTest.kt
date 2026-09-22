package com.bittv.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistDiffCalculatorTest {
    @Test
    fun streamUrlReplacementIsChangedNotAddedAndRemoved() {
        val old = listOf(
            channel(id = "GTV", name = "GTV", url = "https://old.example/gtv.m3u8")
        )
        val new = listOf(
            channel(id = "GTV", name = "GTV", url = "https://new.example/gtv.m3u8")
        )

        val diff = PlaylistDiffCalculator.compare(old, new)

        assertEquals(0, diff.added)
        assertEquals(0, diff.removed)
        assertEquals(1, diff.changed)
        assertEquals(0, diff.unchanged)
    }


    @Test
    fun duplicateEpgIdsRemainDistinctWhenNamesDiffer() {
        val old = listOf(
            channel(id = "x", name = "X Main", url = "https://one.example/live"),
            channel(id = "x", name = "X Backup", url = "https://two.example/live")
        )
        val new = listOf(
            channel(id = "x", name = "X Main", url = "https://one.example/live"),
            channel(id = "x", name = "X Backup", url = "https://two-new.example/live")
        )

        val diff = PlaylistDiffCalculator.compare(old, new)

        assertEquals(0, diff.added)
        assertEquals(0, diff.removed)
        assertEquals(1, diff.changed)
        assertEquals(1, diff.unchanged)
    }

    @Test
    fun generatedIdsSurvivePlaylistReorderingByNameAndGroup() {
        val old = listOf(
            channel(id = "channel-0", name = "RCTI", url = "https://rcti.example/live"),
            channel(id = "channel-1", name = "GTV", url = "https://gtv.example/live")
        )
        val new = listOf(
            channel(id = "channel-0", name = "GTV", url = "https://gtv.example/live"),
            channel(id = "channel-1", name = "RCTI", url = "https://rcti.example/live")
        )

        val (diff, _) = PlaylistDiffCalculator.compareIndex(
            PlaylistDiffCalculator.buildIndex(old),
            new
        )

        assertEquals(0, diff.added)
        assertEquals(0, diff.removed)
        assertEquals(0, diff.changed)
        assertEquals(2, diff.unchanged)
    }

    private fun channel(id: String, name: String, url: String) = Channel(
        id = id,
        name = name,
        logoUrl = null,
        group = "Indonesia",
        streamUrl = url,
        headers = emptyMap(),
        epgId = null,
        country = "ID"
    )
}
