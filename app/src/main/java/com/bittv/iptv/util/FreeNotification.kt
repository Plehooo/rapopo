package com.bittv.iptv.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bittv.iptv.R
import com.bittv.iptv.ui.MainActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Remote notification feed, intentionally separate from the M3U/playlist flow.
 * Edit notification.json on GitHub without rebuilding the APK.
 */
object FreeNotification {
    private const val CHANNEL_ID = "remote_announcements"
    private const val PREFS = "bittv_free_notifications"
    private const val KEY_FINGERPRINT = "last_fingerprint"

    // ID TETAP dengan sengaja (bukan dari hash konten) — supaya notif baru
    // MENGGANTI yang lama di tray, bukan numpuk jadi banyak notif terpisah
    // tiap kali title/message di notif.json diganti.
    private const val NOTIFICATION_ID = 7301

    // This is intentionally independent from the playlist URL.
    private const val FEED_URL =
        "https://raw.githubusercontent.com/Plehooo/ditz/refs/heads/main/notif.json"

    data class Payload(
        val id: String,
        val title: String,
        val message: String,
        val enabled: Boolean,
        val fingerprint: String
    )

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Pengumuman",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifikasi bebas dari pengelola LIVE TV"
                }
            )
        }
    }

    fun checkAndShow(context: Context): Result<Boolean> {
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return@runCatching false
            }

            val payload = fetch()
            if (!payload.enabled || payload.title.isBlank() || payload.message.isBlank()) {
                return@runCatching false
            }

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getString(KEY_FINGERPRINT, null) == payload.fingerprint) {
                return@runCatching false
            }

            ensureChannel(context)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                2108,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app_logo)
                .setLargeIcon(NotificationBranding.largeIcon(context))
                .setColor(NotificationBranding.accentColor(context))
                .setContentTitle(payload.title)
                .setContentText(payload.message)
                .setSubText("LIVE TV")
                .setStyle(NotificationCompat.BigTextStyle().bigText(payload.message))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            prefs.edit().putString(KEY_FINGERPRINT, payload.fingerprint).apply()
            true
        }
    }

    private fun fetch(): Payload {
        var connection: HttpURLConnection? = null
        return try {
            val freshUrl = if (FEED_URL.contains('?')) {
                "$FEED_URL&_=${System.currentTimeMillis()}"
            } else {
                "$FEED_URL?_=${System.currentTimeMillis()}"
            }

            connection = URL(freshUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
            connection.setRequestProperty("Pragma", "no-cache")
            connection.setRequestProperty("User-Agent", "BITTV-Remote-Notification/1.0")
            connection.setRequestProperty("Accept", "application/json, text/plain, */*")

            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Notification HTTP $code")

            val raw = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (raw.isBlank()) throw IllegalStateException("Notification feed is empty")

            val json = JSONObject(raw)
            val id = json.optString("id", "0")
            val title = json.optString("title", "Pengumuman")
            val message = json.optString("message", "")
            val enabled = json.optBoolean("enabled", true)
            val fingerprintSource = buildString {
                append(enabled)
                append("\n")
                append(title.trim())
                append("\n")
                append(message.trim())
            }
            val fingerprint = sha256(fingerprintSource)

            Payload(id, title, message, enabled, fingerprint)
        } finally {
            connection?.disconnect()
        }
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
