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
import com.bittv.iptv.config.RemoteSyncConfig
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
    private const val KEY_INITIALIZED = "initialized"
    private const val KEY_PUSH_EVENTS = "push_events"

    // ID TETAP dengan sengaja (bukan dari hash konten) — supaya notif baru
    // MENGGANTI yang lama di tray, bukan numpuk jadi banyak notif terpisah
    // tiap kali title/message di notif.json diganti.
    private const val NOTIFICATION_ID = 7301

    // This is intentionally independent from the playlist URL.
    private const val FEED_URL = RemoteSyncConfig.NOTIFICATION_URL

    data class Payload(
        val id: String,
        val title: String,
        val message: String,
        val enabled: Boolean,
        val fingerprint: String
    )

    /**
     * Establishes the current GitHub announcement as a silent baseline. This
     * is deliberately separate from checkAndShow(): a fresh install must
     * finish this baseline before joining the shared FCM topic.
     */
    @Synchronized
    fun establishBaseline(context: Context): Result<Boolean> = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_INITIALIZED, false) &&
            !prefs.getString(KEY_FINGERPRINT, null).isNullOrBlank()
        ) {
            return@runCatching false
        }

        val payload = fetch()
        prefs.edit()
            .putBoolean(KEY_INITIALIZED, true)
            .putString(KEY_FINGERPRINT, payload.fingerprint)
            .apply()
        true
    }

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

    @Synchronized
    fun checkAndShow(
        context: Context,
        fromPushEventId: String? = null
    ): Result<Boolean> {
        return runCatching {
            val payload = fetch()
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val initialized = prefs.getBoolean(KEY_INITIALIZED, false)
            val previousFingerprint = prefs.getString(KEY_FINGERPRINT, null)

            if (!fromPushEventId.isNullOrBlank() && hasPushEvent(prefs, fromPushEventId)) {
                return@runCatching false
            }

            // A new installation records the current remote announcement as its
            // baseline. It never creates a surprise notification for content
            // that already existed before the device subscribed to FCM.
            if (!initialized && fromPushEventId.isNullOrBlank()) {
                prefs.edit()
                    .putBoolean(KEY_INITIALIZED, true)
                    .putString(KEY_FINGERPRINT, payload.fingerprint)
                    .apply()
                return@runCatching false
            }

            val changed = previousFingerprint == null || previousFingerprint != payload.fingerprint
            if (!changed) {
                // FCM is an invalidation signal; the downloaded content is the
                // authority. A GitHub commit that changes whitespace, comments,
                // or another unrelated file must never replay the same alert.
                if (!fromPushEventId.isNullOrBlank()) {
                    markState(prefs, payload.fingerprint, fromPushEventId)
                }
                return@runCatching false
            }

            if (!payload.enabled || payload.title.isBlank() || payload.message.isBlank()) {
                markState(prefs, payload.fingerprint, fromPushEventId)
                return@runCatching false
            }

            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                // Still advance the content baseline so granting permission
                // later does not replay an old announcement indefinitely.
                markState(prefs, payload.fingerprint, fromPushEventId)
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
            markState(prefs, payload.fingerprint, fromPushEventId)
            true
        }
    }

    private fun markState(
        prefs: android.content.SharedPreferences,
        fingerprint: String,
        pushEventId: String?
    ) {
        val edit = prefs.edit()
            .putBoolean(KEY_INITIALIZED, true)
            .putString(KEY_FINGERPRINT, fingerprint)
        if (!pushEventId.isNullOrBlank()) {
            val events = getPushEvents(prefs).toMutableList()
            if (!events.contains(pushEventId)) events.add(pushEventId)
            while (events.size > 32) events.removeAt(0)
            edit.putString(KEY_PUSH_EVENTS, events.joinToString("\n"))
        }
        edit.apply()
    }

    private fun hasPushEvent(prefs: android.content.SharedPreferences, eventId: String): Boolean =
        getPushEvents(prefs).contains(eventId)

    private fun getPushEvents(prefs: android.content.SharedPreferences): List<String> =
        prefs.getString(KEY_PUSH_EVENTS, null)
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.toList()
            ?: emptyList()

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
                append(id.trim())
                append("\n")
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
