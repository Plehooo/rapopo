package com.bittv.iptv.util

import android.content.Context

/**
 * Poin global lintas-fitur. Tetap kompatibel dengan API lama, ditambah
 * pengeluaran poin + bonus harian untuk Game Hub / Point Shop.
 */
object PointsManager {
    private const val PREFS = "bittv_points"
    private const val KEY_TOTAL = "total_points"
    private const val KEY_DAILY_CLAIM = "daily_claim"
    private const val DAILY_BONUS = 10
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    fun getTotal(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_TOTAL, 0)
    }

    /** Backward-compatible alias used by newer Gameverse screens. */
    fun getPoints(context: Context): Int = getTotal(context)

    /** Nambah poin dan return total terbaru setelah ditambah. */
    fun addPoints(context: Context, amount: Int): Int {
        if (amount == 0) return getTotal(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updated = (prefs.getInt(KEY_TOTAL, 0) + amount).coerceAtLeast(0)
        prefs.edit().putInt(KEY_TOTAL, updated).apply()
        return updated
    }

    /** Spend only when the balance is sufficient. Returns true on success. */
    fun spendPoints(context: Context, amount: Int): Boolean {
        if (amount <= 0) return true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getInt(KEY_TOTAL, 0)
        if (current < amount) return false
        prefs.edit().putInt(KEY_TOTAL, current - amount).apply()
        return true
    }

    /** Daily reward. Null means the reward was already claimed within 24h. */
    fun claimDailyBonus(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_DAILY_CLAIM, 0L)
        if (last > 0L && now - last < DAY_MS) return null
        prefs.edit().putLong(KEY_DAILY_CLAIM, now).apply()
        return addPoints(context, DAILY_BONUS)
    }
}
