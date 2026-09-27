package com.bittv.iptv.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.bittv.iptv.util.GameNotification
import java.util.concurrent.TimeUnit

class GameNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        GameNotification.ensureChannel(applicationContext)
        GameNotification.show(
            applicationContext,
            "🎮 Quest BITTV siap",
            "Cek Game Hub: daily quest, streak check-in, mini game, pasar virtual, dan mabar."
        )
        return Result.success()
    }

    companion object {
        private const val NAME = "bittv_game_notifications"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<GameNotificationWorker>(24, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
