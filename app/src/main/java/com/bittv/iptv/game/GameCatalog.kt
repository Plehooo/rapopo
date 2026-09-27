package com.bittv.iptv.game

import android.content.Context
import com.bittv.iptv.util.GameContentManager
import org.json.JSONArray
import org.json.JSONObject

/** Data-driven catalogue. No executable code is downloaded with content. */
object GameCatalog {
    data class Character(val id:String,val name:String,val rarity:String,val role:String,val power:Int,val health:Int,val ability:String,val description:String)
    data class Enemy(val id:String,val name:String,val tier:String,val power:Int,val health:Int,val rewardXp:Int,val rewardCoins:Int)
    data class Skill(val id:String,val name:String,val type:String,val power:Int,val cost:Int,val description:String)
    data class Item(val id:String,val name:String,val rarity:String,val category:String,val value:Int,val description:String)
    data class Quest(val id:String,val title:String,val type:String,val target:Int,val xp:Int,val coins:Int,val description:String)
    data class Dungeon(val id:String,val name:String,val region:String,val level:Int,val energy:Int,val reward:String,val description:String)
    data class MiniGame(val id:String,val name:String,val mode:String,val reward:Int,val description:String)
    data class Bundle(val version:Int,val characters:List<Character>,val enemies:List<Enemy>,val skills:List<Skill>,val items:List<Item>,val quests:List<Quest>,val dungeons:List<Dungeon>,val miniGames:List<MiniGame>)

    @Volatile private var cache: Bundle? = null

    fun load(context: Context): Bundle {
        cache?.let { return it }
        return synchronized(this) { cache ?: parse(GameContentManager.readJson(context)).also { cache = it } }
    }

    fun reload(context: Context): Bundle { synchronized(this) { cache = parse(GameContentManager.readJson(context)); return cache!! } }

    private fun parse(raw: String): Bundle {
        val root = JSONObject(raw)
        fun arr(key:String) = root.optJSONArray(key) ?: JSONArray()
        val chars = buildList { val a=arr("characters"); for(i in 0 until a.length()){ val o=a.getJSONObject(i); add(Character(o.optString("id"),o.optString("name"),o.optString("rarity"),o.optString("role"),o.optInt("power"),o.optInt("health"),o.optString("ability"),o.optString("description"))) } }
        val enemies = buildList { val a=arr("enemies"); for(i in 0 until a.length()){ val o=a.getJSONObject(i); add(Enemy(o.optString("id"),o.optString("name"),o.optString("tier"),o.optInt("power"),o.optInt("health"),o.optInt("rewardXp"),o.optInt("rewardCoins"))) } }
        val skills = buildList { val a=arr("skills"); for(i in 0 until a.length()){ val o=a.getJSONObject(i); add(Skill(o.optString("id"),o.optString("name"),o.optString("type"),o.optInt("power"),o.optInt("cost"),o.optString("description"))) } }
        val items = buildList { val a=arr("items"); for(i in 0 until a.length()){ val o=a.getJSONObject(i); add(Item(o.optString("id"),o.optString("name"),o.optString("rarity"),o.optString("category"),o.optInt("value"),o.optString("description"))) } }
        val quests = buildList { val a=arr("quests"); for(i in 0 until a.length()){ val o=a.getJSONObject(i); add(Quest(o.optString("id"),o.optString("title"),o.optString("type"),o.optInt("target"),o.optInt("xp"),o.optInt("coins"),o.optString("description"))) } }
        val dungeons = buildList { val a=arr("dungeons"); for(i in 0 until a.length()){ val o=a.getJSONObject(i); add(Dungeon(o.optString("id"),o.optString("name"),o.optString("region"),o.optInt("level"),o.optInt("energy"),o.optString("reward"),o.optString("description"))) } }
        val games = buildList { val a=arr("miniGames"); for(i in 0 until a.length()){ val o=a.getJSONObject(i); add(MiniGame(o.optString("id"),o.optString("name"),o.optString("mode"),o.optInt("reward"),o.optString("description"))) } }
        return Bundle(root.optInt("version",1), chars, enemies, skills, items, quests, dungeons, games)
    }
}
