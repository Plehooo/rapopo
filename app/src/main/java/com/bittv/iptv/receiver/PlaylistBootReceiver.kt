package com.bittv.iptv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bittv.iptv.worker.AppUpdateWorker
import com.bittv.iptv.worker.EpgUpdateWorker
import com.bittv.iptv.worker.FreeNotificationWorker
import com.bittv.iptv.worker.PlaylistUpdateWorker

/**
 * Re-enqueue semua background worker (playlist, app update, notif promo,
 * EPG) setelah Android selesai boot. WorkManager normalnya udah persisten
 * sendiri, tapi receiver ini jaring pengaman buat OEM (Xiaomi/Oppo/dll)
 * yang suka reset/rebuild jadwal background pas reboot.
 *
 * BUG FIX: sebelumnya cuma PlaylistUpdateWorker yang di-reschedule di sini,
 * jadi AppUpdateWorker/FreeNotificationWorker/EpgUpdateWorker rawan berhenti
 * kalau OEM-nya agresif matiin background job pas reboot -> notif "lain-lain"
 * (update APK, promo, EPG) bisa mati padahal playlist tetap jalan.
 *
 * App tetap harus pernah dibuka minimal sekali biar background work dan izin
 * notifikasi udah diinisialisasi user.
 */
class PlaylistBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        PlaylistUpdateWorker.schedule(appContext)
        AppUpdateWorker.schedule(appContext)
        FreeNotificationWorker.schedule(appContext)
        EpgUpdateWorker.schedule(appContext)
    }
}
