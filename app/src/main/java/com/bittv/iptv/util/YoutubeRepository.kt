package com.bittv.iptv.util

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * YouTube Data API v3 data source.
 *
 * Only public metadata/search is requested. Playback is intentionally handled
 * by YouTube's official embedded player (WebView + /embed/{videoId}), because
 * YouTube Data API does not provide a raw media stream URL for a video.
 */
object YoutubeRepository {

    data class Video(
        val videoId: String,
        val title: String,
        val channelTitle: String,
        val thumbnailUrl: String,
        val publishedAt: String,
        val description: String
    )

    private const val SEARCH_URL = "https://www.googleapis.com/youtube/v3/search"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val MAX_RESULTS = 25

    fun search(apiKey: String, query: String): Result<List<Video>> = runCatching {
        val key = apiKey.trim()
        val cleanQuery = query.trim()
        if (key.isBlank()) throw IllegalStateException("YOUTUBE_API_KEY_MISSING")
        if (cleanQuery.isBlank()) throw IllegalStateException("QUERY_EMPTY")

        val url = buildString {
            append(SEARCH_URL)
            append("?part=snippet")
            append("&type=video")
            append("&maxResults=").append(MAX_RESULTS)
            append("&order=relevance")
            append("&safeSearch=moderate")
            append("&videoEmbeddable=true")
            append("&regionCode=ID")
            append("&relevanceLanguage=id")
            append("&hl=id")
            append("&q=").append(encode(cleanQuery))
            append("&key=").append(encode(key))
        }

        val raw = httpGet(url)
        val root = JSONObject(raw)
        if (root.has("error")) {
            val error = root.optJSONObject("error")
            val reason = error?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
            val message = error?.optString("message").orEmpty()
            throw IllegalStateException(
                listOf(reason, message).filter { it.isNotBlank() }.joinToString(": ").ifBlank {
                    "YOUTUBE_API_ERROR"
                }
            )
        }

        val items = root.optJSONArray("items") ?: JSONArray()
        buildList {
            for (i in 0 until items.length()) {
                parseVideo(items.optJSONObject(i))?.let(::add)
            }
        }
    }

    private fun parseVideo(item: JSONObject?): Video? {
        if (item == null) return null
        val id = item.optJSONObject("id")?.optString("videoId")?.trim().orEmpty()
        if (id.isBlank()) return null

        val snippet = item.optJSONObject("snippet") ?: return null
        val title = snippet.optString("title").trim()
        if (title.isBlank()) return null

        val thumbnails = snippet.optJSONObject("thumbnails")
        val thumbnail = listOf("maxres", "high", "medium", "default")
            .asSequence()
            .mapNotNull { thumbnails?.optJSONObject(it)?.optString("url")?.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        return Video(
            videoId = id,
            title = decodeHtmlEntities(title),
            channelTitle = decodeHtmlEntities(snippet.optString("channelTitle").trim()),
            thumbnailUrl = thumbnail,
            publishedAt = snippet.optString("publishedAt").trim(),
            description = decodeHtmlEntities(snippet.optString("description").trim())
        )
    }

    private fun decodeHtmlEntities(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "BITTV/3.0 Android YouTube")
        }

        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val root = runCatching { JSONObject(body) }.getOrNull()
                val message = root?.optJSONObject("error")?.optString("message").orEmpty()
                throw IllegalStateException(
                    "HTTP_$code" + message.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
                )
            }
            body
        } finally {
            connection.disconnect()
        }
    }
}
