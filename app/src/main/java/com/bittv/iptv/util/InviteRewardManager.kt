package com.bittv.iptv.util

import android.content.Context

/** Local anti-duplicate referral reward. The social relationship itself stays server-side. */
object InviteRewardManager {
    private const val PREFS = "bittv_invite_rewards"
    private const val ACCEPT_PREFIX = "accepted_"
    private const val SENT_PREFIX = "sent_"
    private const val ACCEPT_REWARD = 25
    private const val SENT_REWARD = 10

    @Synchronized
    fun grantAcceptedFriend(context: Context, friendUid: String): Int {
        if (friendUid.isBlank()) return 0
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = ACCEPT_PREFIX + friendUid
        if (prefs.getBoolean(key, false)) return 0
        prefs.edit().putBoolean(key, true).apply()
        PointsManager.addPoints(context, ACCEPT_REWARD)
        return ACCEPT_REWARD
    }

    @Synchronized
    fun grantSentInvite(context: Context, friendUid: String): Int {
        if (friendUid.isBlank()) return 0
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = SENT_PREFIX + friendUid
        if (prefs.getBoolean(key, false)) return 0
        prefs.edit().putBoolean(key, true).apply()
        PointsManager.addPoints(context, SENT_REWARD)
        return SENT_REWARD
    }
}
