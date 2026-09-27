package com.bittv.iptv.util

object HeaderParser {
    fun parse(text: String): Map<String, String> {
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) return@mapNotNull null
                val key = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).trim()
                if (key.isBlank() || value.isBlank()) null else key to value
            }
            .toMap()
    }

    fun format(headers: Map<String, String>): String = headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
}