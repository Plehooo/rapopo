package com.bittv.iptv.ews

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Multi-hazard EWS aggregator.
 *
 * Official feeds:
 * - BMKG earthquake/tsunami feeds
 * - BMKG CAP nowcast weather warnings
 * - MAGMA/PVMBG volcanic activity reports
 *
 * Location matching is distance based whenever the upstream source exposes a
 * coordinate/circle. The user's device coordinate is never uploaded here.
 */
class EwsRepository(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun findNearbyHazards(): Result = withContext(Dispatchers.IO) {
        val location = EwsLocationStore.read(context) ?: return@withContext Result.NoLocation
        val hazards = mutableListOf<EwsHazard>()
        var sourceSuccess = 0

        runCatching { fetchEarthquakes(location, hazards) }.onSuccess { sourceSuccess += it }
        runCatching { fetchWeatherAlerts(location, hazards) }.onSuccess { sourceSuccess += it }
        runCatching { fetchVolcanoAlerts(location, hazards) }.onSuccess { sourceSuccess += it }

        if (sourceSuccess == 0) return@withContext Result.NetworkError

        val now = System.currentTimeMillis()
        val filtered = hazards
            .asSequence()
            .filter { it.expiresAtMillis <= 0L || it.expiresAtMillis >= now }
            .filter { it.distanceKm <= radiusFor(it) }
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<EwsHazard> { it.severity.weight }
                    .thenBy { it.distanceKm }
                    .thenByDescending { it.occurredAtMillis }
            )
            .take(MAX_RETURNED_HAZARDS)
            .toList()

        Result.Success(location, filtered)
    }

    private fun fetchEarthquakes(
        location: EwsLocationStore.SavedLocation,
        out: MutableList<EwsHazard>
    ): Int {
        val urls = listOf(BMKG_EARTHQUAKE_LATEST_URL, BMKG_EARTHQUAKE_M5_URL, BMKG_EARTHQUAKE_FELT_URL)
        val earthquakes = linkedMapOf<String, EwsEarthquake>()
        var successes = 0
        for (url in urls) {
            runCatching {
                val body = getText(url) ?: return@runCatching
                parseEarthquakeFeed(body).forEach { earthquakes[it.eventId] = it }
                successes++
            }
        }

        for (event in earthquakes.values) {
            val distance = EwsDistance.kilometers(location.latitude, location.longitude, event.latitude, event.longitude)
            val tsunami = event.potential.contains("tsunami", true)
            val severity = when {
                tsunami -> EwsHazard.Severity.CRITICAL
                event.felt.isNotBlank() && distance <= 100.0 -> EwsHazard.Severity.WARNING
                event.magnitude >= 6.0 && distance <= 250.0 -> EwsHazard.Severity.WARNING
                event.magnitude >= 5.0 && distance <= 300.0 -> EwsHazard.Severity.WATCH
                event.magnitude >= 3.5 && distance <= 150.0 -> EwsHazard.Severity.ADVISORY
                else -> EwsHazard.Severity.INFO
            }
            if (tsunami && distance <= TSUNAMI_RADIUS_KM || event.felt.isNotBlank() && distance <= FELT_RADIUS_KM || event.magnitude >= MIN_LOCAL_MAGNITUDE && distance <= LOCAL_RADIUS_KM) {
                out += EwsHazard(
                    id = "bmkg-eq-${event.eventId}",
                    type = if (tsunami) EwsHazard.Type.TSUNAMI else EwsHazard.Type.EARTHQUAKE,
                    title = if (tsunami) "Potensi Tsunami" else "Gempa bumi",
                    source = "BMKG",
                    detail = buildString {
                        append("M")
                        append(String.format(Locale.US, "%.1f", event.magnitude))
                        if (event.region.isNotBlank()) append(" • ${event.region}")
                        if (event.depth.isNotBlank()) append(" • ${event.depth}")
                        if (event.potential.isNotBlank()) append(" • ${event.potential}")
                    },
                    latitude = event.latitude,
                    longitude = event.longitude,
                    distanceKm = distance,
                    severity = severity,
                    occurredAtMillis = parseIsoMillis(event.eventId)
                )
            }
        }
        return successes
    }

    private fun parseEarthquakeFeed(body: String): List<EwsEarthquake> {
        val root = JSONObject(body)
        val gempa = root.optJSONObject("Infogempa")?.opt("gempa") ?: return emptyList()
        return when (gempa) {
            is JSONObject -> listOfNotNull(parseEarthquake(gempa))
            is JSONArray -> buildList {
                for (i in 0 until gempa.length()) {
                    parseEarthquake(gempa.optJSONObject(i) ?: continue)?.let(::add)
                }
            }
            else -> emptyList()
        }
    }

    private fun parseEarthquake(item: JSONObject): EwsEarthquake? {
        val parts = item.optString("Coordinates").split(',')
        if (parts.size != 2) return null
        val lat = parts[0].trim().toDoubleOrNull() ?: return null
        val lon = parts[1].trim().toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val eventId = item.optString("DateTime").trim()
        if (eventId.isBlank()) return null
        return EwsEarthquake(
            eventId = eventId,
            date = item.optString("Tanggal").trim(),
            time = item.optString("Jam").trim(),
            magnitude = item.optString("Magnitude").replace(',', '.').toDoubleOrNull() ?: 0.0,
            depth = item.optString("Kedalaman").trim(),
            latitude = lat,
            longitude = lon,
            region = item.optString("Wilayah").trim(),
            potential = item.optString("Potensi").trim(),
            felt = item.optString("Dirasakan").trim()
        )
    }

    private fun fetchWeatherAlerts(
        location: EwsLocationStore.SavedLocation,
        out: MutableList<EwsHazard>
    ): Int {
        val rss = getText(BMKG_NOWCAST_RSS_URL)
        if (rss != null) {
            val links = extractUrls(rss)
                .filter { it.contains("bmkg.go.id") && (it.endsWith(".xml") || it.contains("alert", true)) }
                .distinct()
                .take(MAX_WEATHER_DOCS)
            var successes = 0
            for (link in links) {
                val xml = runCatching { getText(link) }.getOrNull() ?: continue
                val alert = parseCap(xml) ?: continue
                successes++
                val event = alert.toHazard(location)
                if (event != null) out += event
            }
            if (successes > 0) return 1
        }

        // Fallback langsung ke layer Nowcasting publik BMKG. Ini membuat
        // EWS tetap punya jalur cuaca saat halaman/link CAP berubah struktur.
        return fetchWeatherNowcastArcGis(location, out)
    }

    private fun fetchWeatherNowcastArcGis(
        location: EwsLocationStore.SavedLocation,
        out: MutableList<EwsHazard>
    ): Int {
        val url = BMKG_NOWCAST_ARCGIS_QUERY_URL +
            "&geometry=${location.longitude}%2C${location.latitude}"
        val body = getText(url) ?: return 0
        val features = runCatching { JSONObject(body).optJSONArray("features") }.getOrNull()
            ?: return 0

        val now = System.currentTimeMillis()
        for (i in 0 until features.length()) {
            val attrs = features.optJSONObject(i)?.optJSONObject("attributes") ?: continue
            val end = readEpochMillis(attrs, "waktuberakhir", "waktu_berakhir", "validto", "valid_until")
            if (end > 0L && end < now) continue

            val province = firstNonBlank(attrs, "namaprovinsi", "nama_provinsi", "provinsi")
            val impact = firstNonBlank(attrs, "kategoridampak", "kategori_dampak", "kategori")
            val start = readEpochMillis(attrs, "waktupembuatan", "waktu_pembuatan", "waktumulai", "validfrom")
            val detail = buildString {
                if (province.isNotBlank()) append(province)
                if (impact.isNotBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(impact)
                }
                val areaType = firstNonBlank(attrs, "tipearea", "tipe_area")
                if (areaType.isNotBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(areaType)
                }
            }.ifBlank { "Nowcasting BMKG aktif di sekitar lokasi." }

            val objectId = firstNonBlank(attrs, "OBJECTID", "objectid", "id")
            val stableId = objectId.ifBlank {
                listOf(province, impact, start.toString(), end.toString()).joinToString("|")
            }
            val severity = mapNowcastSeverity(impact)
            out += EwsHazard(
                id = "bmkg-nowcast-$stableId",
                type = EwsHazard.Type.WEATHER,
                title = if (impact.isBlank()) "Nowcasting Cuaca BMKG" else "Cuaca • $impact",
                source = "BMKG Nowcasting",
                detail = detail,
                latitude = location.latitude,
                longitude = location.longitude,
                distanceKm = 0.0,
                severity = severity,
                occurredAtMillis = start,
                expiresAtMillis = end
            )
        }
        return 1
    }

    private fun firstNonBlank(obj: JSONObject, vararg keys: String): String =
        keys.asSequence()
            .map { key -> obj.optString(key, "").trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

    private fun readEpochMillis(obj: JSONObject, vararg keys: String): Long {
        for (key in keys) {
            val raw = obj.opt(key) ?: continue
            when (raw) {
                is Number -> {
                    val value = raw.toLong()
                    if (value > 0L) return if (value < 10_000_000_000L) value * 1000L else value
                }
                else -> {
                    val text = raw.toString().trim()
                    text.toLongOrNull()?.let {
                        return if (it < 10_000_000_000L) it * 1000L else it
                    }
                    parseIsoMillis(text).takeIf { it > 0L }?.let { return it }
                }
            }
        }
        return 0L
    }

    private fun mapNowcastSeverity(value: String): EwsHazard.Severity {
        val text = value.lowercase(Locale.US)
        return when {
            "tinggi" in text || "berat" in text || "bahaya" in text -> EwsHazard.Severity.WARNING
            "sedang" in text || "waspada" in text -> EwsHazard.Severity.WATCH
            else -> EwsHazard.Severity.ADVISORY
        }
    }

    private data class CapAlert(
        val identifier: String,
        val sentMillis: Long,
        val expiresMillis: Long,
        val severity: EwsHazard.Severity,
        val headline: String,
        val description: String,
        val areas: List<GeoArea>
    ) {
        fun toHazard(location: EwsLocationStore.SavedLocation): EwsHazard? {
            if (areas.isEmpty()) return null
            val nearest = areas.minByOrNull { distanceTo(location, it) } ?: return null
            val distance = distanceTo(location, nearest)
            val type = EwsHazard.Type.WEATHER
            val title = headline.ifBlank { "Peringatan Dini Cuaca" }
            return EwsHazard(
                id = "bmkg-weather-$identifier",
                type = type,
                title = title,
                source = "BMKG",
                detail = description.lineSequence().take(3).joinToString(" ").trim(),
                latitude = nearest.lat,
                longitude = nearest.lon,
                distanceKm = distance,
                severity = severity,
                occurredAtMillis = sentMillis,
                expiresAtMillis = expiresMillis
            )
        }

        private fun distanceTo(location: EwsLocationStore.SavedLocation, area: GeoArea): Double {
            val center = EwsDistance.kilometers(location.latitude, location.longitude, area.lat, area.lon)
            return max(0.0, center - area.radiusKm)
        }
    }

    private data class GeoArea(val lat: Double, val lon: Double, val radiusKm: Double)

    private fun parseCap(xml: String): CapAlert? {
        return runCatching {
            val parser = Xml.newPullParser().apply {
                setInput(xml.reader())
            }
            var identifier = ""
            var sent = 0L
            var expires = 0L
            var severity = EwsHazard.Severity.ADVISORY
            var headline = ""
            var description = ""
            val areas = mutableListOf<GeoArea>()
            var inInfo = false
            var currentName = ""
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        currentName = parser.name
                        if (parser.name == "info") inInfo = true
                    }
                    XmlPullParser.TEXT -> {
                        val value = parser.text?.trim().orEmpty()
                        if (value.isNotEmpty()) {
                            when (currentName) {
                                "identifier" -> identifier = value
                                "sent" -> sent = parseIsoMillis(value)
                                "expires" -> expires = parseIsoMillis(value)
                                "severity" -> severity = mapSeverity(value)
                                "headline" -> headline = value
                                "description" -> description = value
                                "circle" -> parseCircle(value)?.let(areas::add)
                                "polygon" -> parsePolygonCenter(value)?.let(areas::add)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "info") inInfo = false
                        currentName = ""
                    }
                }
                parser.next()
            }
            if (identifier.isBlank() || areas.isEmpty()) return@runCatching null
            CapAlert(identifier, sent, expires, severity, headline, description, areas)
        }.getOrNull()
    }

    private fun parseCircle(value: String): GeoArea? {
        val pieces = value.split(Regex("\\s+"))
        if (pieces.size < 2) return null
        val center = pieces[0].split(',')
        if (center.size != 2) return null
        val lat = center[0].toDoubleOrNull() ?: return null
        val lon = center[1].toDoubleOrNull() ?: return null
        val radiusM = pieces[1].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return GeoArea(lat, lon, radiusM / 1000.0)
    }

    private fun parsePolygonCenter(value: String): GeoArea? {
        val points = value.trim().split(Regex("\\s+")).mapNotNull {
            val p = it.split(',')
            if (p.size != 2) null else {
                val lat = p[0].toDoubleOrNull()
                val lon = p[1].toDoubleOrNull()
                if (lat == null || lon == null) null else Pair(lat, lon)
            }
        }
        if (points.isEmpty()) return null
        val lat = points.map { it.first }.average()
        val lon = points.map { it.second }.average()
        val radius = points.maxOfOrNull { EwsDistance.kilometers(lat, lon, it.first, it.second) } ?: 0.0
        return GeoArea(lat, lon, radius)
    }

    private fun fetchVolcanoAlerts(
        location: EwsLocationStore.SavedLocation,
        out: MutableList<EwsHazard>
    ): Int {
        val listing = getText(MAGMA_VOLCANO_REPORTS_URL) ?: return 0
        val links = VOLCANO_REPORT_PATTERN.findAll(listing)
            .map { decodeHtml(it.groupValues[1]) }
            .filter { it.contains("magma.esdm.go.id/v1/gunung-api/laporan/") }
            .distinct()
            .take(MAX_VOLCANO_REPORTS)
            .toList()
        var successes = 0
        for (url in links) {
            val html = runCatching { getText(url) }.getOrNull() ?: continue
            val volcano = parseVolcanoReport(url, html) ?: continue
            successes++
            val distance = EwsDistance.kilometers(location.latitude, location.longitude, volcano.lat, volcano.lon)
            if (distance <= VOLCANO_RADIUS_KM && volcano.severity.weight >= EwsHazard.Severity.ADVISORY.weight) {
                out += EwsHazard(
                    id = "magma-volcano-${volcano.id}",
                    type = EwsHazard.Type.VOLCANO,
                    title = "${volcano.name} • ${volcano.level}",
                    source = "MAGMA / PVMBG",
                    detail = volcano.recommendation.ifBlank { volcano.summary },
                    latitude = volcano.lat,
                    longitude = volcano.lon,
                    distanceKm = distance,
                    severity = volcano.severity,
                    occurredAtMillis = volcano.reportTimeMillis
                )
            }
        }
        return if (successes > 0) 1 else 0
    }

    private data class VolcanoReport(
        val id: String,
        val name: String,
        val level: String,
        val lat: Double,
        val lon: Double,
        val severity: EwsHazard.Severity,
        val summary: String,
        val recommendation: String,
        val reportTimeMillis: Long
    )

    private fun parseVolcanoReport(url: String, html: String): VolcanoReport? {
        val id = Regex("/laporan/(\\d+)").find(url)?.groupValues?.getOrNull(1) ?: return null
        val text = htmlToText(html)
        val levelMatch = Regex("Level\\s+(I{1,3}|IV)\\s*\\(([^)]+)\\)", RegexOption.IGNORE_CASE).find(text) ?: return null
        val levelRoman = levelMatch.groupValues[1].uppercase(Locale.US)
        val levelName = "Level $levelRoman (${levelMatch.groupValues[2]})"
        val severity = when (levelRoman) {
            "IV" -> EwsHazard.Severity.CRITICAL
            "III" -> EwsHazard.Severity.WARNING
            "II" -> EwsHazard.Severity.WATCH
            else -> EwsHazard.Severity.INFO
        }
        val coord = Regex("Latitude\\s+(-?\\d+(?:\\.\\d+)?)°?[^,]*,\\s*Longitude\\s+(-?\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find(text) ?: return null
        val lat = coord.groupValues[1].toDoubleOrNull() ?: return null
        val lon = coord.groupValues[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val name = Regex("Level\\s+(?:I{1,3}|IV)\\s*\\((?:[^)]+)\\)\\s*([^,\\n]+)", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            ?: "Gunung Api"
        val recommendation = Regex("Rekomendasi\\s*(.*?)(?:Copyright|$)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(text)?.groupValues?.getOrNull(1)?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        val summary = text.lineSequence().drop(4).take(2).joinToString(" ").trim()
        return VolcanoReport(id, decodeHtml(name), levelName, lat, lon, severity, summary, recommendation, System.currentTimeMillis())
    }

    private fun radiusFor(hazard: EwsHazard): Double = when (hazard.type) {
        EwsHazard.Type.TSUNAMI -> TSUNAMI_RADIUS_KM
        EwsHazard.Type.EARTHQUAKE -> MAX_EARTHQUAKE_RADIUS_KM
        EwsHazard.Type.WEATHER -> MAX_WEATHER_RADIUS_KM
        EwsHazard.Type.VOLCANO -> VOLCANO_RADIUS_KM
        else -> GENERIC_RADIUS_KM
    }

    private fun getText(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Live TV EWS; Android")
            .header("Accept", "application/json, application/xml, text/xml, text/html;q=0.9, */*;q=0.8")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }

    private fun extractUrls(text: String): List<String> =
        Regex("https?://[^\\s<>\\\"]+").findAll(text).map { it.value.trimEnd('.', ',', ';') }.toList()

    private fun htmlToText(html: String): String = html
        .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("\\s+"), " ")
        .let(::decodeHtml)
        .trim()

    private fun decodeHtml(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    private fun parseIsoMillis(value: String): Long {
        val candidates = listOf(
            java.text.SimpleDateFormat("yyyy-MM-dd\'T\'HH:mm:ssXXX", Locale.US),
            java.text.SimpleDateFormat("yyyy-MM-dd\'T\'HH:mm:ss.SSSXXX", Locale.US),
            java.text.SimpleDateFormat("yyyy-MM-dd\'T\'HH:mm:ss\'Z\'", Locale.US)
        )
        for (format in candidates) {
            format.isLenient = false
            runCatching { format.parse(value)?.time }.getOrNull()?.let { return it }
        }
        return 0L
    }

    private fun mapSeverity(value: String): EwsHazard.Severity = when (value.lowercase(Locale.US)) {
        "extreme" -> EwsHazard.Severity.CRITICAL
        "severe" -> EwsHazard.Severity.WARNING
        "moderate" -> EwsHazard.Severity.WATCH
        else -> EwsHazard.Severity.ADVISORY
    }

    sealed class Result {
        data class Success(
            val location: EwsLocationStore.SavedLocation,
            val hazards: List<EwsHazard>
        ) : Result()
        data object NoLocation : Result()
        data object NetworkError : Result()
    }

    companion object {
        const val BMKG_EARTHQUAKE_LATEST_URL = "https://data.bmkg.go.id/DataMKG/TEWS/autogempa.json"
        const val BMKG_EARTHQUAKE_M5_URL = "https://data.bmkg.go.id/DataMKG/TEWS/gempaterkini.json"
        const val BMKG_EARTHQUAKE_FELT_URL = "https://data.bmkg.go.id/DataMKG/TEWS/gempadirasakan.json"
        const val BMKG_NOWCAST_RSS_URL = "https://www.bmkg.go.id/alerts/nowcast/id"
        const val BMKG_NOWCAST_ARCGIS_QUERY_URL = "https://nowcasting.bmkg.go.id/arcgis/rest/services/production/nowcasting_publik_phase2/MapServer/2/query?f=json&where=1%3D1&outFields=*&geometryType=esriGeometryPoint&inSR=4326&spatialRel=esriSpatialRelIntersects&returnGeometry=false&returnZ=false&returnM=false"
        const val MAGMA_VOLCANO_REPORTS_URL = "https://magma.esdm.go.id/v1/gunung-api/laporan"

        const val TSUNAMI_RADIUS_KM = 500.0
        const val MAX_EARTHQUAKE_RADIUS_KM = 500.0
        const val FELT_RADIUS_KM = 300.0
        const val LOCAL_RADIUS_KM = 150.0
        const val MIN_LOCAL_MAGNITUDE = 3.5
        const val MAX_WEATHER_RADIUS_KM = 150.0
        const val VOLCANO_RADIUS_KM = 300.0
        const val GENERIC_RADIUS_KM = 100.0
        const val MAX_WEATHER_DOCS = 25
        const val MAX_VOLCANO_REPORTS = 20
        const val MAX_RETURNED_HAZARDS = 12

        private val VOLCANO_REPORT_PATTERN = Regex(
            "href=[\\\"']([^\\\"']*magma\\.esdm\\.go\\.id/v1/gunung-api/laporan/[^\\\"']+)[\\\"']",
            RegexOption.IGNORE_CASE
        )
    }
}
