package com.bittv.iptv.ews

/** Unified hazard model used by the local EWS engine. */
data class EwsHazard(
    val id: String,
    val type: Type,
    val title: String,
    val source: String,
    val detail: String,
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double,
    val severity: Severity,
    val occurredAtMillis: Long = 0L,
    val expiresAtMillis: Long = 0L,
    val actionable: Boolean = true
) {
    enum class Type(val label: String) {
        EARTHQUAKE("Gempa"),
        TSUNAMI("Tsunami"),
        WEATHER("Cuaca Ekstrem"),
        VOLCANO("Gunung Api"),
        LANDSLIDE("Gerakan Tanah"),
        FLOOD("Banjir"),
        MARINE("Gelombang Laut")
    }

    enum class Severity(val weight: Int, val label: String) {
        INFO(1, "Informasi"),
        ADVISORY(2, "Waspada"),
        WATCH(3, "Siaga"),
        WARNING(4, "Peringatan"),
        CRITICAL(5, "Darurat")
    }
}
