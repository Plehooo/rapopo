package com.bittv.iptv.util

import android.content.Context

/** Local display profile. No password/account data is stored. */
object AppProfileManager {
    private const val PREFS = "bittv_profile"
    private const val KEY_NAME = "display_name"

    fun getName(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NAME, "")
            .orEmpty()
            .trim()

    fun hasName(context: Context): Boolean = getName(context).isNotBlank()

    fun setName(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAME, name.trim().take(24))
            .apply()
    }

    fun logout(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_NAME)
            .apply()
    }
}
