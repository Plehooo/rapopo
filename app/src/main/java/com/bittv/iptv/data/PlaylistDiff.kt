package com.bittv.iptv.data

import java.security.MessageDigest

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
        val oldMap = indexed(oldItems)
        val newMap = indexed(newItems)

        var added = 0
        val removed: Int
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

    /**
     * BUG FIX: dulu perbandingan "channel lama vs baru" cuma mengandalkan
     * `memorySnapshot` di PlaylistRepository, yang cuma field in-memory biasa.
     * PlaylistUpdateWorker/EpgUpdateWorker/dll bikin instance PlaylistRepository
     * BARU tiap kali worker jalan, jadi memorySnapshot-nya selalu null -> diff
     * selalu jatuh ke cabang generik (unchanged=total, added=removed=changed=0).
     * Bahkan di MainActivity yang instance-nya bertahan selama app hidup pun,
     * begitu app di-kill lalu dibuka lagi, "history"-nya reset ke kosong lagi.
     *
     * Fix-nya: bukan simpan channel lengkap/M3U mentah (itu memang sengaja
     * tidak di-cache, lihat komentar di PlaylistRepository), tapi simpan index
     * ringan (stableKey -> hash konten channel) ke SharedPreferences. Index ini
     * kecil dan bertahan lintas proses (app dibuka ulang, atau worker beda
     * instance), jadi diff-nya selalu akurat berdasarkan data run sebelumnya
     * yang beneran tersimpan, bukan cuma harapan objek yang sama masih hidup.
     */
    fun buildIndex(items: List<Channel>): Map<String, String> =
        indexed(items).mapValues { (_, channel) -> contentHash(channel) }

    fun compareIndex(
        oldIndex: Map<String, String>,
        newItems: List<Channel>
    ): Pair<PlaylistDiff, Map<String, String>> {
        val newIndex = buildIndex(newItems)

        var added = 0
        var changed = 0
        var unchanged = 0

        for ((key, hash) in newIndex) {
            val oldHash = oldIndex[key]
            when {
                oldHash == null -> added++
                oldHash == hash -> unchanged++
                else -> changed++
            }
        }
        val removed = (oldIndex.keys - newIndex.keys).size

        return PlaylistDiff(
            added = added,
            removed = removed,
            changed = changed,
            unchanged = unchanged
        ) to newIndex
    }

    private fun indexed(items: List<Channel>): Map<String, Channel> {
        val occurrences = HashMap<String, Int>()
        val result = LinkedHashMap<String, Channel>(items.size)
        for (channel in items) {
            val base = stableKey(channel)
            val ordinal = occurrences[base] ?: 0
            occurrences[base] = ordinal + 1
            result["$base#$ordinal"] = channel
        }
        return result
    }

    private fun stableKey(channel: Channel): String {
        val name = channel.name.trim().lowercase()
        val group = channel.group.trim().lowercase()
        val logo = channel.logoUrl?.trim()?.lowercase().orEmpty()
        val epgId = channel.epgId?.trim()?.lowercase().orEmpty()
        if (epgId.isNotBlank()) return "epg:$epgId|name:$name|group:$group|logo:$logo"

        val id = channel.id.trim().lowercase()
        if (id.isNotBlank() && !id.startsWith("channel-")) {
            return "id:$id|name:$name|group:$group|logo:$logo"
        }

        // Generated parser ids are position-based and therefore unstable when
        // a playlist is reordered. Name+group+logo is the safest remaining
        // identity for channels without an explicit EPG/tvg identifier.
        return "name:$name|group:$group|logo:$logo"
    }

    private fun equivalent(a: Channel, b: Channel): Boolean =
        a.name == b.name &&
            a.logoUrl == b.logoUrl &&
            a.group == b.group &&
            a.streamUrl == b.streamUrl &&
            a.headers == b.headers &&
            a.epgId == b.epgId &&
            a.country == b.country

    /** Hash pendek dari field yang dianggap relevan (sama seperti [equivalent]),
     *  cukup buat mendeteksi "berubah/tidak", tidak perlu reversible. */
    private fun contentHash(channel: Channel): String {
        val raw = buildString {
            append(channel.name); append('\u0000')
            append(channel.logoUrl.orEmpty()); append('\u0000')
            append(channel.group); append('\u0000')
            append(channel.streamUrl); append('\u0000')
            append(channel.headers.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" })
            append('\u0000')
            append(channel.epgId.orEmpty()); append('\u0000')
            append(channel.country.orEmpty())
        }
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
