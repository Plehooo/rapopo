package com.bittv.iptv.worker

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bittv.iptv.config.ConfigStore
import com.bittv.iptv.util.PlaylistNotification
import com.bittv.iptv.util.FreeNotification
import com.bittv.iptv.util.PlaylistRepository
import com.bittv.iptv.util.PlaylistUpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class PlaylistUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(
    appContext,
    params
) {

    override suspend fun doWork() =
        withContext(Dispatchers.IO) {

            val config =
                ConfigStore.load(
                    applicationContext
                )
            val realtime = inputData.getBoolean(KEY_REALTIME, false)

            if (!config.autoUpdateEnabled && !realtime) {
                return@withContext Result.success()
            }

            val repository =
                PlaylistRepository(
                    applicationContext,
                    config
                )

            when (
                val result =
                    repository.checkForUpdate()
            ) {

                is PlaylistUpdateResult.Updated -> {

                    PlaylistRepository.publishLatestSnapshot(result.snapshot)

                    if (
                        config.notificationsEnabled &&
                        !result.firstRemoteSync
                    ) {

                        PlaylistNotification.showUpdatedOnce(
                            context = applicationContext,
                            revision = result.snapshot.revision,
                            diff = result.diff,
                            total = result.totalChannels
                        )
                    }

                    if (realtime && config.notificationsEnabled) {
                        // The announcement feed is independent of M3U; check it
                        // in the same queued job so one FCM event cannot race two
                        // independent network jobs.
                        FreeNotification.checkAndShow(applicationContext)
                    }

                    if (realtime) {
                        applicationContext.sendBroadcast(
                            Intent(ACTION_REMOTE_PLAYLIST_UPDATED).apply {
                                setPackage(applicationContext.packageName)
                                putExtra("revision", result.snapshot.revision)
                                putExtra("channels", result.totalChannels)
                            }
                        )
                    }

                    Result.success(
                        workDataOf(
                            "updated" to true,
                            "revision" to
                                result.snapshot.revision,
                            "channels" to
                                result.totalChannels
                        )
                    )
                }

                is PlaylistUpdateResult.NotModified -> {

                    if (realtime && config.notificationsEnabled) {
                        FreeNotification.checkAndShow(applicationContext)
                    }

                    Result.success(
                        workDataOf(
                            "updated" to false,
                            "revision" to
                                result.snapshot.revision
                        )
                    )
                }

                is PlaylistUpdateResult.Failed -> {

                    if (runAttemptCount < MAX_RETRY_COUNT) {
                        Result.retry()
                    } else {
                        Result.failure(
                            workDataOf(
                                "updated" to false,
                                "error" to
                                    (result.error.message
                                        ?: "Playlist update failed")
                            )
                        )
                    }
                }
            }
        }

    companion object {

        const val ACTION_REMOTE_PLAYLIST_UPDATED = "com.bittv.iptv.REMOTE_PLAYLIST_UPDATED"
        private const val REALTIME_NAME = "live_tv_remote_realtime_sync"
        private const val KEY_REALTIME = "realtime"

        fun enqueueRealtime(context: Context, kind: String = "sync") {
            if (kind.isBlank()) return
            val request = OneTimeWorkRequestBuilder<PlaylistUpdateWorker>()
                .setInputData(workDataOf(KEY_REALTIME to true, "kind" to kind))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                REALTIME_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        private const val PERIODIC_NAME =
            "live_tv_playlist_periodic"

        private const val INITIAL_NAME =
            "live_tv_playlist_initial"

        private const val MAX_RETRY_COUNT =
            3

        fun schedule(
            context: Context
        ) {

            val config =
                ConfigStore.load(context)

            if (!config.autoUpdateEnabled) {
                return
            }

            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(
                        NetworkType.CONNECTED
                    )
                    .build()

            val periodic =
                PeriodicWorkRequestBuilder<
                    PlaylistUpdateWorker
                >(
                    config.backgroundCheckMinutes
                        .coerceAtLeast(15L),
                    TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()

            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(
                    PERIODIC_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodic
                )

            val initial =
                OneTimeWorkRequestBuilder<
                    PlaylistUpdateWorker
                >()
                    .setConstraints(constraints)
                    .setInitialDelay(
                        config.firstBackgroundDelaySeconds
                            .coerceAtLeast(5L),
                        TimeUnit.SECONDS
                    )
                    .build()

            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(
                    INITIAL_NAME,
                    ExistingWorkPolicy.KEEP,
                    initial
                )
        }
    }
}
