package com.bittv.iptv.util

import android.content.Context
import kotlin.math.max

object GameProgressManager {
    data class RpgState(
        var level: Int,
        var xp: Int,
        var hp: Int,
        var maxHp: Int,
        var weaponLevel: Int,
        var armorLevel: Int,
        var potions: Int
    ) {
        val attack: Int get() = 10 + level * 3 + weaponLevel * 4
        val defense: Int get() = 4 + level * 2 + armorLevel * 3
        val xpToNext: Int get() = max(40, level * 100)
    }

    data class ShopItem(
        val id: String,
        val name: String,
        val cost: Int,
        val description: String
    )

    val shopItems = listOf(
        ShopItem("potion", "Potion HP", 20, "Pulihkan 35 HP di RPG"),
        ShopItem("sword", "Power Core", 80, "Naikkan power +4"),
        ShopItem("armor", "Guardian Gear", 80, "Naikkan defense +3"),
        ShopItem("xp_book", "XP Book", 50, "Tambah 30 XP dan bisa naik level")
    )

    private const val PREFS = "bittv_rpg"
    private const val LEVEL = "level"
    private const val XP = "xp"
    private const val HP = "hp"
    private const val MAX_HP = "max_hp"
    private const val WEAPON = "weapon"
    private const val ARMOR = "armor"
    private const val POTIONS = "potions"

    fun get(context: Context): RpgState {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val level = p.getInt(LEVEL, 1).coerceAtLeast(1)
        val maxHp = p.getInt(MAX_HP, 100 + (level - 1) * 15).coerceAtLeast(100)
        return RpgState(
            level = level,
            xp = p.getInt(XP, 0).coerceAtLeast(0),
            hp = p.getInt(HP, maxHp).coerceIn(0, maxHp),
            maxHp = maxHp,
            weaponLevel = p.getInt(WEAPON, 0).coerceAtLeast(0),
            armorLevel = p.getInt(ARMOR, 0).coerceAtLeast(0),
            potions = p.getInt(POTIONS, 0).coerceAtLeast(0)
        )
    }

    fun save(context: Context, state: RpgState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(LEVEL, state.level)
            .putInt(XP, state.xp)
            .putInt(HP, state.hp.coerceIn(0, state.maxHp))
            .putInt(MAX_HP, state.maxHp)
            .putInt(WEAPON, state.weaponLevel)
            .putInt(ARMOR, state.armorLevel)
            .putInt(POTIONS, state.potions)
            .apply()
    }

    /** Returns the refreshed state after XP is awarded. */
    fun addXp(context: Context, amount: Int): RpgState {
        val state = get(context)
        state.xp += amount.coerceAtLeast(0)
        while (state.xp >= state.xpToNext) {
            state.xp -= state.xpToNext
            state.level += 1
            state.maxHp += 15
            state.hp = state.maxHp
        }
        save(context, state)
        return state
    }

    fun buy(context: Context, item: ShopItem): Boolean {
        if (!PointsManager.spendPoints(context, item.cost)) return false
        val state = get(context)
        when (item.id) {
            "potion" -> state.potions += 1
            "sword" -> state.weaponLevel += 1
            "armor" -> state.armorLevel += 1
            "xp_book" -> {
                save(context, state)
                addXp(context, 30)
                return true
            }
        }
        save(context, state)
        return true
    }


    /** Sell one owned virtual item back to the Point Shop for 50% value. */
    fun sell(context: Context, item: ShopItem): Boolean {
        val state = get(context)
        val sold = when (item.id) {
            "potion" -> if (state.potions > 0) { state.potions--; true } else false
            "sword" -> if (state.weaponLevel > 0) { state.weaponLevel--; true } else false
            "armor" -> if (state.armorLevel > 0) { state.armorLevel--; true } else false
            else -> false
        }
        if (!sold) return false
        save(context, state)
        PointsManager.addPoints(context, (item.cost / 2).coerceAtLeast(1))
        return true
    }

    fun drinkPotion(context: Context): Boolean {
        val state = get(context)
        if (state.potions <= 0 || state.hp >= state.maxHp) return false
        state.potions -= 1
        state.hp = (state.hp + 35).coerceAtMost(state.maxHp)
        save(context, state)
        return true
    }
}
