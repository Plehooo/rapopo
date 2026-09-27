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
import org.json.JSONObject
import java.util.Locale

object EwsNotification {
    private val EVENT_LOCK = Any()
    private const val CHANNEL_ID = "bmkg_ews"
    private const val PREFS = "bittv_bmkg_ews"
    private const val KEY_SEEN = "seen_events"
    private const val KEY_INITIALIZED = "seen_initialized"
    private const val NOTIFICATION_ID = 7401

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "EWS Bencana Terdekat",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Peringatan bencana terdekat dari sumber resmi BMKG dan Badan Geologi"
                }
            )
        }
    }

    fun showNearbyOnce(context: Context, hazards: List<EwsHazard>) {
        synchronized(EVENT_LOCK) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()

            // Hasil kosong juga valid. Baseline ini mencegah scan berikutnya
            // dianggap "scan pertama" terus-menerus.
            if (hazards.isEmpty()) {
                if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
                    prefs.edit().putBoolean(KEY_INITIALIZED, true).commit()
                }
                return
            }

            val seen = loadSeen(prefs)
            val active = hazards
                .filter { it.expiresAtMillis <= 0L || it.expiresAtMillis >= now }
                .distinctBy(::eventKey)
                .sortedWith(
                    compareByDescending<EwsHazard> { it.severity.weight }
                        .thenBy { it.distanceKm }
                        .thenByDescending { it.occurredAtMillis }
                )

            val initialized = prefs.getBoolean(KEY_INITIALIZED, false)
            if (!initialized) {
                // Pada pemasangan baru, bahaya yang cukup serius tetap wajib
                // masuk sebagai alert. INFO/WASPADA cukup menjadi baseline.
                val urgent = active.filter { it.severity.weight >= EwsHazard.Severity.WATCH.weight }
                active.forEach { seen[eventKey(it)] = now }
                prune(seen, active, now)
                saveSeen(prefs, seen, commit = true)
                prefs.edit().putBoolean(KEY_INITIALIZED, true).commit()
                if (urgent.isEmpty()) return
                if (!notificationsAllowed(context)) return
                postNotification(context, urgent, now)
                return
            }

            // Semua event baru boleh masuk notifikasi; tidak dipotong cuma
            // karena banyak event ditemukan dalam satu scan.
            val unseen = active.filter { seen[eventKey(it)] == null }
            if (unseen.isEmpty()) return
            if (!notificationsAllowed(context)) return

            // Commit sebelum notify: lifecycle/process death tidak akan membuat
            // scan berikutnya mengirim event yang sama lagi.
            unseen.forEach { seen[eventKey(it)] = now }
            prune(seen, active, now)
            saveSeen(prefs, seen, commit = true)
            prefs.edit().putBoolean(KEY_INITIALIZED, true).commit()
            postNotification(context, unseen, now)
        }
    }

    private fun notificationsAllowed(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun postNotification(context: Context, selected: List<EwsHazard>, now: Long) {
        if (selected.isEmpty()) return
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
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(highest.occurredAtMillis.takeIf { it > 0L } ?: now)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun eventKey(hazard: EwsHazard): String = hazard.id.trim()

    private fun loadSeen(prefs: android.content.SharedPreferences): MutableMap<String, Long> {
        val result = LinkedHashMap<String, Long>()
        val raw = prefs.getString(KEY_SEEN, null).orEmpty()
        runCatching {
            val obj = JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = obj.optLong(key, 0L)
            }
        }
        return result
    }

    private fun saveSeen(
        prefs: android.content.SharedPreferences,
        seen: Map<String, Long>,
        commit: Boolean = false
    ) {
        val obj = JSONObject()
        seen.entries.toList().takeLast(MAX_HISTORY).forEach { (key, value) -> obj.put(key, value) }
        val editor = prefs.edit().putString(KEY_SEEN, obj.toString())
        if (commit) editor.commit() else editor.apply()
    }

    private fun prune(
        seen: MutableMap<String, Long>,
        active: List<EwsHazard>,
        now: Long
    ) {
        val activeKeys = active.map { eventKey(it) }.toSet()
        val cutoff = now - HISTORY_MILLIS
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value < cutoff && entry.key !in activeKeys) iterator.remove()
        }
        while (seen.size > MAX_HISTORY) seen.remove(seen.keys.first())
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

    private const val MAX_HISTORY = 200
    private const val HISTORY_MILLIS = 7L * 24L * 60L * 60L * 1000L
}
