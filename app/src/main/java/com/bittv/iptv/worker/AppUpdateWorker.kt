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
import com.bittv.iptv.util.AppUpdateChecker
import com.bittv.iptv.util.AppUpdateNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class AppUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork() =
        withContext(Dispatchers.IO) {
            when (val result = AppUpdateChecker.checkAndDownload(applicationContext)) {

                is AppUpdateChecker.UpdateResult.Available -> {
                    AppUpdateNotification.showReadyToInstall(
                        applicationContext,
                        result.versionName,
                        result.apkFile,
                        result.mandatory
                    )
                    Result.success(
                        workDataOf(
                            "updateAvailable" to true,
                            "mandatory" to result.mandatory
                        )
                    )
                }

                is AppUpdateChecker.UpdateResult.UpToDate -> {
                    Result.success(workDataOf("updateAvailable" to false))
                }

                is AppUpdateChecker.UpdateResult.Failed -> {
                    if (runAttemptCount < MAX_RETRY_COUNT) {
                        Result.retry()
                    } else {
                        Result.failure(
                            workDataOf("error" to (result.error.message ?: "Update check failed"))
                        )
                    }
                }
            }
        }

    companion object {
        private const val PERIODIC_NAME = "live_tv_app_update_periodic"
        private const val INITIAL_NAME = "live_tv_app_update_initial"
        private const val MAX_RETRY_COUNT = 3
        private const val CHECK_INTERVAL_MINUTES = 60L

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val periodic = PeriodicWorkRequestBuilder<AppUpdateWorker>(
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

            val initial = OneTimeWorkRequestBuilder<AppUpdateWorker>()
                .setConstraints(constraints)
                .setInitialDelay(15L, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(INITIAL_NAME, ExistingWorkPolicy.KEEP, initial)
        }
    }
}
