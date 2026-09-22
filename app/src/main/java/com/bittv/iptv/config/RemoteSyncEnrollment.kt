package com.bittv.iptv.config

import android.content.Context

/**
 * Controls when this installation is allowed to subscribe to the shared FCM
 * topic. A fresh install is enrolled only after a successful playlist baseline
 * has been established, so an already-existing GitHub announcement cannot look
 * like a new notification to a brand-new device.
 */
object RemoteSyncEnrollment {
    private const val PREFS = "bittv_remote_sync"
    private const val KEY_ENROLLED = "enrolled"

    fun isEnrolled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENROLLED, false)

    fun markEnrolled(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENROLLED, true)
            .apply()
    }
}
