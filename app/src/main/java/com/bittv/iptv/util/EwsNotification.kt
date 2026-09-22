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
import com.bittv.iptv.ews.EwsHazard
import com.bittv.iptv.ui.MainActivity
import java.util.Locale

object EwsNotification {
    private const val CHANNEL_ID = "bmkg_ews"
    private const val PREFS = "bittv_bmkg_ews"
    private const val KEY_INITIALIZED = "initialized"
    private const val KEY_SEEN_EVENTS = "seen_events"
    private const val NOTIFICATION_ID = 7401

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "EWS Bencana Terdekat", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Peringatan bencana terdekat dari sumber resmi BMKG dan Badan Geologi"
                }
            )
        }
    }

    @Synchronized
    fun showNearbyOnce(context: Context, hazards: List<EwsHazard>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = getSeenEvents(prefs).toMutableSet()
        val initialized = prefs.getBoolean(KEY_INITIALIZED, false)

        // An empty successful scan is still a valid baseline. This prevents
        // the first real hazard after installation from being swallowed as
        // "old" simply because the first check happened on a quiet day.
        if (hazards.isEmpty()) {
            if (!initialized) prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
            return
        }

        val freshHazards = hazards
            .sortedWith(compareByDescending<EwsHazard> { it.severity.weight }.thenBy { it.distanceKm })
            .filter { stableEventKey(it) !in seen }
            .take(MAX_LINES)

        // First successful scan is a baseline only. Existing earthquakes /
        // weather / volcano reports never generate a surprise notification on
        // a fresh install (or after permissions are first granted).
        if (!initialized) {
            hazards.forEach { seen.add(stableEventKey(it)) }
            saveSeenEvents(prefs, seen)
            prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
            return
        }

        if (freshHazards.isEmpty()) return

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(context)
        val body = freshHazards.joinToString("\n") { formatHazard(it) } +
            "\n\nSumber: BMKG / MAGMA-PVMBG. Periksa kanal resmi untuk arahan keselamatan terbaru."

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            7410,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val highest = freshHazards.first()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setLargeIcon(NotificationBranding.largeIcon(context))
            .setColor(NotificationBranding.accentColor(context))
            .setContentTitle("EWS • ${highest.title}")
            .setContentText(formatHazard(highest))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        freshHazards.forEach { seen.add(stableEventKey(it)) }
        saveSeenEvents(prefs, seen)
        prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
    }

    private fun stableEventKey(hazard: EwsHazard): String =
        hazard.id.trim().lowercase(Locale.US)

    private fun getSeenEvents(prefs: android.content.SharedPreferences): List<String> =
        prefs.getString(KEY_SEEN_EVENTS, null)
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.toList()
            ?: emptyList()

    private fun saveSeenEvents(prefs: android.content.SharedPreferences, values: Set<String>) {
        val compact = values.toList().takeLast(256)
        prefs.edit().putString(KEY_SEEN_EVENTS, compact.joinToString("\n")).apply()
    }

    private fun formatHazard(hazard: EwsHazard): String {
        val distance = if (hazard.distanceKm < 10.0) {
            String.format(Locale.US, "%.1f", hazard.distanceKm)
        } else {
            String.format(Locale.US, "%.0f", hazard.distanceKm)
        }
        val detail = hazard.detail.take(180).replace(Regex("\\s+"), " ").trim()
        return "${hazard.severity.label} • ${hazard.type.label} • ±${distance} km • ${detail.ifBlank { hazard.source }}"
    }

    private const val MAX_LINES = 6
}
