package com.bittv.iptv.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bittv.iptv.config.ConfigStore
import com.bittv.iptv.util.EpgRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class EpgUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("bittv", Context.MODE_PRIVATE)
        val url = prefs.getString("epg_url", null).orEmpty()
        if (url.isBlank()) return@withContext Result.success()

        val repository = EpgRepository(applicationContext)
        return@withContext repository.refreshBlocking(url)
            .fold(
                onSuccess = { Result.success() },
                onFailure = {
                    if (runAttemptCount < MAX_RETRY_COUNT) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
            )
    }

    companion object {
        private const val WORK_NAME = "live_tv_epg_periodic"
        private const val MAX_RETRY_COUNT = 3

        fun schedule(context: Context) {
            val config = ConfigStore.load(context)
            if (!config.autoUpdateEnabled) return

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<EpgUpdateWorker>(
                6,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

            val initial = OneTimeWorkRequestBuilder<EpgUpdateWorker>()
                .setConstraints(constraints)
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_initial",
                ExistingWorkPolicy.KEEP,
                initial
            )
        }


        fun scheduleInitialNow(context: Context) {
            val config = ConfigStore.load(context)
            if (!config.autoUpdateEnabled) return

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val initial = OneTimeWorkRequestBuilder<EpgUpdateWorker>()
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_initial_now",
                ExistingWorkPolicy.REPLACE,
                initial
            )
        }
    }
}
