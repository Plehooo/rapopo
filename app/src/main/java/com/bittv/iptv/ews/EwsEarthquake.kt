package com.bittv.iptv.ews

data class EwsEarthquake(
    val eventId: String,
    val date: String,
    val time: String,
    val magnitude: Double,
    val depth: String,
    val latitude: Double,
    val longitude: Double,
    val region: String,
    val potential: String,
    val felt: String
)
