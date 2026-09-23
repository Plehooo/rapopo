package com.bittv.iptv.util

import android.content.Context
import android.util.Log
import com.bittv.iptv.config.ConfigStore
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps FCM enrollment behind the existing remote-state baseline.
 * A fresh install first snapshots the current remote notification feed, then
 * subscribes to the topic. The baseline flag is repaired automatically for
 * older installs where the fingerprint exists but the separate flag was never
 * committed.
 */
object RemotePushManager {
    const val TOPIC = "bittv_live_updates"

    private const val PREFS = "bittv_remote_push"
    private const val KEY_BASELINE_READY = "baseline_ready"
    private const val KEY_ENROLLED = "topic_enrolled"
    private val enrolling = AtomicBoolean(false)

    fun isBaselineReady(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_BASELINE_READY, false)) return true

        // Migration/self-heal: the original build stored the remote
        // fingerprint but forgot to set this second flag, which permanently
        // blocked FCM subscription and made "realtime" silently fail.
        if (FreeNotification.hasBaseline(context)) {
            prefs.edit().putBoolean(KEY_BASELINE_READY, true).commit()
            return true
        }
        return false
    }

    fun markBaselineReady(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BASELINE_READY, true)
            .commit()
    }

    fun ensureTopicSubscription(context: Context) {
        val appContext = context.applicationContext
        if (!isBaselineReady(appContext) || enrolling.getAndSet(true)) return
        if (!ConfigStore.load(appContext).notificationsEnabled) {
            enrolling.set(false)
            return
        }

        // Channel creation does not require POST_NOTIFICATIONS permission.
        // Subscribe regardless of permission state so FCM enrollment is not
        // lost while the Android 13+ runtime permission dialog is pending.
        FreeNotification.ensureChannel(appContext)

        FirebaseMessaging.getInstance()
            .subscribeToTopic(TOPIC)
            .addOnCompleteListener { task ->
                val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (task.isSuccessful) {
                    prefs.edit().putBoolean(KEY_ENROLLED, true).apply()
                    Log.d(TAG, "FCM topic subscribed: $TOPIC")
                } else {
                    Log.e(TAG, "FCM topic subscribe failed: $TOPIC", task.exception)
                    prefs.edit().putBoolean(KEY_ENROLLED, false).apply()
                }
                enrolling.set(false)
            }
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

    private const val TAG = "BITTV-FCM"
}
