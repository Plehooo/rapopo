package com.bittv.iptv.ews

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object EwsDistance {
    private const val EARTH_RADIUS_KM = 6371.0088

    fun kilometers(
        latitude1: Double,
        longitude1: Double,
        latitude2: Double,
        longitude2: Double
    ): Double {
        val dLat = Math.toRadians(latitude2 - latitude1)
        val dLon = Math.toRadians(longitude2 - longitude1)
        val a = (sin(dLat / 2) * sin(dLat / 2)) +
            (cos(Math.toRadians(latitude1)) *
                cos(Math.toRadians(latitude2)) *
                sin(dLon / 2) * sin(dLon / 2))
        val safeA = a.coerceIn(0.0, 1.0)
        return EARTH_RADIUS_KM * 2.0 * atan2(sqrt(safeA), sqrt(1.0 - safeA))
    }
}
