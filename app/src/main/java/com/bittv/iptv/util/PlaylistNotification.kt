package com.bittv.iptv.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bittv.iptv.R
import com.bittv.iptv.data.PlaylistDiff
import com.bittv.iptv.ui.MainActivity

object PlaylistNotification {
    private const val CHANNEL_ID = "live_tv_updates"
    private const val PREFS = "bittv_notifications"
    private const val KEY_LAST_REVISION = "last_revision"

    // ID TETAP dengan sengaja (bukan dari `revision` yang terus naik) —
    // dulu pakai revision.toInt() sebagai ID, jadi tiap ada update malah
    // bikin notif BARU numpuk di tray, bukan gantiin yang lama. Sekarang
    // notif baru selalu MENGGANTI notif lama, sama kayak FreeNotification.
    private const val NOTIFICATION_ID = 7302

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "LIVE TV Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Pemberitahuan ketika daftar channel LIVE TV berubah"
                }
            )
        }
    }

    fun showUpdatedOnce(
        context: Context,
        revision: Long,
        diff: PlaylistDiff,
        total: Int
    ) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_LAST_REVISION, -1L) == revision) return

        ensureChannel(context)

        val parts = buildList {
            if (diff.added > 0) add("${diff.added} baru")
            if (diff.changed > 0) add("${diff.changed} diperbarui")
            if (diff.removed > 0) add("${diff.removed} dihapus")
        }

        val body = if (parts.isEmpty()) {
            "Daftar channel diperbarui • $total channel tersedia"
        } else {
            "Ada perubahan: " + parts.joinToString(" • ") + " • $total channel tersedia"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2109,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setLargeIcon(NotificationBranding.largeIcon(context))
            .setColor(NotificationBranding.accentColor(context))
            .setContentTitle("LIVE TV • Ada pembaruan")
            .setContentText(body)
            .setSubText("LIVE TV")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        prefs.edit().putLong(KEY_LAST_REVISION, revision).apply()
    }
}
