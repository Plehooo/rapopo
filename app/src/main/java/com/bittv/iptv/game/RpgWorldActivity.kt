package com.bittv.iptv.game

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bittv.iptv.util.UiPolish
import com.bittv.iptv.R
import com.bittv.iptv.ads.AdManager
import com.bittv.iptv.social.FirebaseIdentity
import com.bittv.iptv.util.GameNotification
import com.bittv.iptv.util.GameProgressManager
import com.bittv.iptv.util.PointsManager
import com.bittv.iptv.util.SoundFxManager
import java.util.Locale
import kotlin.math.max
import kotlin.random.Random

class RpgWorldActivity : AppCompatActivity() {
    private lateinit var stats: TextView
    private lateinit var hpBar: ProgressBar
    private lateinit var log: TextView
    private lateinit var quest: TextView
    private var state = GameProgressManager.RpgState(1, 0, 100, 100, 0, 0, 0)
    private var battlesToday = 0
    private var lastQuestDay = ""
    private var busy = false

    private data class Enemy(val name: String, val level: Int, val hp: Int, val attack: Int, val xp: Int, val loot: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = GameProgressManager.get(this)
        loadDailyQuestState()
        buildUi()
        syncToFirebase()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 24)
            setBackgroundColor(getColor(R.color.bg_root))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        UiPolish.setupEdgeToEdge(this, root)
        UiPolish.polish(root)

