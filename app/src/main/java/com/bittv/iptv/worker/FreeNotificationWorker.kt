package com.bittv.iptv.worker

import android.content.Context
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
import com.bittv.iptv.util.FreeNotification
import com.bittv.iptv.util.RemotePushManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Fallback notification worker. FCM is the realtime path; this worker exists
 * only as a recovery path when a push is delayed/unavailable. The periodic
 * check never gets scheduled as a 10-second startup task.
 */
class FreeNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork() =
        withContext(Dispatchers.IO) {
            val baselineOnly = inputData.getBoolean(KEY_BASELINE_ONLY, false)
            if (!ConfigStore.load(applicationContext).notificationsEnabled && !baselineOnly) {
                return@withContext Result.success()
            }

            if (!RemotePushManager.isBaselineReady(applicationContext)) {
                val baseline = FreeNotification.primeBaseline(applicationContext)
                if (baseline.isSuccess) {
                    RemotePushManager.ensureTopicSubscription(applicationContext)
                    return@withContext Result.success(workDataOf("baselined" to true))
                }
                return@withContext if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.failure()
            }

            if (baselineOnly) {
                RemotePushManager.ensureTopicSubscription(applicationContext)
                return@withContext Result.success(workDataOf("baseline_only" to true))
            }

                val result = FreeNotification.checkAndShow(applicationContext)
            result.fold(
                onSuccess = { shown -> Result.success(workDataOf("shown" to shown)) },
                onFailure = { if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.failure() }
            )
        }

    companion object {
        private const val PERIODIC_NAME = "live_tv_free_notification_periodic"
        private const val BASELINE_RETRY_NAME = "live_tv_free_notification_baseline_retry"
        private const val CATCH_UP_NAME = "live_tv_free_notification_post_enrollment"
        private const val LEGACY_INITIAL_NAME = "live_tv_free_notification_initial"
        private const val KEY_BASELINE_ONLY = "baseline_only"
        private const val KEY_CATCH_UP = "catch_up"
        private const val MAX_RETRY_COUNT = 3
        private const val CHECK_INTERVAL_MINUTES = 30L

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            // Remove the old 10-second startup poll from previous APKs.
            wm.cancelUniqueWork(LEGACY_INITIAL_NAME)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val periodic = PeriodicWorkRequestBuilder<FreeNotificationWorker>(
                CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            wm.enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )
        }

        /**
         * Catch a remote change that happened between silent baseline and FCM
         * topic enrollment. A fresh install with no change stays silent because
         * the fingerprint still matches the baseline.
         */
        fun scheduleCatchUp(context: Context) {
            val request = OneTimeWorkRequestBuilder<FreeNotificationWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(5L, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_CATCH_UP to true))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                CATCH_UP_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        /** Silent retry used only to establish baseline/subscription after a transient first fetch failure. */
        fun scheduleBaselineRetry(context: Context) {
            val request = OneTimeWorkRequestBuilder<FreeNotificationWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(15L, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_BASELINE_ONLY to true))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                BASELINE_RETRY_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }

}
