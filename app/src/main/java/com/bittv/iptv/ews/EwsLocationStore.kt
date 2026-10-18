package com.bittv.iptv.ews

import android.content.Context

/** Only the last user-approved coordinate is persisted locally. */
object EwsLocationStore {
    private const val PREFS = "bittv_ews_location"
    private const val KEY_LATITUDE = "latitude"
    private const val KEY_LONGITUDE = "longitude"
    private const val KEY_TIME = "time"

    data class SavedLocation(
        val latitude: Double,
        val longitude: Double,
        val capturedAtMillis: Long
    )

    fun save(context: Context, latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || !longitude.isFinite()) return
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LATITUDE, latitude.toString())
            .putString(KEY_LONGITUDE, longitude.toString())
            .putLong(KEY_TIME, System.currentTimeMillis())
            .apply()
    }

    fun read(context: Context): SavedLocation? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val latitude = prefs.getString(KEY_LATITUDE, null)?.toDoubleOrNull() ?: return null
        val longitude = prefs.getString(KEY_LONGITUDE, null)?.toDoubleOrNull() ?: return null
        val capturedAt = prefs.getLong(KEY_TIME, 0L)
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return SavedLocation(latitude, longitude, capturedAt)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
