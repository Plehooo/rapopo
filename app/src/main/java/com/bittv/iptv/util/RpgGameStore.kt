package com.bittv.iptv.util

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small persistent RPG state used by the in-app Game Hub.
 * It intentionally stays offline-first so the RPG remains playable even when
 * Firebase/network is unavailable. Multiplayer synchronizes only the profile
 * and room state; core single-player progress lives on-device.
 */
class RpgGameStore(context: Context) {
    data class State(
        val level: Int = 1,
        val xp: Int = 0,
        val gold: Int = 250,
        val hp: Int = 100,
        val maxHp: Int = 100,
        val stamina: Int = 20,
        val maxStamina: Int = 20,
        val potions: Int = 2,
        val wins: Int = 0,
        val explores: Int = 0,
        val dailyClaimDate: String = "",
        val classId: String = ""
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): State = State(
        level = prefs.getInt(KEY_LEVEL, 1).coerceAtLeast(1),
        xp = prefs.getInt(KEY_XP, 0).coerceAtLeast(0),
        gold = prefs.getInt(KEY_GOLD, 250).coerceAtLeast(0),
        hp = prefs.getInt(KEY_HP, 100).coerceAtLeast(1),
        maxHp = prefs.getInt(KEY_MAX_HP, 100).coerceAtLeast(1),
        stamina = prefs.getInt(KEY_STAMINA, 20).coerceAtLeast(0),
        maxStamina = prefs.getInt(KEY_MAX_STAMINA, 20).coerceAtLeast(1),
        potions = prefs.getInt(KEY_POTIONS, 2).coerceAtLeast(0),
        wins = prefs.getInt(KEY_WINS, 0).coerceAtLeast(0),
        explores = prefs.getInt(KEY_EXPLORES, 0).coerceAtLeast(0),
        dailyClaimDate = prefs.getString(KEY_DAILY, "").orEmpty(),
        classId = prefs.getString(KEY_CLASS, "").orEmpty()
    ).let(::normalize)

    fun save(state: State): State {
        val safe = normalize(state)
        prefs.edit()
            .putInt(KEY_LEVEL, safe.level)
            .putInt(KEY_XP, safe.xp)
            .putInt(KEY_GOLD, safe.gold)
            .putInt(KEY_HP, safe.hp)
            .putInt(KEY_MAX_HP, safe.maxHp)
            .putInt(KEY_STAMINA, safe.stamina)
            .putInt(KEY_MAX_STAMINA, safe.maxStamina)
            .putInt(KEY_POTIONS, safe.potions)
            .putInt(KEY_WINS, safe.wins)
            .putInt(KEY_EXPLORES, safe.explores)
            .putString(KEY_DAILY, safe.dailyClaimDate)
            .putString(KEY_CLASS, safe.classId)
            .apply()
        return safe
    }

    fun addRewards(state: State, xpGain: Int, goldGain: Int): State = save(
        normalize(state.copy(
            xp = state.xp + xpGain.coerceAtLeast(0),
            gold = state.gold + goldGain.coerceAtLeast(0)
        ))
    )

    fun setClass(state: State, classId: String): State {
        val safeClass = CLASS_IDS.firstOrNull { it == classId } ?: DEFAULT_CLASS
        return save(state.copy(classId = safeClass))
    }

    fun claimDaily(state: State): State? {
        val today = todayKey()
        if (state.dailyClaimDate == today) return null
        return save(normalize(state.copy(
            xp = state.xp + DAILY_XP,
            gold = state.gold + DAILY_GOLD,
            potions = state.potions + 1,
            dailyClaimDate = today
        )))
    }

    fun fullHeal(state: State, goldCost: Int = 50): State? {
        if (state.hp >= state.maxHp || state.gold < goldCost) return null
        return save(state.copy(hp = state.maxHp, gold = state.gold - goldCost))
    }

    fun usePotion(state: State): State? {
        if (state.potions <= 0 || state.hp >= state.maxHp) return null
        val heal = (state.maxHp * 0.35f).toInt().coerceAtLeast(1)
        return save(state.copy(
            hp = (state.hp + heal).coerceAtMost(state.maxHp),
            potions = state.potions - 1
        ))
    }

    fun spendStamina(state: State, amount: Int): State? {
        val safeAmount = amount.coerceAtLeast(0)
        if (state.stamina < safeAmount) return null
        return save(state.copy(stamina = state.stamina - safeAmount))
    }

    fun regenerateTick(state: State): State {
        if (state.stamina >= state.maxStamina) return state
        return save(state.copy(stamina = (state.stamina + 1).coerceAtMost(state.maxStamina)))
    }

    fun markExplore(state: State): State = save(state.copy(explores = state.explores + 1))

    fun markWin(state: State): State = save(state.copy(wins = state.wins + 1))

    fun reset(): State = save(State())

    fun xpToNext(level: Int): Int = 100 + ((level - 1).coerceAtLeast(0) * 50)

    fun normalize(state: State): State {
        var level = state.level.coerceAtLeast(1)
        var xp = state.xp.coerceAtLeast(0)
        var maxHp = state.maxHp.coerceAtLeast(100)
        var maxStamina = state.maxStamina.coerceAtLeast(20)

        while (xp >= xpToNext(level)) {
            xp -= xpToNext(level)
            level += 1
            maxHp += 15
            maxStamina += 2
        }

        return state.copy(
            level = level,
            xp = xp,
            gold = state.gold.coerceAtLeast(0),
            hp = state.hp.coerceIn(1, maxHp),
            maxHp = maxHp,
            stamina = state.stamina.coerceIn(0, maxStamina),
            maxStamina = maxStamina,
            potions = state.potions.coerceAtLeast(0),
            wins = state.wins.coerceAtLeast(0),
            explores = state.explores.coerceAtLeast(0)
        )
    }

    fun className(id: String): String = when (id) {
        CLASS_MAGE -> "Mage"
        CLASS_RANGER -> "Ranger"
        else -> "Warrior"
    }

    fun classEmoji(id: String): String = when (id) {
        CLASS_MAGE -> "🧙"
        CLASS_RANGER -> "🏹"
        else -> "⚔️"
    }

    private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    companion object {
        private const val PREFS = "bittv_rpg"
        private const val KEY_LEVEL = "level"
        private const val KEY_XP = "xp"
        private const val KEY_GOLD = "gold"
        private const val KEY_HP = "hp"
        private const val KEY_MAX_HP = "max_hp"
        private const val KEY_STAMINA = "stamina"
        private const val KEY_MAX_STAMINA = "max_stamina"
        private const val KEY_POTIONS = "potions"
        private const val KEY_WINS = "wins"
        private const val KEY_EXPLORES = "explores"
        private const val KEY_DAILY = "daily"
        private const val KEY_CLASS = "class"

        const val CLASS_WARRIOR = "warrior"
        const val CLASS_MAGE = "mage"
        const val CLASS_RANGER = "ranger"
        const val DEFAULT_CLASS = CLASS_WARRIOR
        const val DAILY_XP = 50
        const val DAILY_GOLD = 100
        val CLASS_IDS = listOf(CLASS_WARRIOR, CLASS_MAGE, CLASS_RANGER)
    }
}
