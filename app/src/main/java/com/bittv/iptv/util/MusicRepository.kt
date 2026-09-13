package com.bittv.iptv.util

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Data source fitur Musik.
 *
 * Alur sekarang:
 * 1) Cari memakai YouTube Search (YTS-style search dari halaman YouTube).
 * 2) User memilih hasil.
 * 3) Baru item terpilih di-resolve ke MP3 melalui ytplay.
 * 4) URL MP3 dikembalikan ke player.
 */
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

    private const val YOUTUBE_SEARCH_URL = "https://www.youtube.com/results?search_query="
    private const val YTPLAY_URL = "https://api-faa.my.id/faa/ytplay?query="
    private const val TIMEOUT_MS = 12_000

    /** Search hasil YouTube tanpa auto-play. */
    fun search(query: String): Result<List<MusicTrack>> = runCatching {
        val raw = httpGetOrThrowDetailed(YOUTUBE_SEARCH_URL + encode(query))
        val tracks = parseYoutubeSearch(raw)
        if (tracks.isEmpty()) {
            throw IllegalStateException("EMPTY_RESULT: hasil YouTube tidak ditemukan")
        }
        tracks
    }

    /** Resolve hanya ketika user menekan salah satu hasil search. */
    fun resolveToMp3(track: MusicTrack): Result<MusicTrack> = runCatching {
        val candidates = buildList {
            if (track.sourceUrl.isNotBlank()) add(track.sourceUrl)
            val exactQuery = buildString {
                append(track.title)
                if (track.author.isNotBlank()) append(" ").append(track.author)
            }
            if (exactQuery.isNotBlank()) add(exactQuery)
        }.distinct()

        var lastError: Throwable? = null
        for (candidate in candidates) {
            try {
                val raw = httpGetOrThrowDetailed(YTPLAY_URL + encode(candidate))
                val root = parseJsonObjectOrThrow(raw)
                if (!root.optBoolean("status", false)) {
                    lastError = IllegalStateException("API_STATUS_FALSE")
                    continue
                }

                val result = root.opt("result")
                val resolved = when (result) {
                    is JSONObject -> parseTrack(result)
                    is JSONArray -> {
                        var found: MusicTrack? = null
                        for (i in 0 until result.length()) {
                            val item = parseTrack(result.optJSONObject(i))
                            if (item != null && item.mp3Url.isNotBlank()) {
                                found = item
                                break
                            }
                        }
                        found
                    }
                    else -> null
                }

                if (resolved != null && resolved.mp3Url.isNotBlank()) {
                    return@runCatching track.copy(
                        title = resolved.title.ifBlank { track.title },
                        channel = resolved.channel.ifBlank { track.channel },
                        author = resolved.author.ifBlank { track.author },
                        thumbnailUrl = track.thumbnailUrl.ifBlank { resolved.thumbnailUrl },
                        durationLabel = track.durationLabel.ifBlank { resolved.durationLabel },
                        sourceUrl = track.sourceUrl.ifBlank { resolved.sourceUrl },
                        mp3Url = resolved.mp3Url,
                        views = track.views.ifBlank { resolved.views },
                        published = track.published.ifBlank { resolved.published },
                        durationSeconds = if (track.durationSeconds > 0) track.durationSeconds else resolved.durationSeconds
                    )
                }
                lastError = IllegalStateException("MP3_NOT_FOUND")
            } catch (t: Throwable) {
                lastError = t
            }
        }

        throw (lastError ?: IllegalStateException("MP3_NOT_FOUND"))
    }

    private fun parseYoutubeSearch(raw: String): List<MusicTrack> {
        val items = ArrayList<MusicTrack>()
        var cursor = 0
        val marker = "\"videoRenderer\":"

        while (true) {
            val markerIndex = raw.indexOf(marker, cursor)
            if (markerIndex < 0) break
            val objectStart = raw.indexOf('{', markerIndex + marker.length)
            if (objectStart < 0) break
            val objectEnd = findJsonObjectEnd(raw, objectStart)
            if (objectEnd <= objectStart) break

            val obj = runCatching {
                JSONObject(raw.substring(objectStart, objectEnd + 1))
            }.getOrNull()

            parseYoutubeVideo(obj)?.let { track ->
                if (items.none { it.sourceUrl == track.sourceUrl }) items.add(track)
            }

            cursor = objectEnd + 1
            if (items.size >= 20) break
        }

        return items
    }

    private fun parseYoutubeVideo(obj: JSONObject?): MusicTrack? {
        if (obj == null) return null

        val videoId = obj.optString("videoId").trim()
        if (videoId.isBlank()) return null

        val titleObj = obj.optJSONObject("title")
        val title = titleObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")?.trim()
            .orEmpty()
            .ifBlank { titleObj?.optString("simpleText").orEmpty().trim() }
        if (title.isBlank()) return null

        val authorObj = obj.optJSONObject("ownerText")
        val author = authorObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")?.trim()
            .orEmpty()
            .ifBlank { authorObj?.optString("simpleText").orEmpty().trim() }

        val durationLabel = obj.optJSONObject("lengthText")?.optString("simpleText").orEmpty().trim()
        val views = obj.optJSONObject("viewCountText")?.optString("simpleText").orEmpty().trim()
        val published = obj.optJSONObject("publishedTimeText")?.optString("simpleText").orEmpty().trim()
        val thumbnail = pickThumbnail(obj.optJSONObject("thumbnail")?.optJSONArray("thumbnails"))

        return MusicTrack(
            title = title,
            channel = author,
            durationLabel = durationLabel,
            thumbnailUrl = thumbnail,
            sourceUrl = "https://www.youtube.com/watch?v=$videoId",
            author = author,
            views = views,
            published = published
        )
    }

    private fun pickThumbnail(thumbnails: JSONArray?): String {
        if (thumbnails == null || thumbnails.length() == 0) return ""
        var best = ""
        for (i in 0 until thumbnails.length()) {
            val item = thumbnails.optJSONObject(i) ?: continue
            val url = item.optString("url").trim()
            if (url.isNotBlank()) best = url
        }
        return best
    }

    private fun findJsonObjectEnd(text: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
        }
        return -1
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
        connection.setRequestProperty("Accept", "text/html,application/json,application/xhtml+xml,*/*;q=0.8")
        connection.setRequestProperty("Referer", "https://www.youtube.com/")

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
