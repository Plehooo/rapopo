package com.bittv.iptv.util

import android.content.Context
import android.os.Handler
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy

@UnstableApi
object PlayerTuning {

    fun renderersFactory(context: Context): DefaultRenderersFactory =
        DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

    fun loadErrorPolicy(): DefaultLoadErrorHandlingPolicy =
        DefaultLoadErrorHandlingPolicy(6)

    fun tune(builder: DefaultTrackSelector.Parameters.Builder) {
        builder.setAllowVideoMixedMimeTypeAdaptiveness(true)
        builder.setAllowVideoNonSeamlessAdaptiveness(true)
        builder.setAllowAudioMixedMimeTypeAdaptiveness(true)
        builder.setExceedRendererCapabilitiesIfNecessary(true)
    }

    fun recoverLiveWindow(player: ExoPlayer, error: PlaybackException): Boolean {
        if (error.errorCode != PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) return false
        player.seekToDefaultPosition()
        player.prepare()
        return true
    }

    class StallWatchdog(private val handler: Handler, private val timeoutMs: Long = 20_000L) {
        private var pending: Runnable? = null

        fun arm(onStall: () -> Unit) {
            disarm()
            val r = Runnable { onStall() }
            pending = r
            handler.postDelayed(r, timeoutMs)
        }

        fun disarm() {
            pending?.let { handler.removeCallbacks(it) }
            pending = null
        }
    }
}
