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
    private val CHECK_LOCK = Any()
    private const val CHANNEL_ID = "remote_announcements"
    private const val PREFS = "bittv_free_notifications"
    private const val KEY_FINGERPRINT = "last_fingerprint"
    private const val KEY_PENDING_ID = "pending_id"
    private const val KEY_PENDING_TITLE = "pending_title"
    private const val KEY_PENDING_MESSAGE = "pending_message"
    private const val KEY_PENDING_ENABLED = "pending_enabled"
    private const val KEY_PENDING_FINGERPRINT = "pending_fingerprint"

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

    fun hasBaseline(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FINGERPRINT, null) != null

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

    /**
     * First-install baseline. Reads the current remote announcement silently
     * and remembers it, so an already-existing announcement is never pushed
     * as if it were newly published to a brand-new installation.
     */
    fun primeBaseline(context: Context): Result<Boolean> {
        return synchronized(CHECK_LOCK) {
            runCatching {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (prefs.getString(KEY_FINGERPRINT, null) != null) {
                    return@runCatching false
                }
                val payload = fetch()
                prefs.edit().putString(KEY_FINGERPRINT, payload.fingerprint).commit()
                true
            }
        }
    }

    /**
     * Render an announcement directly from an FCM data-only payload.
     * This path performs no network I/O so it also works when MainActivity
     * is completely stopped/backgrounded.
     */
    fun showFromPush(
        context: Context,
        id: String,
        title: String,
        message: String,
        enabled: Boolean,
        suppliedFingerprint: String? = null
    ): Boolean {
        return synchronized(CHECK_LOCK) {
            runCatching {
                val appContext = context.applicationContext
                if (!RemotePushManager.isBaselineReady(appContext)) return@runCatching false

                val cleanId = id.trim().ifBlank { "0" }
                val cleanTitle = title.trim()
                val cleanMessage = message.trim()
                val fingerprint = suppliedFingerprint?.trim().takeIf { !it.isNullOrBlank() }
                    ?: fingerprintOf(cleanId, enabled, cleanTitle, cleanMessage)

                val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (prefs.getString(KEY_FINGERPRINT, null) == fingerprint) {
                    clearPending(prefs)
                    return@runCatching false
                }

                if (!enabled || cleanTitle.isBlank() || cleanMessage.isBlank()) {
                    clearPending(prefs)
                    prefs.edit().putString(KEY_FINGERPRINT, fingerprint).commit()
                    return@runCatching false
                }

                if (!hasNotificationPermission(appContext) ||
                    !NotificationManagerCompat.from(appContext).areNotificationsEnabled()
                ) {
                    prefs.edit()
                        .putString(KEY_PENDING_ID, cleanId)
                        .putString(KEY_PENDING_TITLE, cleanTitle)
                        .putString(KEY_PENDING_MESSAGE, cleanMessage)
                        .putBoolean(KEY_PENDING_ENABLED, enabled)
                        .putString(KEY_PENDING_FINGERPRINT, fingerprint)
                        .commit()
                    return@runCatching false
                }

                postNotification(appContext, cleanTitle, cleanMessage)
                clearPending(prefs)
                prefs.edit().putString(KEY_FINGERPRINT, fingerprint).commit()
                true
            }.getOrDefault(false)
        }
    }

    /** Show a deferred push after the user grants notification permission. */
    fun showPending(context: Context): Boolean {
        return synchronized(CHECK_LOCK) {
            runCatching {
                val appContext = context.applicationContext
                if (!RemotePushManager.isBaselineReady(appContext) ||
                    !hasNotificationPermission(appContext) ||
                    !NotificationManagerCompat.from(appContext).areNotificationsEnabled()
                ) return@runCatching false

                val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val fingerprint = prefs.getString(KEY_PENDING_FINGERPRINT, null)
                    ?: return@runCatching false
                if (prefs.getString(KEY_FINGERPRINT, null) == fingerprint) {
                    clearPending(prefs)
                    return@runCatching false
                }

                val enabled = prefs.getBoolean(KEY_PENDING_ENABLED, true)
                val title = prefs.getString(KEY_PENDING_TITLE, "")?.trim().orEmpty()
                val message = prefs.getString(KEY_PENDING_MESSAGE, "")?.trim().orEmpty()
                if (!enabled || title.isBlank() || message.isBlank()) {
                    clearPending(prefs)
                    prefs.edit().putString(KEY_FINGERPRINT, fingerprint).commit()
                    return@runCatching false
                }

                postNotification(appContext, title, message)
                clearPending(prefs)
                prefs.edit().putString(KEY_FINGERPRINT, fingerprint).commit()
                true
            }.getOrDefault(false)
        }
    }

    fun checkAndShow(context: Context): Result<Boolean> {
        return synchronized(CHECK_LOCK) {
            runCatching {
                val payload = fetch()
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val oldFingerprint = prefs.getString(KEY_FINGERPRINT, null)

            // Always advance the local baseline first. This prevents an
            // announcement that is disabled/blank from being remembered as
            // "new" and then firing unexpectedly when it is later enabled.
                if (oldFingerprint == payload.fingerprint) {
                    return@runCatching false
                }

            if (!payload.enabled || payload.title.isBlank() || payload.message.isBlank()) {
                prefs.edit().putString(KEY_FINGERPRINT, payload.fingerprint).commit()
                return@runCatching false
            }

            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                // Do not consume the event while notification permission is
                // missing; the user may grant it later and the next sync can
                // still show the change.
                return@runCatching false
            }

            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                return@runCatching false
            }

            postNotification(context, payload.title, payload.message)
            prefs.edit().putString(KEY_FINGERPRINT, payload.fingerprint).commit()
            true
            }
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return android.os.Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun postNotification(context: Context, title: String, message: String) {
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
            .setContentTitle(title)
            .setContentText(message)
            .setSubText("LIVE TV")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun fingerprintOf(id: String, enabled: Boolean, title: String, message: String): String {
        return sha256(buildString {
            append(id.trim())
            append('\n')
            append(enabled)
            append("\n")
            append(title.trim())
            append("\n")
            append(message.trim())
        })
    }

    private fun clearPending(prefs: android.content.SharedPreferences) {
        prefs.edit()
            .remove(KEY_PENDING_ID)
            .remove(KEY_PENDING_TITLE)
            .remove(KEY_PENDING_MESSAGE)
            .remove(KEY_PENDING_ENABLED)
            .remove(KEY_PENDING_FINGERPRINT)
            .apply()
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
                append(id.trim())
                append('\n')
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
