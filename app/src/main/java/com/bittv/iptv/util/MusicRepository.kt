package com.bittv.iptv.util

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Data source fitur Musik.
 * Endpoint ytplay sudah mengembalikan metadata + mp3, jadi hasil search
 * langsung siap diputar tanpa request resolve kedua.
 */
object MusicRepository {

    data class MusicTrack(
        val title: String,
        val author: String,
        val durationLabel: String,
        val durationSeconds: Int,
        val viewsLabel: String,
        val publishedLabel: String,
        val thumbnailUrl: String,
        val mp3Url: String,
        val sourceUrl: String
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

        val result = root.opt("result")
        val objects = when (result) {
            is JSONObject -> listOf(result)
            is JSONArray -> buildList {
                for (i in 0 until result.length()) {
                    result.optJSONObject(i)?.let(::add)
                }
            }
            else -> emptyList()
        }

        val items = ArrayList<MusicTrack>(objects.size)
        for (obj in objects) {
            val title = obj.optString("title").trim()
            val mp3 = obj.optString("mp3").trim()
            if (title.isBlank() || mp3.isBlank()) continue

            val durationSeconds = obj.optInt("duration", 0)
            val durationLabel = obj.optString("duration_timestamp").trim().ifBlank {
                obj.optString("duration").trim().let { rawDuration ->
                    if (rawDuration.isBlank() || rawDuration == "0") "" else rawDuration
                }
            }

            items += MusicTrack(
                title = title,
                author = obj.optString("author").trim(),
                durationLabel = durationLabel,
                durationSeconds = durationSeconds,
                viewsLabel = formatViews(obj.optLong("views", 0L)),
                publishedLabel = obj.optString("published").trim(),
                thumbnailUrl = obj.optString("thumbnail").trim().ifBlank {
                    obj.optString("imageUrl").trim()
                },
                mp3Url = mp3,
                sourceUrl = obj.optString("url").trim().ifBlank {
                    obj.optString("link").trim()
                }
            )
        }

        if (items.isEmpty()) {
            throw IllegalStateException("EMPTY_RESULT: result tidak berisi lagu yang punya mp3")
        }
        items
    }

    private fun parseJsonObjectOrThrow(raw: String): JSONObject {
        return runCatching { JSONObject(raw) }.getOrElse {
            val preview = raw.trim().take(80).replace("\n", " ")
            throw IllegalStateException("SERVER_BLOCKED: responsnya bukan JSON -> \"$preview\"")
        }
    }

    private fun httpGetOrThrowDetailed(url: String): String = try {
        httpGet(url)
    } catch (e: Exception) {
        throw IllegalStateException("NETWORK_ERROR: ${e.javaClass.simpleName} ${e.message ?: ""}")
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
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
            }.getOrNull().orEmpty().take(120)
            connection.disconnect()
            throw IllegalStateException("HTTP_ERROR: kode $code -> \"$errBody\"")
        }

        return try {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun formatViews(views: Long): String {
        if (views <= 0L) return ""
        return when {
            views >= 1_000_000_000L -> String.format(java.util.Locale.US, "%.1fB views", views / 1_000_000_000.0)
            views >= 1_000_000L -> String.format(java.util.Locale.US, "%.1fM views", views / 1_000_000.0)
            views >= 1_000L -> String.format(java.util.Locale.US, "%.1fK views", views / 1_000.0)
            else -> "$views views"
        }.replace(".0B", "B").replace(".0M", "M").replace(".0K", "K")
    }

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")
}
