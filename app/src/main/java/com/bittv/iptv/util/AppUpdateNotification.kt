package com.bittv.iptv.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bittv.iptv.R
import java.io.File

object AppUpdateNotification {
    private const val CHANNEL_ID = "live_tv_app_updates"
    private const val NOTIFICATION_ID = 9001

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Update Aplikasi",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Pemberitahuan saat ada versi baru aplikasi siap dipasang"
                }
            )
        }
    }

    fun showReadyToInstall(context: Context, versionName: String, apkFile: File, mandatory: Boolean = false) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(context)

        val installIntent = AppUpdateChecker.installIntent(context, apkFile)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setLargeIcon(NotificationBranding.largeIcon(context))
            .setColor(NotificationBranding.accentColor(context))
            .setContentTitle(
                if (mandatory) "Update WAJIB — v$versionName" else "Update tersedia — v$versionName"
            )
            .setContentText(
                if (mandatory) "Wajib dipasang untuk lanjut pakai aplikasi. Ketuk untuk update."
                else "Ketuk untuk memasang versi terbaru"
            )
            .setSubText("LIVE TV")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(!mandatory)
            .setOngoing(mandatory)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
