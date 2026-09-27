package com.bittv.iptv.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.bittv.iptv.R
import java.util.concurrent.atomic.AtomicBoolean

/** Small offline sound set: no network, no large music files, safe for low-end phones. */
object SoundFxManager {
    enum class Fx { TAP, SUCCESS, ERROR, LEVEL_UP, HIT }

    private var pool: SoundPool? = null
    private val ready = AtomicBoolean(false)
    private val ids = mutableMapOf<Fx, Int>()

    @Synchronized
    fun init(context: Context) {
        if (pool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val soundPool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()
        pool = soundPool
        soundPool.setOnLoadCompleteListener { _, _, _ ->
            if (ids.size >= Fx.values().size) ready.set(true)
        }
        ids[Fx.TAP] = soundPool.load(context, R.raw.fx_tap, 1)
        ids[Fx.HIT] = soundPool.load(context, R.raw.fx_hit, 1)
        ids[Fx.SUCCESS] = soundPool.load(context, R.raw.fx_success, 1)
        ids[Fx.ERROR] = soundPool.load(context, R.raw.fx_error, 1)
        ids[Fx.LEVEL_UP] = soundPool.load(context, R.raw.fx_level_up, 1)
    }

    fun play(context: Context, fx: Fx, volume: Float = 0.85f) {
        init(context.applicationContext)
        val soundPool = pool ?: return
        val soundId = ids[fx] ?: return
        if (!ready.get()) return
        runCatching { soundPool.play(soundId, volume, volume, 1, 0, 1f) }
    }

    @Synchronized
    fun release() {
        runCatching { pool?.release() }
        pool = null
        ids.clear()
        ready.set(false)
    }
}
