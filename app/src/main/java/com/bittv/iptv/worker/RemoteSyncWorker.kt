package com.bittv.iptv.worker

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bittv.iptv.config.ConfigStore
import com.bittv.iptv.config.RemoteSyncConfig
import com.bittv.iptv.util.FreeNotification
import com.bittv.iptv.util.PlaylistRepository
import com.bittv.iptv.util.PlaylistNotification
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single-flight push sync. Duplicate FCM deliveries collapse into one work
 * item; the underlying playlist/announcement stores provide content-level
 * dedupe as the second line of defense.
 */
class RemoteSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val config = ConfigStore.load(applicationContext)
        if (!config.autoUpdateEnabled && !config.notificationsEnabled) return@withContext Result.success()

        val playlistChanged = inputData.getBoolean(KEY_PLAYLIST, false)
        val announcementChanged = inputData.getBoolean(KEY_ANNOUNCEMENT, false)
        val eventId = inputData.getString(KEY_EVENT_ID).orEmpty()

        var playlistUpdated = false
        var announcementShown = false

        if (config.autoUpdateEnabled && (playlistChanged || !announcementChanged)) {
            when (val result = PlaylistRepository(applicationContext, config).checkForUpdate()) {
                is com.bittv.iptv.util.PlaylistUpdateResult.Updated -> {
                    playlistUpdated = true
                    if (config.notificationsEnabled && !result.firstRemoteSync) {
                        PlaylistNotification.showUpdatedOnce(
                            context = applicationContext,
                            revision = result.snapshot.revision,
                            diff = result.diff,
                            total = result.totalChannels
                        )
                    }
                    broadcast(applicationContext, PlaylistUpdateWorker.ACTION_PLAYLIST_UPDATED)
                }
                is com.bittv.iptv.util.PlaylistUpdateResult.NotModified -> Unit
                is com.bittv.iptv.util.PlaylistUpdateResult.Failed -> return@withContext retryOrFailure()
            }
        }

        if (config.notificationsEnabled && announcementChanged) {
            val shown = FreeNotification.checkAndShow(
                applicationContext,
                fromPushEventId = eventId.ifBlank { null }
            )
            if (shown.isFailure) return@withContext retryOrFailure()
            announcementShown = shown.getOrDefault(false)
        }

        Result.success(workDataOf(
            "playlist_updated" to playlistUpdated,
            "announcement_shown" to announcementShown,
            "event_id" to eventId
        ))
    }

    private fun retryOrFailure(): Result =
        if (runAttemptCount < 3) Result.retry() else Result.failure()

    private fun broadcast(context: Context, action: String) {
        context.sendBroadcast(Intent(action).setPackage(context.packageName))
    }

    companion object {
        private const val UNIQUE_NAME = "bittv_remote_push_sync"
        private const val KEY_EVENT_ID = "event_id"
        private const val KEY_PLAYLIST = "playlist_changed"
        private const val KEY_ANNOUNCEMENT = "announcement_changed"

        fun enqueueFromFcm(
            context: Context,
            eventId: String,
            playlistChanged: Boolean,
            announcementChanged: Boolean
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<RemoteSyncWorker>()
                .setInputData(workDataOf(
                    KEY_EVENT_ID to eventId,
                    KEY_PLAYLIST to playlistChanged,
                    KEY_ANNOUNCEMENT to announcementChanged
                ))
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request
            )
        }

        fun subscribeToTopic(context: Context) {
            if (!com.bittv.iptv.config.RemoteSyncEnrollment.isEnrolled(context)) return
            FirebaseMessaging.getInstance()
                .subscribeToTopic(RemoteSyncConfig.FCM_TOPIC)
                .addOnFailureListener { error ->
                    android.util.Log.w(
                        "BITTV-FCM",
                        "Topic subscription deferred: ${error.message}",
                        error
                    )
                }
        }
    }
}
