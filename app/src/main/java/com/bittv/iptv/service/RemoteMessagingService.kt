package com.bittv.iptv.service

import android.util.Log
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

        // Existing worker architecture is retained. One queue handles both
        // playlist and announcement checks and dedupes them through their
        // existing persisted fingerprints/revisions.
        PlaylistUpdateWorker.enqueueRealtime(applicationContext, kind)
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
