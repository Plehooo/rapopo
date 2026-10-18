package com.bittv.iptv.util

import org.xmlpull.v1.XmlPullParser
import android.util.Xml
import java.io.StringReader

data class EpgProgramme(
    val channelId: String,
    val start: Long,
    val end: Long,
    val title: String,
    val description: String?
)

object EpgParser {
    fun parse(xml: String): List<EpgProgramme> {
        val result = mutableListOf<EpgProgramme>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var event = parser.eventType
        var currentChannel: String? = null
        var start = 0L
        var end = 0L
        var title: String? = null
        var desc: String? = null
        var inTitle = false
        var inDesc = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                    "programme" -> {
                        currentChannel = parser.getAttributeValue(null, "channel")
                        start = parseXmltvTime(parser.getAttributeValue(null, "start"))
                        end = parseXmltvTime(parser.getAttributeValue(null, "stop"))
                        title = null; desc = null
                    }
                    "title" -> inTitle = true
                    "desc" -> inDesc = true
                }
                XmlPullParser.TEXT -> {
                    if (inTitle) title = parser.text.trim()
                    if (inDesc) desc = parser.text.trim()
                }
                XmlPullParser.END_TAG -> when (parser.name.lowercase()) {
                    "title" -> inTitle = false
                    "desc" -> inDesc = false
                    "programme" -> {
                        val id = currentChannel
                        if (!id.isNullOrBlank() && !title.isNullOrBlank()) {
                            result += EpgProgramme(id, start, end, title!!, desc)
                        }
                        currentChannel = null
                    }
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun parseXmltvTime(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val clean = value.trim()
        return runCatching {
            val parts = clean.split(Regex("\\s+"))
            val base = parts[0].take(14)
            val zone = parts.getOrNull(1)
            val pattern = if (zone.isNullOrBlank()) "yyyyMMddHHmmss" else "yyyyMMddHHmmss Z"
            val input = if (zone.isNullOrBlank()) base else "$base $zone"
            val fmt = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
            if (zone.isNullOrBlank()) fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
            fmt.parse(input)?.time ?: 0L
        }.getOrDefault(0L)
    }
}
