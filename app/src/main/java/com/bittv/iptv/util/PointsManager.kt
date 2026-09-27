package com.bittv.iptv.util

import android.content.Context

/**
 * Poin global lintas-fitur (bukan cuma skor sesi Tebak Gambar). Disimpan di
 * SharedPreferences biar persist antar sesi/restart app. Dipakai sekarang
 * oleh: jawaban benar Tebak Gambar, dan pencarian musik yang berhasil dapat
 * hasil. Gampang ditambah ke fitur lain nanti tinggal panggil addPoints().
 */
object PointsManager {
    private const val PREFS = "bittv_points"
    private const val KEY_TOTAL = "total_points"

    fun getTotal(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_TOTAL, 0)
    }

    /** Nambah poin dan return total terbaru setelah ditambah. */
    fun addPoints(context: Context, amount: Int): Int {
        if (amount == 0) return getTotal(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updated = (prefs.getInt(KEY_TOTAL, 0) + amount).coerceAtLeast(0)
        prefs.edit().putInt(KEY_TOTAL, updated).apply()
        return updated
    }
}
