package com.bittv.iptv.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.concurrent.atomic.AtomicInteger
import com.bittv.iptv.R
import com.bittv.iptv.ui.MainActivity

object GameNotification {
    const val CHANNEL_ID = "game_events"
    const val CHANNEL_MABAR = "mabar_invites"
    const val CHANNEL_QUEST = "game_quests"
    const val CHANNEL_CONTENT = "game_content"
    const val TOPIC = "bittv_game_events"
    const val TOPIC_MABAR = "bittv_mabar"
    private val nextId = AtomicInteger(7411)

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Game & Check-in", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Quest, check-in, hadiah virtual, dan event game" })
        }
        if (manager.getNotificationChannel(CHANNEL_MABAR) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_MABAR, "Mabar & Invite", NotificationManager.IMPORTANCE_HIGH).apply { description = "Undangan room dan aktivitas multiplayer" })
        }
        if (manager.getNotificationChannel(CHANNEL_QUEST) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_QUEST, "Quest & Reward", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Quest harian, streak, achievement, dan reward" })
        }
        if (manager.getNotificationChannel(CHANNEL_CONTENT) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_CONTENT, "Content Update", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Game content pack dan event baru" })
        }
    }

    fun showFromPush(context: Context, title: String, message: String): Boolean {
        val cleanTitle = title.trim()
        val cleanMessage = message.trim()
        if (cleanTitle.isBlank() || cleanMessage.isBlank()) return false
        show(context, cleanTitle, cleanMessage)
        return true
    }

    fun show(context: Context, title: String, message: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)
        val pending = PendingIntent.getActivity(
            context,
            ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_game", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setLargeIcon(NotificationBranding.largeIcon(context))
            .setColor(NotificationBranding.accentColor(context))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val id = nextId.updateAndGet { current -> if (current >= 7999) 7411 else current + 1 }
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun showChannel(context: Context, channelId: String, title: String, message: String) {
        val cleanTitle = title.trim(); val cleanMessage = message.trim()
        if (cleanTitle.isBlank() || cleanMessage.isBlank() || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)
        val pending = PendingIntent.getActivity(context, ID + channelId.hashCode(), Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_game", true)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setLargeIcon(NotificationBranding.largeIcon(context))
            .setColor(NotificationBranding.accentColor(context))
            .setContentTitle(cleanTitle)
            .setContentText(cleanMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cleanMessage))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val id = nextId.updateAndGet { current -> if (current >= 7999) 7411 else current + 1 }
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun showMabar(context: Context, title: String, message: String) = showChannel(context, CHANNEL_MABAR, title, message)
    fun showQuest(context: Context, title: String, message: String) = showChannel(context, CHANNEL_QUEST, title, message)
    fun showContent(context: Context, title: String, message: String) = showChannel(context, CHANNEL_CONTENT, title, message)

    private const val ID = 7400
}
