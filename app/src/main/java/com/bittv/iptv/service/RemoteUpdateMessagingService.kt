package com.bittv.iptv.service

import android.util.Log
import com.bittv.iptv.config.RemoteSyncConfig
import com.bittv.iptv.config.RemoteSyncEnrollment
import com.bittv.iptv.worker.RemoteSyncWorker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives GitHub-triggered FCM data messages while the app is foreground or
 * background. No notification payload is used so the app has one single path
 * for deduplication and live playlist synchronization.
 */
class RemoteUpdateMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != RemoteSyncConfig.FCM_EVENT_TYPE) return

        // A fresh installation must never process a shared-topic event before
        // its first successful local baseline has been established. This is a
        // second safety gate in addition to the guarded topic subscription.
        if (!RemoteSyncEnrollment.isEnrolled(applicationContext)) return

        val eventId = data["event_id"]?.takeIf { it.isNotBlank() }
            ?: message.messageId
            ?: System.currentTimeMillis().toString()
        val playlistChanged = data["playlist_changed"] == "1"
        val announcementChanged = data["announcement_changed"] == "1"

        Log.d(TAG, "Remote update event=$eventId playlist=$playlistChanged announcement=$announcementChanged")

        RemoteSyncWorker.enqueueFromFcm(
            context = applicationContext,
            eventId = eventId,
            playlistChanged = playlistChanged,
            announcementChanged = announcementChanged
        )
    }


    override fun onDeletedMessages() {
        // FCM can discard an excessive backlog. The official recovery path is
        // a full sync; use no event id so the local content baselines remain
        // authoritative and an existing announcement is not force-replayed.
        RemoteSyncWorker.enqueueFromFcm(
            context = applicationContext,
            eventId = "",
            playlistChanged = true,
            announcementChanged = true
        )
    }

    override fun onNewToken(token: String) {
        // Never enroll a fresh install before its first successful remote
        // baseline. This prevents an old GitHub notification from being
        // mistaken for new content during first install/setup.
        if (RemoteSyncEnrollment.isEnrolled(applicationContext)) {
            RemoteSyncWorker.subscribeToTopic(applicationContext)
        }
    }

    companion object {
        private const val TAG = "BITTV-FCM"
    }
}
