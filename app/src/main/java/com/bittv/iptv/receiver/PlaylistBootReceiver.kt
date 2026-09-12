package com.bittv.iptv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bittv.iptv.worker.PlaylistUpdateWorker

/**
 * Re-enqueue background playlist checking after Android finishes booting.
 * WorkManager normally persists scheduled work itself, but this receiver is
 * an explicit safety net for OEMs that restore/rebuild background scheduling.
 *
 * The app must have been launched at least once so its background work and
 * notification permission can be initialized by the user.
 */
class PlaylistBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        PlaylistUpdateWorker.schedule(context.applicationContext)
    }
}
