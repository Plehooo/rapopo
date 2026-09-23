package com.bittv.iptv.util

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import com.bittv.iptv.config.ConfigStore
import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps FCM enrollment behind the existing remote-state baseline.
 * A fresh install first snapshots the current remote notification feed, then
 * subscribes to the topic. This prevents an old announcement from appearing
 * immediately after installation.
 */
object RemotePushManager {
    const val TOPIC = "bittv_live_updates"

    private const val PREFS = "bittv_remote_push"
    private const val KEY_BASELINE_READY = "baseline_ready"
    private const val KEY_ENROLLED = "topic_enrolled"
    private val enrolling = AtomicBoolean(false)

    fun isBaselineReady(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BASELINE_READY, false)

    fun markBaselineReady(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BASELINE_READY, true)
            .apply()
    }

    fun ensureTopicSubscription(context: Context) {
        val appContext = context.applicationContext
        if (!isBaselineReady(appContext) || enrolling.getAndSet(true)) return
        if (!notificationsAllowed(appContext)) {
            enrolling.set(false)
            return
        }

        FreeNotification.ensureChannel(appContext)
        FirebaseMessaging.getInstance()
            .subscribeToTopic(TOPIC)
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_ENROLLED, true)
                        .apply()
                }
                enrolling.set(false)
            }
    }

    private fun notificationsAllowed(context: Context): Boolean {
        if (!ConfigStore.load(context).notificationsEnabled) return false
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun markAndSubscribe(context: Context) {
        markBaselineReady(context)
        ensureTopicSubscription(context)
    }

    fun handleTokenRefresh(context: Context) {
        if (isBaselineReady(context)) ensureTopicSubscription(context)
    }

    fun noteRemoteAnnouncement(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong("last_remote_push_ms", System.currentTimeMillis())
            .apply()
    }
}
