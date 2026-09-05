package com.bittv.iptv.util

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Sumber data buat fitur "Musik": search judul lagu, terus ambil link mp3
 * langsung siap play. Dua endpoint beda kegunaan:
 *  - search()          -> daftar hasil pencarian (buat ditampilin di list).
 *  - resolvePlayable()  -> link mp3 + metadata buat item yang mau diputer,
 *                          dicari ulang pakai judul persis item yang dipilih
 *                          biar hasilnya presisi sama yang di-tap user.
 *
 * Ini SENGAJA gak nyebut "YouTube" di UI — dari sisi user cuma keliatan
 * "cari lagu" terus "putar", walaupun sumber datanya dari situ.
 */
object MusicRepository {

    /** Satu baris hasil pencarian lagu. */
    data class MusicTrack(
        val title: String,
        val channel: String,
        val durationLabel: String,
        val thumbnailUrl: String,
        val sourceUrl: String
    )

    /** Lagu yang udah siap diputer (ada link mp3 langsung). */
    data class PlayableTrack(
        val title: String,
        val author: String,
        val mp3Url: String,
        val thumbnailUrl: String,
        val durationSeconds: Int
    )

    private const val SEARCH_URL = "https://api-faa.my.id/faa/ytplay?query="
    private const val RESOLVE_URL = "https://api-faa.my.id/faa/youtube?q="
    private const val TIMEOUT_MS = 15_000

    /** Panggil dari background thread. */
    fun search(query: String): Result<List<MusicTrack>> = runCatching {
        val raw = httpGet(SEARCH_URL + encode(query))
        val root = JSONObject(raw)
        if (!root.optBoolean("status", false)) {
            throw IllegalStateException("Pencarian gagal")
        }
        val array = root.optJSONArray("result") ?: JSONArray()
        val items = ArrayList<MusicTrack>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val title = obj.optString("title").trim()
            val link = obj.optString("link").trim()
            if (title.isBlank() || link.isBlank()) continue
            items += MusicTrack(
                title = title,
                channel = obj.optString("channel").trim(),
                durationLabel = obj.optString("duration").trim(),
                thumbnailUrl = obj.optString("imageUrl").trim(),
                sourceUrl = link
            )
        }
        if (items.isEmpty()) throw IllegalStateException("Lagu tidak ditemukan")
        items
    }

    /** Panggil dari background thread. [titleOrQuery] idealnya judul persis dari hasil search. */
    fun resolvePlayable(titleOrQuery: String): Result<PlayableTrack> = runCatching {
        val raw = httpGet(RESOLVE_URL + encode(titleOrQuery))
        val root = JSONObject(raw)
        if (!root.optBoolean("status", false)) {
            throw IllegalStateException("Lagu tidak bisa diputar")
        }
        val result = root.optJSONObject("result")
            ?: throw IllegalStateException("Respons kosong")
        val mp3 = result.optString("mp3").trim()
        if (mp3.isBlank()) throw IllegalStateException("Link audio tidak tersedia")

        PlayableTrack(
            title = result.optString("title").trim().ifBlank { titleOrQuery },
            author = result.optString("author").trim(),
            mp3Url = mp3,
            thumbnailUrl = result.optString("thumbnail").trim(),
            durationSeconds = result.optInt("duration", 0)
        )
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android BITTV)")
        return try {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")
}
