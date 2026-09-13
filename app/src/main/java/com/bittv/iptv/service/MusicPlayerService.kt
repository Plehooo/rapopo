package com.bittv.iptv.service

import android.app.PendingIntent
import android.content.Intent

import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.bittv.iptv.ui.MainActivity

/**
 * Service player musik terpisah dari player TV di MainActivity. Bedanya
 * sama TV: video TV memang sengaja dipause pas app diminimize/pindah tab
 * (buang-buang data/baterai kalau video jalan gak keliatan). Musik audio-only
 * gak masalah jalan di background, jadi dibikin MediaSessionService beneran
 * (foreground service + notifikasi kontrol), persis kayak Spotify — lagu
 * tetap lanjut walau app diminimize, layar dimatiin, atau di-swipe dari
 * recent apps (MediaSessionService bawaan Media3 otomatis TETAP jalan kalau
 * lagu masih playing pas task di-remove, dan baru berhenti sendiri kalau
 * lagi paused).
 *
 * Audio focus ditangani otomatis lewat setHandleAudioBecomingNoisy +
 * setAudioAttributes(..., true) di bawah, jadi kalau video TV mulai diputer
 * lagi, ExoPlayer musik ini otomatis kalah fokus (auto pause) tanpa perlu
 * kode manual buat "matiin salah satu".
 */
class MusicPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            )
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Klik area notifikasi / label LIVE TV membuka kembali MainActivity.
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            7702,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                // Begitu lagu benar-benar habis, service dihentikan sehingga
                // media notification ikut hilang dari panel notifikasi.
                if (playbackState == Player.STATE_ENDED) {
                    stopSelf()
                }
            }
        })

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Kalau task di-swipe dan lagu lagi gak playing, ikutan berhenti — biar gak nyangkut foreground service sia-sia. */
    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val session = mediaSession ?: return
        if (!session.player.playWhenReady || session.player.mediaItemCount == 0) {
            session.player.stop()
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
