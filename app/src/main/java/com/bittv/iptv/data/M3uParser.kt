package com.bittv.iptv.data

import java.net.URI

object M3uParser {
    private val attributeRegex = Regex("([A-Za-z0-9_-]+)\\s*=\\s*\"([^\"]*)\"")

    fun parse(
        text: String,
        baseUrl: String? = null,
        defaultHeaders: Map<String, String> = emptyMap()
    ): List<Channel> {
        val result = ArrayList<Channel>()
        var pending: Pending? = null
        var pendingHeaders = defaultHeaders.toMutableMap()
        var generatedId = 0

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> pending = parseExtInf(line)
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    parseVlcOpt(line.substringAfter(':')).forEach { (k, v) -> pendingHeaders[k] = v }
                }
                line.startsWith("#EXTHTTP:", ignoreCase = true) -> {
                    parseHeaderBlock(line.substringAfter(':')).forEach { (k, v) -> pendingHeaders[k] = v }
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    pending = pending?.copy(group = line.substringAfter(':').trim().ifBlank { "Ungrouped" })
                }
                line.startsWith("#") -> Unit
                else -> {
                    val raw = line.substringBefore("#").trim()
                    val parts = raw.split("|", limit = 2)
                    val url = resolveUrl(parts[0], baseUrl) ?: continue
                    val inlineHeaders = if (parts.size == 2) parseHeaderBlock(parts[1]) else emptyMap()
                    val p = pending
                    val name = p?.name?.takeIf { it.isNotBlank() } ?: url
                    val id = p?.id?.takeIf { it.isNotBlank() }
                        ?: "channel-${generatedId++}"
                    val headers = pendingHeaders.toMutableMap().apply { putAll(inlineHeaders) }
                    result += Channel(
                        id = id,
                        name = name,
                        logoUrl = p?.logoUrl,
                        group = p?.group ?: "Ungrouped",
                        streamUrl = url,
                        headers = headers,
                        epgId = p?.epgId,
                        country = p?.country
                    )
                    pending = null
                    pendingHeaders = defaultHeaders.toMutableMap()
                }
            }
        }
        // tvg-id is an EPG identifier, not necessarily a unique stream identifier.
        // Keep channels that share the same tvg-id when their stream URL differs.
        return result.distinctBy {
            "${it.id}\u0000${it.streamUrl}"
        }
    }

    fun isValid(text: String, baseUrl: String? = null): Boolean {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("#EXTM3U", ignoreCase = true)) return false
        return parse(text, baseUrl).isNotEmpty()
    }

    fun playlistEpgUrl(text: String, baseUrl: String? = null): String? {
        val header = text.lineSequence().firstOrNull { it.startsWith("#EXTM3U", true) } ?: return null
        val attrs = attributeRegex.findAll(header).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val raw = attrs["url-tvg"] ?: attrs["x-tvg-url"] ?: return null
        return resolveUrl(raw, baseUrl)
    }

    private fun parseExtInf(line: String): Pending {
        // Pakai koma TERAKHIR (bukan pertama) buat misahin atribut dari nama.
        // Beberapa entri (misal 3 channel Disney di playlist adit.m3u) punya
        // "tvg-logo=URL," nyempil sebelum nama asli (bukan atribut resmi,
        // cuma teks nyasar dari sumber playlist-nya), jadi kalau pakai koma
        // pertama, nama channel-nya keambil salah (keisi teks tvg-logo itu).
        val comma = line.lastIndexOf(',')
        val attributePart = if (comma >= 0) line.substring(0, comma) else line
        val displayName = if (comma >= 0) line.substring(comma + 1).trim() else ""
        val attrs = attributeRegex.findAll(attributePart).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        return Pending(
            id = attrs["tvg-id"],
            name = attrs["tvg-name"] ?: displayName,
            logoUrl = attrs["tvg-logo"],
            group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: "Ungrouped",
            epgId = attrs["tvg-id"],
            country = attrs["tvg-country"]
        )
    }

    private fun parseVlcOpt(value: String): Map<String, String> {
        val eq = value.indexOf('=')
        if (eq <= 0) return emptyMap()
        val key = value.substring(0, eq).trim().lowercase()
        val v = value.substring(eq + 1).trim()
        return when (key) {
            "http-referrer", "http-referer" -> mapOf("Referer" to v)
            "http-user-agent" -> mapOf("User-Agent" to v)
            "http-origin" -> mapOf("Origin" to v)
            else -> emptyMap()
        }
    }

    // Header names recognized as the START of a new header entry inside a
    // block like "Referer=...&User-Agent=...". We deliberately do NOT split
    // on every '&' or '|' character: those are common inside the header
    // VALUES themselves (e.g. a Referer URL with its own query string like
    // "?vod=123&token=abc"), and naively splitting on them truncates the
    // value and injects bogus extra headers (e.g. a fake "token" header).
    // Splitting only happens right before one of these known names.
    private val knownHeaderNames = listOf(
        "referer", "referrer", "user-agent", "origin", "cookie",
        "authorization", "x-forwarded-for", "accept", "accept-language"
    )

    private val headerBoundaryRegex = Regex(
        "(?:^|[&|\\n])(?=(?:" +
            knownHeaderNames.joinToString("|") { Regex.escape(it) } +
            ")\\s*=)",
        RegexOption.IGNORE_CASE
    )

    private fun parseHeaderBlock(value: String): Map<String, String> =
        headerBoundaryRegex.split(value)
            .filter { it.isNotBlank() }
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) return@mapNotNull null
                val key = part.substring(0, eq).trim()
                val v = part.substring(eq + 1).trim()
                if (key.isBlank() || v.isBlank()) null else key to v
            }.toMap()

    private fun resolveUrl(value: String, baseUrl: String?): String? {
        if (value.startsWith("http://", true) || value.startsWith("https://", true)) return value
        if (baseUrl.isNullOrBlank()) return null
        return runCatching { URI(baseUrl).resolve(value).toString() }.getOrNull()
            ?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
    }

    private data class Pending(
        val id: String?,
        val name: String?,
        val logoUrl: String?,
        val group: String,
        val epgId: String?,
        val country: String?
    )
}
