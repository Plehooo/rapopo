package com.bittv.iptv.util

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Rewards genuine foreground playback time with local virtual points. */
object WatchRewardManager {
    data class Result(val earned: Int, val total: Int, val capped: Boolean)

    private const val PREFS = "bittv_watch_rewards"
    private const val DAY = "day"
    private const val ACCUM_MS = "accum_ms"
    private const val EARNED_TODAY = "earned_today"
    private const val INTERVAL_MS = 5L * 60L * 1000L
    private const val REWARD = 5
    private const val DAILY_CAP = 50

    fun tick(context: Context, elapsedMs: Long): Result {
        if (elapsedMs <= 0L) return Result(0, getEarned(context), isCapped(context))
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = dayStamp()
        var accumulated = prefs.getLong(ACCUM_MS, 0L)
        var earned = prefs.getInt(EARNED_TODAY, 0)
        if (prefs.getString(DAY, null) != today) {
            accumulated = 0L
            earned = 0
        }

        accumulated += elapsedMs.coerceAtMost(2L * INTERVAL_MS)
        var gained = 0
        while (accumulated >= INTERVAL_MS && earned < DAILY_CAP) {
            accumulated -= INTERVAL_MS
            earned = (earned + REWARD).coerceAtMost(DAILY_CAP)
            gained += REWARD
        }
        if (earned >= DAILY_CAP) accumulated = 0L

        prefs.edit()
            .putString(DAY, today)
            .putLong(ACCUM_MS, accumulated)
            .putInt(EARNED_TODAY, earned)
            .apply()

        if (gained > 0) PointsManager.addPoints(context, gained)
        return Result(gained, earned, earned >= DAILY_CAP)
    }

    fun getEarned(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(DAY, null) != dayStamp()) return 0
        return prefs.getInt(EARNED_TODAY, 0).coerceIn(0, DAILY_CAP)
    }

    private fun isCapped(context: Context) = getEarned(context) >= DAILY_CAP

    private fun dayStamp(): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        }
        return format.format(Date())
    }
}
