package com.bittv.iptv.util

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Data source fitur Musik: satu request search sudah membawa metadata + mp3. */
object MusicRepository {

    data class MusicTrack(
        val title: String,
        val channel: String,
        val durationLabel: String,
        val thumbnailUrl: String,
        val sourceUrl: String,
        val author: String = "",
        val mp3Url: String = "",
        val views: String = "",
        val published: String = "",
        val durationSeconds: Int = 0
    )

    private const val SEARCH_URL = "https://api-faa.my.id/faa/ytplay?query="
    private const val TIMEOUT_MS = 15_000

    fun search(query: String): Result<List<MusicTrack>> = runCatching {
        val raw = httpGetOrThrowDetailed(SEARCH_URL + encode(query))
        val root = parseJsonObjectOrThrow(raw)
        if (!root.optBoolean("status", false)) {
            val serverMsg = root.optString("message").ifBlank { root.optString("creator") }
            throw IllegalStateException("API_STATUS_FALSE: ${serverMsg.ifBlank { "tidak ada pesan" }}")
        }

        val items = ArrayList<MusicTrack>()
        val result = root.opt("result")

        // Format yang terlihat pada screenshot browser: result adalah JSONObject.
        if (result is JSONObject) {
            parseTrack(result)?.let(items::add)
        } else if (result is JSONArray) {
            // Tetap dukung format array bila API suatu saat mengirim banyak hasil.
            for (i in 0 until result.length()) {
                parseTrack(result.optJSONObject(i))?.let(items::add)
            }
        }

        if (items.isEmpty()) {
            throw IllegalStateException("EMPTY_RESULT: API tidak mengirim hasil musik yang valid")
        }
        items
    }

    private fun parseTrack(obj: JSONObject?): MusicTrack? {
        if (obj == null) return null

        val title = obj.optString("title").trim()
        val mp3 = obj.optString("mp3").trim()
        if (title.isBlank()) return null

        val durationSeconds = obj.optInt("duration", 0)
        val durationLabel = obj.optString("duration_timestamp").trim().ifBlank {
            if (durationSeconds > 0) formatDuration(durationSeconds) else obj.optString("duration").trim()
        }

        val thumbnail = obj.optString("thumbnail").trim()
            .ifBlank { obj.optString("imageUrl").trim() }
        val sourceUrl = obj.optString("url").trim()
            .ifBlank { obj.optString("link").trim() }
        val author = obj.optString("author").trim()
            .ifBlank { obj.optString("channel").trim() }

        return MusicTrack(
            title = title,
            channel = author,
            durationLabel = durationLabel,
            thumbnailUrl = thumbnail,
            sourceUrl = sourceUrl,
            author = author,
            mp3Url = mp3,
            views = obj.optString("views").trim(),
            published = obj.optString("published").trim(),
            durationSeconds = durationSeconds
        )
    }

    private fun parseJsonObjectOrThrow(raw: String): JSONObject = runCatching {
        JSONObject(raw)
    }.getOrElse {
        val preview = raw.trim().take(100).replace("\n", " ")
        throw IllegalStateException("SERVER_BLOCKED: responsnya bukan JSON -> \"$preview\"")
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
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
        )
        connection.setRequestProperty("Accept", "application/json, text/plain, */*")
        connection.setRequestProperty("Referer", "https://api-faa.my.id/")

        val code = connection.responseCode
        if (code !in 200..299) {
            val errBody = runCatching {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            }.getOrNull().orEmpty().take(100)
            connection.disconnect()
            throw IllegalStateException("HTTP_ERROR: kode $code -> \"$errBody\"")
        }

        return try {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun formatDuration(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")
}
