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

        // Scan sukses tanpa hazard juga merupakan hasil yang valid. Pada
        // instalasi baru, tandai baseline kosong agar hazard BARU pada scan
        // berikutnya langsung dianggap event baru dan bisa dinotif.
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
            )

        // Fresh install / first EWS scan: establish a silent baseline.
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            active.forEach { seen[eventKey(it)] = now } 
            prune(seen, active, now)
            saveSeen(prefs, seen, commit = true)
            prefs.edit().putBoolean(KEY_INITIALIZED, true).commit()
            return
        }

        // Full-in: SEMUA hazard baru dikirim, tidak ada lagi yang dipotong
        // diam-diam oleh batas jumlah baris. BigTextStyle akan discroll kalau
        // panjang; itu lebih baik daripada ada bahaya yang tidak pernah
        // ternotifikasi cuma karena kalah urutan di daftar.
        val unseen = active.filter { hazard ->
            seen[eventKey(hazard)] == null
        }
        if (unseen.isEmpty()) return

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val selected = unseen
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

        // Commit the event state BEFORE posting the notification. This closes
        // the race where a background worker posts a notification and Android
        // kills/recreates the app process before SharedPreferences.apply()
        // has flushed the seen-event state to disk. On the next app launch the
        // same hazard is therefore already known and cannot notify again.
        selected.forEach { seen[eventKey(it)] = now }
        prune(seen, active, now)
        saveSeen(prefs, seen, commit = true)
        prefs.edit().putBoolean(KEY_INITIALIZED, true).commit()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    
        }
    }

    private fun eventKey(hazard: EwsHazard): String {
        // A stable event identifier prevents distance jitter, feed ordering,
        // or repeated worker scans from producing duplicate alerts.
        return hazard.id.trim()
    }

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
            if (entry.value < cutoff && entry.key !in activeKeys) {
                iterator.remove()
            }
        }
        while (seen.size > MAX_HISTORY) {
            seen.remove(seen.keys.first())
        }
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
