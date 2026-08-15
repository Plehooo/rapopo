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
import com.bittv.iptv.util.FreeNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Ini yang sebelumnya HILANG: FreeNotification.checkAndShow() sudah lama ada
 * di kode tapi tidak pernah dipanggil dari mana pun, jadi notif.json tidak
 * pernah benar-benar dicek. Worker ini yang manggil.
 */
class FreeNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork() =
        withContext(Dispatchers.IO) {
            val result = FreeNotification.checkAndShow(applicationContext)
            result.fold(
                onSuccess = { shown -> Result.success(workDataOf("shown" to shown)) },
                onFailure = {
                    if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.failure()
                }
            )
        }

    companion object {
        private const val PERIODIC_NAME = "live_tv_free_notification_periodic"
        private const val INITIAL_NAME = "live_tv_free_notification_initial"
        private const val MAX_RETRY_COUNT = 3
        private const val CHECK_INTERVAL_MINUTES = 30L

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val periodic = PeriodicWorkRequestBuilder<FreeNotificationWorker>(
                CHECK_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    PERIODIC_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    periodic
                )

            val initial = OneTimeWorkRequestBuilder<FreeNotificationWorker>()
                .setConstraints(constraints)
                .setInitialDelay(10L, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(INITIAL_NAME, ExistingWorkPolicy.REPLACE, initial)
        }
    }
}
