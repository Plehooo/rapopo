package com.bittv.iptv.data

data class PlaylistDiff(
    val added: Int,
    val removed: Int,
    val changed: Int,
    val unchanged: Int
) {
    val total: Int get() = added + removed + unchanged + changed
}

object PlaylistDiffCalculator {
    fun compare(oldItems: List<Channel>, newItems: List<Channel>): PlaylistDiff {
        val oldMap = oldItems.associateBy { stableKey(it) }
        val newMap = newItems.associateBy { stableKey(it) }

        var added = 0
        var removed = 0
        var changed = 0
        var unchanged = 0

        for ((key, newItem) in newMap) {
            val oldItem = oldMap[key]
            when {
                oldItem == null -> added++
                equivalent(oldItem, newItem) -> unchanged++
                else -> changed++
            }
        }
        removed = (oldMap.keys - newMap.keys).size

        return PlaylistDiff(
            added = added,
            removed = removed,
            changed = changed,
            unchanged = unchanged
        )
    }

    private fun stableKey(channel: Channel): String {
        val url = channel.streamUrl.trim().lowercase()
        return if (url.isNotBlank()) {
            "url:$url"
        } else {
            "id:${channel.id.trim().lowercase()}\u0000name:${channel.name.trim().lowercase()}"
        }
    }

    private fun equivalent(a: Channel, b: Channel): Boolean =
        a.name == b.name &&
            a.logoUrl == b.logoUrl &&
            a.group == b.group &&
            a.streamUrl == b.streamUrl &&
            a.headers == b.headers &&
            a.epgId == b.epgId &&
            a.country == b.country
}
