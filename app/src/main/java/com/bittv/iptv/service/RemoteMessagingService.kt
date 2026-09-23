package com.bittv.iptv.service

import android.util.Log
import com.bittv.iptv.util.FreeNotification
import com.bittv.iptv.util.RemotePushManager
import com.bittv.iptv.worker.PlaylistUpdateWorker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM data-only receiver.
 *
 * No network call is done here. The callback only queues the existing remote
 * sync worker, keeping the callback short and reliable when the app is in the
 * background or not currently open.
 */
class RemoteMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        if (data.isEmpty()) return

        if (!RemotePushManager.isBaselineReady(applicationContext)) {
            return
        }

        val kind = data["kind"].orEmpty().ifBlank { "sync" }
        Log.d(TAG, "Remote update received: $kind")

        // A data-only FCM push must render the user-visible announcement here,
        // without a network call. MainActivity may not exist at all.
        val notifTitle = data["notif_title"]
        val notifMessage = data["notif_message"]
        if (!notifTitle.isNullOrBlank() || data.containsKey("notif_fingerprint")) {
            RemotePushManager.noteRemoteAnnouncement(applicationContext)
            FreeNotification.showFromPush(
                context = applicationContext,
                id = data["notif_id"].orEmpty(),
                title = notifTitle.orEmpty(),
                message = notifMessage.orEmpty(),
                enabled = data["notif_enabled"]?.equals("true", ignoreCase = true) == true,
                suppliedFingerprint = data["notif_fingerprint"]
            )
        }

        // Playlist/sync messages use the existing worker pipeline for network
        // access. The worker publishes the new snapshot and broadcasts it to
        // a live MainActivity without requiring a manual refresh.
        if (kind != "notification") {
            PlaylistUpdateWorker.enqueueRealtime(applicationContext, kind)
        }
    }

    override fun onDeletedMessages() {
        super.onDeletedMessages()
        if (RemotePushManager.isBaselineReady(applicationContext)) {
            PlaylistUpdateWorker.enqueueRealtime(applicationContext, "resync")
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        RemotePushManager.handleTokenRefresh(applicationContext)
    }

    companion object {
        private const val TAG = "BITTV-FCM"
    }
}
