package com.bittv.iptv.util

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Data source fitur Musik.
 *
 * Search utama: halaman hasil YouTube -> ekstrak ytInitialData -> videoRenderer.
 * Fallback: api-faa ytplay, supaya search tetap punya hasil saat YouTube
 * mengembalikan halaman consent/challenge yang tidak berisi data pencarian.
 *
 * Playback: hasil YouTube TIDAK langsung diputar. Saat user memilih hasil,
 * resolveToMp3() memanggil api-faa/faa/ytplay untuk mendapatkan URL MP3.
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

    private const val YOUTUBE_SEARCH_URL =
        "https://www.youtube.com/results?search_query="
    private const val YTPLAY_URL = "https://api-faa.my.id/faa/ytplay?query="
    private const val TIMEOUT_MS = 15_000
    private const val MAX_RESULTS = 25

    fun search(query: String): Result<List<MusicTrack>> = runCatching {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) throw IllegalStateException("QUERY_EMPTY")

        var youtubeError: Throwable? = null
        try {
            val raw = httpGetOrThrowDetailed(
                YOUTUBE_SEARCH_URL + encode(cleanQuery) + "&hl=en&gl=US"
            )
            val tracks = parseYoutubeSearch(raw)
            if (tracks.isNotEmpty()) return@runCatching tracks
            youtubeError = IllegalStateException("YOUTUBE_EMPTY")
        } catch (t: Throwable) {
            youtubeError = t
        }

        // Fallback yang sudah terbukti pada endpoint yang user gunakan di browser.
        try {
            val fallback = searchViaYtPlay(cleanQuery)
            if (fallback.isNotEmpty()) return@runCatching fallback
        } catch (fallbackError: Throwable) {
            val first = youtubeError?.message.orEmpty()
            val second = fallbackError.message.orEmpty()
            throw IllegalStateException(
                "SEARCH_FAILED: YouTube=$first | API=$second"
            )
        }

        throw IllegalStateException(
            "EMPTY_RESULT: YouTube tidak mengirim hasil pencarian yang bisa dibaca"
        )
    }

    /** Resolve hanya ketika user memilih salah satu hasil search. */
    fun resolveToMp3(track: MusicTrack): Result<MusicTrack> = runCatching {
        if (track.mp3Url.isNotBlank()) return@runCatching track

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
                    lastError = IllegalStateException(
                        "API_STATUS_FALSE: ${root.optString("message").ifBlank { "no message" }}"
                    )
                    continue
                }

                val resolved = extractPlayableTracks(root).firstOrNull()
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
                        durationSeconds = if (track.durationSeconds > 0) {
                            track.durationSeconds
                        } else {
                            resolved.durationSeconds
                        }
                    )
                }
                lastError = IllegalStateException("MP3_NOT_FOUND")
            } catch (t: Throwable) {
                lastError = t
            }
        }

        throw (lastError ?: IllegalStateException("MP3_NOT_FOUND"))
    }

    private fun searchViaYtPlay(query: String): List<MusicTrack> {
        val raw = httpGetOrThrowDetailed(YTPLAY_URL + encode(query))
        val root = parseJsonObjectOrThrow(raw)
        if (!root.optBoolean("status", false)) return emptyList()
        return extractPlayableTracks(root)
    }

    private fun extractPlayableTracks(root: JSONObject): List<MusicTrack> {
        val out = ArrayList<MusicTrack>()
        val result = root.opt("result")
        when (result) {
            is JSONObject -> parseApiTrack(result)?.let(out::add)
            is JSONArray -> {
                for (i in 0 until result.length()) {
                    parseApiTrack(result.optJSONObject(i))?.let(out::add)
                }
            }
        }
        return out
    }

    /** Parse the current YouTube results page from its embedded ytInitialData JSON. */
    private fun parseYoutubeSearch(raw: String): List<MusicTrack> {
        val initialData = extractJsonAfterMarker(
            raw,
            listOf(
                "var ytInitialData = ",
                "ytInitialData = ",
                "window[\"ytInitialData\"] = "
            )
        ) ?: return emptyList()

        val root = runCatching { JSONObject(initialData) }.getOrNull() ?: return emptyList()
        val items = ArrayList<MusicTrack>()
        collectVideoRenderers(root, items)
        return items
            .asSequence()
            .filter { it.sourceUrl.isNotBlank() && it.title.isNotBlank() }
            .distinctBy { it.sourceUrl }
            .take(MAX_RESULTS)
            .toList()
    }

    /** Recursively finds videoRenderer/gridVideoRenderer nodes regardless of nesting. */
    private fun collectVideoRenderers(value: Any?, out: MutableList<MusicTrack>) {
        when (value) {
            is JSONObject -> {
                val video = value.optJSONObject("videoRenderer")
                    ?: value.optJSONObject("gridVideoRenderer")
                if (video != null) parseYoutubeVideo(video)?.let(out::add)

                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    collectVideoRenderers(value.opt(key), out)
                    if (out.size >= MAX_RESULTS) return
                }
            }
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    collectVideoRenderers(value.opt(i), out)
                    if (out.size >= MAX_RESULTS) return
                }
            }
        }
    }

    private fun parseYoutubeVideo(obj: JSONObject): MusicTrack? {
        val videoId = obj.optString("videoId").trim()
        if (videoId.isBlank()) return null

        val title = firstRunText(
            obj.optJSONObject("title")
                ?: obj.optJSONObject("headline")
        )
        if (title.isBlank()) return null

        val author = firstRunText(
            obj.optJSONObject("ownerText")
                ?: obj.optJSONObject("longBylineText")
                ?: obj.optJSONObject("shortBylineText")
        )

        val durationLabel = simpleText(obj.optJSONObject("lengthText"))
        val views = simpleText(obj.optJSONObject("viewCountText"))
        val published = simpleText(obj.optJSONObject("publishedTimeText"))
        val thumbnail = pickThumbnail(
            obj.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        )

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

    private fun parseApiTrack(obj: JSONObject?): MusicTrack? {
        if (obj == null) return null
        val title = obj.optString("title").trim()
        if (title.isBlank()) return null

        val durationSeconds = obj.optInt("duration", 0)
        val durationLabel = obj.optString("duration_timestamp").trim().ifBlank {
            if (durationSeconds > 0) formatDuration(durationSeconds)
            else obj.optString("duration").trim()
        }
        val thumbnail = obj.optString("thumbnail").trim()
            .ifBlank { obj.optString("imageUrl").trim() }
        val sourceUrl = obj.optString("url").trim()
            .ifBlank { obj.optString("link").trim() }
        val author = obj.optString("author").trim()
            .ifBlank { obj.optString("channel").trim() }
        val mp3 = obj.optString("mp3").trim()

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

    private fun firstRunText(obj: JSONObject?): String {
        if (obj == null) return ""
        return obj.optJSONArray("runs")?.let { runs ->
            buildString {
                for (i in 0 until runs.length()) {
                    if (isNotEmpty()) append("")
                    append(runs.optJSONObject(i)?.optString("text").orEmpty())
                }
            }.trim()
        }.orEmpty().ifBlank { simpleText(obj) }
    }

    private fun simpleText(obj: JSONObject?): String =
        obj?.optString("simpleText")?.trim().orEmpty()

    private fun pickThumbnail(thumbnails: JSONArray?): String {
        if (thumbnails == null) return ""
        var best = ""
        var bestArea = -1L
        for (i in 0 until thumbnails.length()) {
            val item = thumbnails.optJSONObject(i) ?: continue
            val url = item.optString("url").trim()
            val width = item.optLong("width", 0L)
            val height = item.optLong("height", 0L)
            val area = width * height
            if (url.isNotBlank() && area >= bestArea) {
                best = url
                bestArea = area
            }
        }
        return best
    }

    /** Extracts a balanced JSON object beginning at a known JS assignment marker. */
    private fun extractJsonAfterMarker(raw: String, markers: List<String>): String? {
        for (marker in markers) {
            var from = 0
            while (true) {
                val markerIndex = raw.indexOf(marker, from)
                if (markerIndex < 0) break
                val start = raw.indexOf('{', markerIndex + marker.length)
                if (start >= 0) {
                    val end = findBalancedObjectEnd(raw, start)
                    if (end > start) return raw.substring(start, end + 1)
                }
                from = markerIndex + marker.length
            }
        }
        return null
    }

    private fun findBalancedObjectEnd(text: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') inString = false
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

    private fun parseJsonObjectOrThrow(raw: String): JSONObject = runCatching {
        JSONObject(raw)
    }.getOrElse {
        val preview = raw.trim().take(160).replace("\n", " ")
        throw IllegalStateException("SERVER_BLOCKED: respons bukan JSON -> \"$preview\"")
    }

    private fun httpGetOrThrowDetailed(url: String): String = try {
        httpGet(url)
    } catch (e: Exception) {
        throw IllegalStateException(
            "NETWORK_ERROR: ${e.javaClass.simpleName} ${e.message ?: ""}"
        )
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
        connection.setRequestProperty(
            "Accept",
            "text/html,application/xhtml+xml,application/json,text/plain,*/*"
        )
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9,id;q=0.8")
        connection.setRequestProperty("Referer", "https://www.youtube.com/")

        val code = connection.responseCode
        if (code !in 200..299) {
            val errBody = runCatching {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            }.getOrNull().orEmpty().take(160)
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
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")
}
