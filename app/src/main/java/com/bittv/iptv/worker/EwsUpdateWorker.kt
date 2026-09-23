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
import com.bittv.iptv.config.ConfigStore
import com.bittv.iptv.ews.EwsRepository
import com.bittv.iptv.util.EwsNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class EwsUpdateWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val config = ConfigStore.load(applicationContext)
            if (!config.notificationsEnabled) return@withContext Result.success()

            when (val result = EwsRepository(applicationContext).findNearbyHazards()) {
                is EwsRepository.Result.NoLocation -> Result.success()
                is EwsRepository.Result.NetworkError -> if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.failure()
                is EwsRepository.Result.Success -> {
                    if (result.hazards.isNotEmpty()) {
                        EwsNotification.showNearbyOnce(applicationContext, result.hazards)
                    }
                    Result.success()
                }
            }
        } catch (_: Throwable) {
            if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC_NAME = "live_tv_bmkg_ews_periodic"
        private const val NOW_NAME = "live_tv_bmkg_ews_now"
        private const val MAX_RETRY_COUNT = 3

        fun schedule(context: Context) {
            val config = ConfigStore.load(context)
            if (!config.notificationsEnabled) return
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val periodic = PeriodicWorkRequestBuilder<EwsUpdateWorker>(15L, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )
            enqueueNow(context, constraints)
        }

        fun enqueueNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            enqueueNow(context, constraints)
        }

        private fun enqueueNow(context: Context, constraints: Constraints) {
            val request = OneTimeWorkRequestBuilder<EwsUpdateWorker>()
                .setConstraints(constraints)
                .setInitialDelay(1L, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
