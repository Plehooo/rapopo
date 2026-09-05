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
        val raw = httpGetOrThrowDetailed(SEARCH_URL + encode(query))
        val root = parseJsonObjectOrThrow(raw)
        if (!root.optBoolean("status", false)) {
            val serverMsg = root.optString("message").ifBlank { root.optString("creator") }
            throw IllegalStateException("API_STATUS_FALSE: ${serverMsg.ifBlank { "tidak ada pesan" }}")
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
        if (items.isEmpty()) {
            throw IllegalStateException("EMPTY_RESULT: server balikin ${array.length()} item mentah tapi 0 yang valid")
        }
        items
    }

    /** Panggil dari background thread. [titleOrQuery] idealnya judul persis dari hasil search. */
    fun resolvePlayable(titleOrQuery: String): Result<PlayableTrack> = runCatching {
        val raw = httpGetOrThrowDetailed(RESOLVE_URL + encode(titleOrQuery))
        val root = parseJsonObjectOrThrow(raw)
        if (!root.optBoolean("status", false)) {
            val serverMsg = root.optString("message").ifBlank { root.optString("creator") }
            throw IllegalStateException("API_STATUS_FALSE: ${serverMsg.ifBlank { "tidak ada pesan" }}")
        }
        val result = root.optJSONObject("result")
            ?: throw IllegalStateException("EMPTY_RESULT: field result kosong")
        val mp3 = result.optString("mp3").trim()
        if (mp3.isBlank()) throw IllegalStateException("EMPTY_RESULT: field mp3 kosong")

        PlayableTrack(
            title = result.optString("title").trim().ifBlank { titleOrQuery },
            author = result.optString("author").trim(),
            mp3Url = mp3,
            thumbnailUrl = result.optString("thumbnail").trim(),
            durationSeconds = result.optInt("duration", 0)
        )
    }

    /**
     * Parse body jadi JSONObject. Kalau gagal, kemungkinan besar server BUKAN
     * balikin JSON (misal halaman blokir/challenge dari proteksi anti-bot,
     * atau rate-limit) — dilempar sebagai error yang beda biar kelihatan di
     * pesan UI kalau ini masalah server, bukan "lagu gak ada".
     */
    private fun parseJsonObjectOrThrow(raw: String): JSONObject {
        return runCatching { JSONObject(raw) }.getOrElse {
            val preview = raw.trim().take(80).replace("\n", " ")
            throw IllegalStateException("SERVER_BLOCKED: responsnya bukan JSON -> \"$preview\"")
        }
    }

    private fun httpGetOrThrowDetailed(url: String): String {
        return try {
            httpGet(url)
        } catch (e: Exception) {
            throw IllegalStateException("NETWORK_ERROR: ${e.javaClass.simpleName} ${e.message ?: ""}")
        }
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        // User-Agent browser asli (Chrome Android) — API musik ini kelihatannya
        // punya proteksi anti-bot yang nolak User-Agent generik/palsu.
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13; SM-A125F) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        )
        connection.setRequestProperty("Accept", "application/json, text/plain, */*")
        connection.setRequestProperty("Referer", "https://api-faa.my.id/")

        val code = connection.responseCode
        if (code !in 200..299) {
            val errBody = runCatching {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            }.getOrNull().orEmpty().take(80)
            connection.disconnect()
            throw IllegalStateException("HTTP_ERROR: kode $code -> \"$errBody\"")
        }

        return try {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")
}
