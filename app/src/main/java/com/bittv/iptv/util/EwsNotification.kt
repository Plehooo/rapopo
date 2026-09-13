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
    private const val KEY_LAST_SIGNATURE = "last_signature"
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

    fun showNearbyOnce(context: Context, hazards: List<EwsHazard>) {
        if (hazards.isEmpty()) return
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val selected = hazards.sortedWith(
            compareByDescending<EwsHazard> { it.severity.weight }.thenBy { it.distanceKm }
        ).take(MAX_LINES)
        val signature = selected.joinToString("|") { "${it.id}:${it.severity}:${it.distanceKm.toInt()}" }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_SIGNATURE, null) == signature) return

        ensureChannel(context)
        val body = selected.joinToString("\n") { formatHazard(it) } +
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

        val highest = selected.first()
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
        prefs.edit().putString(KEY_LAST_SIGNATURE, signature).apply()
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
