package com.bittv.iptv.util

import android.content.Context
import java.util.UUID
import kotlin.math.max

/** Local progression/cache. Competitive coin rewards remain server-authoritative. */
object GameProfileManager {
    data class Profile(
        val playerId: String,
        val displayName: String,
        val level: Int,
        val xp: Int,
        val energy: Int,
        val crystals: Int,
        val selectedCharacter: String,
        val unlockedCharacters: Set<String>,
        val inventory: Map<String, Int>,
        val completedQuests: Set<String>,
        val streak: Int,
        val bestScore: Int
    ) {
        val nextXp: Int get() = 120 + (level - 1) * 70
    }

    private const val PREFS = "bittv_game_profile_v2"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context): Profile {
        val p = prefs(context)
        val playerId = p.getString("player_id", null) ?: UUID.randomUUID().toString().also { p.edit().putString("player_id", it).apply() }
        return Profile(
            playerId = playerId,
            displayName = p.getString("display_name", AppProfileManager.getName(context).ifBlank { "Pemain" }) ?: "Pemain",
            level = p.getInt("level", 1).coerceAtLeast(1),
            xp = p.getInt("xp", 0).coerceAtLeast(0),
            energy = p.getInt("energy", 12).coerceIn(0, 30),
            crystals = p.getInt("crystals", 25).coerceAtLeast(0),
            selectedCharacter = p.getString("character", "sera") ?: "sera",
            unlockedCharacters = p.getStringSet("unlocked", setOf("sera", "kael", "luma"))?.toSet() ?: setOf("sera", "kael", "luma"),
            inventory = decodeInventory(p.getString("inventory", "") ?: ""),
            completedQuests = p.getStringSet("quests", emptySet())?.toSet() ?: emptySet(),
            streak = p.getInt("streak", 0).coerceAtLeast(0),
            bestScore = p.getInt("best_score", 0).coerceAtLeast(0)
        )
    }

    fun setDisplayName(context: Context, name: String) { prefs(context).edit().putString("display_name", name.trim().take(24).ifBlank { "Pemain" }).apply() }
    fun setCharacter(context: Context, id: String) { prefs(context).edit().putString("character", id).apply() }

    fun addXp(context: Context, amount: Int): Profile {
        val p = prefs(context)
        var level = p.getInt("level", 1).coerceAtLeast(1)
        var xp = p.getInt("xp", 0).coerceAtLeast(0) + amount.coerceAtLeast(0)
        while (xp >= 120 + (level - 1) * 70) {
            xp -= 120 + (level - 1) * 70
            level++
        }
        p.edit().putInt("level", level).putInt("xp", xp).apply()
        return get(context)
    }

    fun consumeEnergy(context: Context, amount: Int = 1): Boolean {
        val profile = get(context)
        if (profile.energy < amount) return false
        prefs(context).edit().putInt("energy", profile.energy - amount).apply()
        return true
    }

    fun addEnergy(context: Context, amount: Int) { prefs(context).edit().putInt("energy", (get(context).energy + amount).coerceAtMost(30)).apply() }
    fun addCrystals(context: Context, amount: Int) { prefs(context).edit().putInt("crystals", (get(context).crystals + amount).coerceAtLeast(0)).apply() }

    fun unlock(context: Context, id: String) {
        val set = get(context).unlockedCharacters.toMutableSet().apply { add(id) }
        prefs(context).edit().putStringSet("unlocked", set).apply()
    }

    fun addItem(context: Context, id: String, amount: Int = 1) {
        if (amount <= 0) return
        val current = get(context).inventory.toMutableMap()
        current[id] = (current[id] ?: 0) + amount
        prefs(context).edit().putString("inventory", encodeInventory(current)).apply()
    }

    fun completeQuest(context: Context, id: String): Boolean {
        val p = get(context)
        if (p.completedQuests.contains(id)) return false
        val set = p.completedQuests.toMutableSet().apply { add(id) }
        prefs(context).edit().putStringSet("quests", set).apply()
        return true
    }

    fun setBestScore(context: Context, score: Int) {
        val best = max(get(context).bestScore, score)
        prefs(context).edit().putInt("best_score", best).apply()
    }

    fun resetEnergyIfNewDay(context: Context) {
        val p = prefs(context)
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Jakarta") }
        val today = formatter.format(java.util.Date())
        val last = p.getString("energy_day", "")
        if (last != today) p.edit().putString("energy_day", today).putInt("energy", 30).apply()
    }

    private fun encodeInventory(map: Map<String, Int>): String = map.entries.joinToString(";") { "${it.key}:${it.value.coerceAtLeast(0)}" }
    private fun decodeInventory(value: String): Map<String, Int> = value.split(';').mapNotNull { token ->
        val split = token.split(':', limit = 2)
        if (split.size != 2) null else split[0].takeIf { it.isNotBlank() }?.let { id -> id to (split[1].toIntOrNull()?.coerceAtLeast(0) ?: 0) }
    }.toMap()
}