        content.addView(TextView(this).apply {
            text = "⚔️ RPG WORLD • BITTV"
            textSize = 25f
            setTextColor(getColor(R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        content.addView(TextView(this).apply {
            text = "Jelajah • Battle • Loot • Quest • Boss"
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, 3, 0, 14)
        })

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 14)
            setBackgroundResource(R.drawable.bg_button_game)
        }
        stats = TextView(this).apply { textSize = 15f; setTextColor(getColor(R.color.text_primary)) }
        hpBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        hero.addView(stats)
        hero.addView(hpBar, LinearLayout.LayoutParams(-1, 14).apply { topMargin = 10 })
        content.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })

        content.addView(TextView(this).apply {
            text = "📜 DAILY ADVENTURE"
            textSize = 12f
            setTextColor(getColor(R.color.game_accent_light))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 6)
        })
        quest = TextView(this).apply { textSize = 13f; setTextColor(getColor(R.color.text_secondary)); setPadding(0, 0, 0, 10) }
        content.addView(quest)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        actions.addView(action("🗺️ Explore", "Temui musuh, cari XP dan loot") { explore(false) })
        actions.addView(action("🔥 Dungeon Elite", "Musuh lebih kuat, hadiah lebih besar") { explore(true) })
        actions.addView(action("👹 World Boss", "Terbuka setelah 3 battle hari ini") { fightBoss() })
        actions.addView(action("🧪 Gunakan Potion", "Pulihkan 35 HP") { heal() })
        actions.addView(action("✨ Train Skill", "Tukar 30 poin menjadi +25 XP") { train() })
        content.addView(actions)

        content.addView(TextView(this).apply {
            text = "📖 BATTLE LOG"
            textSize = 12f
            setTextColor(getColor(R.color.game_accent_light))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 16, 0, 6)
        })
        log = TextView(this).apply {
            text = "Dunia menunggu. Pilih aksi."
            textSize = 13f
            setTextColor(getColor(R.color.text_primary))
            setPadding(14, 12, 14, 12)
            setBackgroundColor(getColor(R.color.surface))
        }
        content.addView(log)
        AdManager.attachBanner(this, content)
        UiPolish.polish(root)
        render()
    }

    private fun action(title: String, subtitle: String, click: () -> Unit): View = Button(this).apply {
        text = "$title\n$subtitle"
        textSize = 14f
        gravity = Gravity.CENTER_VERTICAL
        isAllCaps = false
        setTextColor(getColor(R.color.text_primary))
        setBackgroundResource(R.drawable.bg_button_game)
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8 }
    }

    private fun explore(elite: Boolean) {
        if (busy) return
        busy = true
        val level = max(1, state.level + if (elite) 2 else Random.nextInt(0, 2))
        val pool = if (elite) listOf(
            Enemy("Void Warden", level, 140 + level * 16, 15 + level * 3, 70 + level * 8, 45 + level * 6),
            Enemy("Storm Hydra", level, 155 + level * 18, 18 + level * 3, 85 + level * 8, 55 + level * 6)
        ) else listOf(
            Enemy("Murk Slime", level, 70 + level * 10, 8 + level * 2, 28 + level * 4, 18 + level * 2),
            Enemy("Rogue Bot", level, 82 + level * 11, 10 + level * 2, 34 + level * 4, 22 + level * 2),
            Enemy("Night Crow", level, 64 + level * 9, 11 + level * 2, 32 + level * 4, 20 + level * 2)
        )
        val enemy = pool.random()
        var enemyHp = enemy.hp
        var rounds = 0
        val sb = StringBuilder("⚔️ ${enemy.name} muncul! HP ${enemy.hp}.\n")
        while (enemyHp > 0 && state.hp > 0) {
            rounds++
            val playerDamage = max(1, state.attack + Random.nextInt(-3, 7))
            enemyHp -= playerDamage
            sb.append("• Kamu -$playerDamage damage\n")
            if (enemyHp <= 0) break
            val enemyDamage = max(1, enemy.attack + Random.nextInt(-2, 4) - state.defense / 5)
            state.hp = (state.hp - enemyDamage).coerceAtLeast(0)
            sb.append("• ${enemy.name} -$enemyDamage HP\n")
            if (rounds > 40) break
        }
        if (state.hp > 0) {
            state = GameProgressManager.addXp(this, enemy.xp)
            val pointReward = max(1, enemy.loot / 4)
            PointsManager.addPoints(this, pointReward)
            battlesToday++
            if (elite) state.potions += 1
            GameProgressManager.save(this, state)
            sb.append("✅ Menang! +${enemy.xp} XP • +$pointReward poin")
            if (elite) sb.append(" • Loot Potion +1")
            log.text = sb.toString()
            GameNotification.show(this, "⚔️ Battle selesai", "+${enemy.xp} XP • +$pointReward poin")
            maybeQuestComplete()
        } else {
            state.hp = max(1, state.maxHp / 4)
            GameProgressManager.save(this, state)
            log.text = sb.append("💀 Kamu tumbang. HP dipulihkan sebagian.").toString()
        }
        busy = false
        render()
        syncToFirebase()
    }

    private fun fightBoss() {
        if (battlesToday < 3) {
            log.text = "👹 World Boss belum terbuka. Selesaikan 3 battle hari ini ($battlesToday/3)."
            return
        }
        if (busy) return
        busy = true
        val boss = Enemy("BITTV Overlord", state.level + 3, 240 + state.level * 25, 24 + state.level * 4, 160 + state.level * 12, 100)
        var hp = boss.hp
        var turn = 0
        while (hp > 0 && state.hp > 0 && turn++ < 60) {
            hp -= max(1, state.attack + Random.nextInt(4, 12))
            if (hp <= 0) break
            state.hp = (state.hp - max(1, boss.attack - state.defense / 4 + Random.nextInt(-3, 5))).coerceAtLeast(0)
        }
        if (state.hp > 0) {
            state = GameProgressManager.addXp(this, boss.xp)
            PointsManager.addPoints(this, 40)
            state.potions += 2
            GameProgressManager.save(this, state)
            log.text = "👑 WORLD BOSS KALAH! +${boss.xp} XP • +40 poin • Potion +2"
            GameNotification.show(this, "👑 World Boss ditaklukkan", "+${boss.xp} XP • +40 poin")
        } else {
            state.hp = max(1, state.maxHp / 3)
            GameProgressManager.save(this, state)
            log.text = "👹 World Boss terlalu kuat. Kamu kabur dengan sisa HP."
        }
        busy = false
        render()
        syncToFirebase()
    }

    private fun heal() {
        if (GameProgressManager.drinkPotion(this)) {
            state = GameProgressManager.get(this)
            log.text = "🧪 Potion dipakai. HP +35."
            PointsManager.addPoints(this, 1)
            render()
            syncToFirebase()
        } else {
            log.text = "Tidak bisa minum potion sekarang. Butuh potion dan HP belum penuh."
        }
    }

    private fun train() {
        if (!PointsManager.spendPoints(this, 30)) {
            log.text = "Poin kurang. Butuh 30 poin untuk latihan."
            return
        }
        state = GameProgressManager.addXp(this, 25)
        log.text = "✨ Training selesai. +25 XP."
        render()
        syncToFirebase()
    }

    private fun loadDailyQuestState() {
        val prefs = getSharedPreferences("bittv_rpg_depth", MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyyMMdd", Locale.US).format(java.util.Date())
        if (prefs.getString("day", "") != today) {
            prefs.edit().putString("day", today).putInt("battles", 0).apply()
            battlesToday = 0
        } else battlesToday = prefs.getInt("battles", 0)
        lastQuestDay = today
    }

    private fun saveDailyQuestState() {
        getSharedPreferences("bittv_rpg_depth", MODE_PRIVATE).edit()
            .putString("day", lastQuestDay)
            .putInt("battles", battlesToday)
            .apply()
    }

    private fun maybeQuestComplete() {
        saveDailyQuestState()
        if (battlesToday == 3) {
            val total = PointsManager.addPoints(this, 25)
            log.text = "🎯 DAILY DUNGEON SELESAI! +25 bonus poin • total $total"
            GameNotification.show(this, "🎯 Daily Dungeon selesai", "+25 bonus poin. World Boss terbuka!")
        }
    }

    private fun render() {
        stats.text = "Lv ${state.level}  •  ATK ${state.attack}  •  DEF ${state.defense}\nXP ${state.xp}/${state.xpToNext}  •  Potion ${state.potions}  •  Poin ${PointsManager.getTotal(this)}"
        hpBar.progress = ((state.hp.toFloat() / state.maxHp.toFloat()) * 100f).toInt().coerceIn(0, 100)
        quest.text = "Battle hari ini: $battlesToday/3 ${if (battlesToday >= 3) "• 👹 World Boss READY" else "• selesaikan 3 battle"}"
    }

    private fun syncToFirebase() {
        FirebaseIdentity.ensureAnonymous(this) { auth ->
            val user = auth?.currentUser ?: return@ensureAnonymous
            val db = FirebaseIdentity.database(this) ?: return@ensureAnonymous
            val profileName = FirebaseIdentity.cleanName(com.bittv.iptv.util.AppProfileManager.getName(this).ifBlank { "Pemain" })
            db.reference.child("users").child(user.uid).updateChildren(
                mapOf(
                    "displayName" to profileName,
                    "inviteCode" to FirebaseIdentity.inviteCode(user.uid),
                    "rpg" to mapOf(
                        "level" to state.level,
                        "xp" to state.xp,
                        "hp" to state.hp,
                        "maxHp" to state.maxHp,
                        "weaponLevel" to state.weaponLevel,
                        "armorLevel" to state.armorLevel,
                        "potions" to state.potions,
                        "updatedAt" to com.google.firebase.database.ServerValue.TIMESTAMP
                    )
                )
            )
        }
    }
}
