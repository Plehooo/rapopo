package com.bittv.iptv.config

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class AppConfig(
    val appName: String,
    val producer: String,
    val version: String,
    val playlistUrl: String,
    val foregroundCheckSeconds: Long,
    val backgroundCheckMinutes: Long,
    val firstBackgroundDelaySeconds: Long,
    val maxPlaylistBytes: Long,
    val minimumChannels: Int,
    val notificationsEnabled: Boolean,
    val autoUpdateEnabled: Boolean,
    val useConditionalHttp: Boolean
)

object ConfigStore {
    // This key only obfuscates configuration at rest inside the APK.
    // It is NOT a secure secret store because any APK can ultimately be inspected.
    private const val KEY_PART_A = "HCeJRBc8HO0YUAp/XFYmr7"
    private const val KEY_PART_B = "Ui4ZrirIrhVHlPiPUmPMk="

    @Volatile
    private var cached: AppConfig? = null

    fun load(context: Context): AppConfig {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val encoded = context.assets.open("config.json.enc").bufferedReader().use { it.readText().trim() }
            val decoded = decrypt(encoded)
            val json = JSONObject(decoded)
            val app = json.getJSONObject("app")
            val update = json.getJSONObject("update")
            val cache = json.getJSONObject("cache")
            val features = json.getJSONObject("features")
            return AppConfig(
                appName = app.optString("name", "LIVE TV"),
                producer = app.optString("by", "ADITIYA"),
                version = app.optString("version", "3.0.0"),
                playlistUrl = app.getString("playlistUrl"),
                foregroundCheckSeconds = update.optLong("foregroundCheckSeconds", 60L).coerceAtLeast(30L),
                backgroundCheckMinutes = update.optLong("backgroundCheckMinutes", 15L).coerceAtLeast(15L),
                firstBackgroundDelaySeconds = update.optLong("firstBackgroundDelaySeconds", 10L).coerceAtLeast(5L),
                maxPlaylistBytes = cache.optLong("maxBytes", 8L * 1024L * 1024L),
                minimumChannels = cache.optInt("minimumChannels", 1).coerceAtLeast(1),
                notificationsEnabled = update.optBoolean("notifyOnChange", true) && features.optBoolean("notifications", true),
                autoUpdateEnabled = update.optBoolean("enabled", true) && features.optBoolean("autoUpdate", true),
                useConditionalHttp = update.optBoolean("useConditionalHttp", true)
            ).also { cached = it }
        }
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(':', limit = 2)
        require(parts.size == 2) { "Invalid encrypted config" }
        val iv = Base64.decode(parts[0], Base64.DEFAULT)
        val ciphertext = Base64.decode(parts[1], Base64.DEFAULT)
        val key = Base64.decode(KEY_PART_A + KEY_PART_B, Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }
}
