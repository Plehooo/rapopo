package com.bittv.iptv.service

import android.util.Log
import com.bittv.iptv.util.FreeNotification
import com.bittv.iptv.util.RemotePushManager
import com.bittv.iptv.worker.PlaylistUpdateWorker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM receiver.
 *
 * Realtime announcements are sent with notification + data payloads. When the
 * app is foreground, FCM calls this service and we render the branded local
 * notification. When the app is backgrounded/not open, Firebase/Android can
 * place the notification directly in the system tray, which is more reliable
 * than depending on Activity lifecycle callbacks.
 */
class RemoteMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        if (data.isEmpty() && remoteMessage.notification == null) return

        if (!RemotePushManager.isBaselineReady(applicationContext)) {
            Log.w(TAG, "FCM received before baseline; ignoring stale/un-enrolled message")
            return
        }

        val kind = data["kind"].orEmpty().ifBlank { "notification" }
        Log.d(TAG, "Remote update received: $kind")

        // In foreground, FCM does not automatically show a notification for a
        // notification payload, so render it ourselves. Prefer explicit data
        // fields from our publisher, with the FCM notification fields as a
        // compatibility fallback for Firebase Console tests.
        val notificationPayload = remoteMessage.notification
        val notifTitle = data["notif_title"] ?: notificationPayload?.title
        val notifMessage = data["notif_message"] ?: notificationPayload?.body

        if (!notifTitle.isNullOrBlank() || data.containsKey("notif_fingerprint")) {
            val enabled = data["notif_enabled"]?.equals("true", ignoreCase = true)
                ?: (notificationPayload != null)
            RemotePushManager.noteRemoteAnnouncement(applicationContext)
            FreeNotification.showFromPush(
                context = applicationContext,
                id = data["notif_id"].orEmpty(),
                title = notifTitle.orEmpty(),
                message = notifMessage.orEmpty(),
                enabled = enabled,
                suppliedFingerprint = data["notif_fingerprint"]
            )
        }

        // Playlist/sync messages use the existing worker pipeline for network
        // access. The worker publishes the new snapshot and broadcasts it to a
        // live MainActivity without requiring a manual refresh.
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
        Log.d(TAG, "FCM token refreshed")
        RemotePushManager.handleTokenRefresh(applicationContext)
    }

    companion object {
        private const val TAG = "BITTV-FCM"
    }
}
