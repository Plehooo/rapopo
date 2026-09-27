package com.bittv.iptv.game

import androidx.appcompat.app.AlertDialog
import android.graphics.Typeface
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
import com.bittv.iptv.util.GameContentManager
import com.bittv.iptv.util.GameNotification
import com.bittv.iptv.util.GameProfileManager
import com.bittv.iptv.util.SoundFxManager
import com.bittv.iptv.util.PointsManager
import kotlin.math.max
import kotlin.random.Random

/**
 * Deep, data-driven RPG layer. All content comes from the versioned catalogue;
 * the client only owns cosmetic/local progression, while competitive rewards
 * use the existing Firebase economy path.
 */
class DeepRpgActivity : AppCompatActivity() {
    private lateinit var stats: TextView
    private lateinit var detail: TextView
    private lateinit var xpBar: ProgressBar
    private lateinit var energyBar: ProgressBar
    private lateinit var log: TextView
    private lateinit var worldView: GameWorld3DView
    private var busy = false

    private fun profile() = GameProfileManager.get(this)
    private fun catalog() = GameCatalog.load(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GameProfileManager.resetEnergyIfNewDay(this)
        buildUi()
        render()
    }

    override fun onStart() {
        super.onStart()
        if (::worldView.isInitialized) worldView.setAnimationEnabled(true)
    }

    override fun onStop() {
        if (::worldView.isInitialized) worldView.setAnimationEnabled(false)
        super.onStop()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 22); setBackgroundColor(getColor(R.color.bg_root)) }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        UiPolish.setupEdgeToEdge(this, root)
        UiPolish.polish(root)

        content.addView(TextView(this).apply {
            text = "🌌 BITTV GAMEVERSE • RPG"
            textSize = 25f; setTextColor(getColor(R.color.text_primary)); typeface = Typeface.DEFAULT_BOLD
        })
        content.addView(TextView(this).apply {
            text = "Character • Quest • Dungeon • Collection • Content Pack"
            textSize = 12f; setTextColor(getColor(R.color.text_secondary)); setPadding(0, 4, 0, 12)
        })

        worldView = GameWorld3DView(this).apply {
            contentDescription = "3D arena RPG BITTV"
            setBackgroundResource(R.drawable.bg_button_game)
        }
        content.addView(worldView, LinearLayout.LayoutParams(-1, dp(225)).apply { bottomMargin = 12 })

        val profileCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16); setBackgroundResource(R.drawable.bg_button_game)
        }
        stats = TextView(this).apply { textSize = 16f; setTextColor(getColor(R.color.text_primary)); typeface = Typeface.DEFAULT_BOLD }
        xpBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        energyBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 30 }
        profileCard.addView(stats)
        profileCard.addView(TextView(this).apply { text = "XP"; textSize = 10f; setTextColor(getColor(R.color.text_secondary)) })
        profileCard.addView(xpBar, LinearLayout.LayoutParams(-1, 14).apply { topMargin = 4 })
        profileCard.addView(TextView(this).apply { text = "ENERGY"; textSize = 10f; setTextColor(getColor(R.color.text_secondary)); setPadding(0, 8, 0, 0) })
        profileCard.addView(energyBar, LinearLayout.LayoutParams(-1, 14).apply { topMargin = 4 })
        content.addView(profileCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })

        detail = TextView(this).apply { textSize = 13f; setTextColor(getColor(R.color.text_secondary)); setPadding(12, 12, 12, 12); setBackgroundResource(R.drawable.bg_channel) }
        content.addView(detail, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })

        val actions = listOf(
            "🧬 Character Roster" to { roster() },
            "🗺️ Explore World" to { explore() },
            "🏛️ Dungeon Ladder" to { dungeon() },
            "📜 Quest Board" to { quests() },
            "🎒 Inventory" to { inventory() },
            "✨ Skill Codex" to { skillCodex() },
            "⬆️ Upgrade Profile" to { upgradeProfile() },
            "⚡ Isi Energy dari Reward Ad" to { refillEnergy() },
            "☁️ Refresh Content Pack" to { contentPack() }
        )
        actions.forEach { (title, click) ->
            content.addView(Button(this).apply {
                text = title; isAllCaps = false; textSize = 14f; gravity = Gravity.CENTER_VERTICAL
                setTextColor(getColor(R.color.text_primary)); setBackgroundResource(R.drawable.bg_button_game)
                setOnClickListener { click() }
                layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 7 }
            })
        }

        content.addView(TextView(this).apply { text = "📓 ADVENTURE LOG"; textSize = 11f; typeface = Typeface.DEFAULT_BOLD; setTextColor(getColor(R.color.game_accent_light)); setPadding(0, 14, 0, 6) })
        log = TextView(this).apply { textSize = 13f; setTextColor(getColor(R.color.text_primary)); setPadding(14, 12, 14, 12); setBackgroundColor(getColor(R.color.surface)) }
        content.addView(log)
        AdManager.attachBanner(this, content)
        UiPolish.polish(root)
    }

    private fun render() {
        val p = profile()
        val c = catalog()
        val hero = c.characters.firstOrNull { it.id == p.selectedCharacter } ?: c.characters.first()
        stats.text = "${hero.name}  •  Lv ${p.level}  •  ${p.roleLabel()}\n⚡ ${p.energy}/30  •  💎 ${p.crystals}  •  🧩 ${p.unlockedCharacters.size}/${c.characters.size} karakter"
        xpBar.progress = ((p.xp.toFloat() / max(1, p.nextXp)) * 100f).toInt().coerceIn(0, 100)
        energyBar.progress = p.energy
        detail.text = "${hero.ability}  •  Power ${hero.power + p.level * 8}  •  HP ${hero.health}\n${hero.description}\n\nContent v${c.version} • ${c.dungeons.size} dungeon • ${c.quests.size} quest • ${c.skills.size} skill • ${c.items.size} item"
        val worldEnemyPower = (c.enemies.maxOfOrNull { it.power } ?: hero.power + 30) + (p.level * 5)
        worldView.setStats(hero.power + p.level * 8, worldEnemyPower)
    }

    private fun GameProfileManager.Profile.roleLabel(): String = "Character ${selectedCharacter.uppercase()}"

    private fun roster() {
        val c = catalog(); val p = profile()
        val lines = c.characters.map { ch ->
            val owned = p.unlockedCharacters.contains(ch.id); if (owned) "✅ ${ch.name} • ${ch.rarity} • ${ch.role} • PWR ${ch.power}" else "🔒 ${ch.name} • ${ch.rarity} • unlock di event/quest"
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("🧬 Character Roster").setItems(lines) { _, which ->
            val ch = c.characters[which]
            if (p.unlockedCharacters.contains(ch.id)) { GameProfileManager.setCharacter(this, ch.id); log.text = "Karakter aktif: ${ch.name}"; render() }
        }.setNegativeButton("Tutup", null).show()
    }

    private fun explore() {
        if (busy || !GameProfileManager.consumeEnergy(this)) { log.text = "Energy habis. Reset harian atau tonton reward ad untuk isi."; return }
        busy = true
        val c = catalog(); val enemy = c.enemies.random(); val p = profile(); val hero = c.characters.firstOrNull { it.id == p.selectedCharacter } ?: c.characters.first()
        val advantage = (hero.power + p.level * 8) - enemy.power
        val win = advantage >= -35 || Random.nextInt(100) < 58
        val xp = if (win) enemy.rewardXp + p.level * 5 else max(10, enemy.rewardXp / 3)
        GameProfileManager.addXp(this, xp)
        val item = c.items.random(); GameProfileManager.addItem(this, item.id, if (win) 1 else 0)
        SoundFxManager.play(this, if (win) SoundFxManager.Fx.SUCCESS else SoundFxManager.Fx.ERROR)
        if (win && Random.nextInt(100) < 22) {
            val ch = c.characters.filter { it.rarity != "Common" && !profile().unlockedCharacters.contains(it.id) }.randomOrNull()
            if (ch != null) { GameProfileManager.unlock(this, ch.id); log.text = "⭐ DISCOVERY! ${ch.name} terbuka.\nEncounter: ${enemy.name}\n+${xp} XP • +1 ${item.name}" }
            else log.text = "✅ ${enemy.name} selesai.\n+${xp} XP • +1 ${item.name}"
        } else log.text = if (win) "✅ Encounter ${enemy.name} selesai.\n+${xp} XP • +1 ${item.name}" else "↩️ ${enemy.name} terlalu berat. Kamu mundur aman.\n+${xp} XP"
        render(); GameNotification.show(this, if (win) "🌟 RPG Reward" else "🧭 RPG Explore", log.text.toString())
        busy = false
    }

    private fun dungeon() {
        val p = profile(); val available = catalog().dungeons.filter { it.level <= p.level + 4 }
        val options = available.map { "${it.name}\nLv ${it.level} • Energy ${it.energy} • ${it.reward}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("🏛️ Dungeon Ladder").setItems(options) { _, index ->
            val d = available[index]
            if (!GameProfileManager.consumeEnergy(this, d.energy)) { log.text = "Energy tidak cukup untuk ${d.name}."; return@setItems }
            val reward = catalog().items.firstOrNull { it.name == d.reward } ?: catalog().items.first()
            GameProfileManager.addXp(this, 70 + d.level * 12); GameProfileManager.addItem(this, reward.id)
            SoundFxManager.play(this, SoundFxManager.Fx.HIT)
            log.text = "🏛️ ${d.name} cleared.\n${d.description}\n+XP • +${reward.name}"
            render(); GameNotification.show(this, "🏛️ Dungeon Clear", "${d.name} selesai.")
        }.setNegativeButton("Tutup", null).show()
    }

    private fun quests() {
        val p = profile(); val available = catalog().quests.filter { !p.completedQuests.contains(it.id) }.take(12)
        val lines = available.map { "${it.title}\nTarget ${it.target} • +${it.xp} XP • +${it.coins} coin virtual" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("📜 Quest Board").setItems(lines) { _, which ->
            val q = available[which]
            if (!GameProfileManager.completeQuest(this, q.id)) return@setItems
            GameProfileManager.addXp(this, q.xp)
            PointsManager.addPoints(this, q.coins.coerceAtMost(100))
            SoundFxManager.play(this, SoundFxManager.Fx.LEVEL_UP)
            log.text = "🏁 ${q.title} selesai.\n${q.description}\n+${q.xp} XP • +${q.coins} virtual reward"
            render(); GameNotification.show(this, "🏁 Quest Complete", q.title)
        }.setNegativeButton("Tutup", null).show()
    }

    private fun inventory() {
        val p = profile(); val c = catalog(); val text = p.inventory.entries.sortedByDescending { it.value }.joinToString("\n") { entry ->
            val item = c.items.firstOrNull { it.id == entry.key }; "• ${item?.name ?: entry.key} ×${entry.value} • ${item?.rarity ?: "Unknown"}"
        }.ifBlank { "Inventory masih kosong. Explore atau clear dungeon." }
        AlertDialog.Builder(this).setTitle("🎒 Inventory").setMessage(text).setPositiveButton("Tutup", null).show()
    }

    private fun skillCodex() {
        val text = catalog().skills.take(20).joinToString("\n\n") { "${it.name} • ${it.type}\nPower ${it.power} • Cost ${it.cost}\n${it.description}" }
        AlertDialog.Builder(this).setTitle("✨ Skill Codex").setMessage(text).setPositiveButton("Tutup", null).show()
    }

    private fun upgradeProfile() {
        val cost = 60 + profile().level * 20
        val has = PointsManager.getPoints(this) >= cost
        AlertDialog.Builder(this).setTitle("⬆️ Profile Upgrade").setMessage("Naikkan progression dengan ${cost} poin lokal.\nStatus: ${if (has) "siap" else "poin kurang"}.")
            .setNegativeButton("Batal", null).setPositiveButton("Upgrade") { _, _ ->
                if (PointsManager.spendPoints(this, cost)) { GameProfileManager.addXp(this, 90); GameProfileManager.addCrystals(this, 2); log.text = "⬆️ Upgrade berhasil. +XP +2 crystal."; SoundFxManager.play(this, SoundFxManager.Fx.LEVEL_UP); render() }
                else log.text = "Poin kurang." 
            }.show()
    }

    private fun refillEnergy() {
        AdManager.showRewarded(this, { reward ->
            GameProfileManager.addEnergy(this, 8)
            log.text = "⚡ +${reward.amount} reward • +8 Energy"
            render()
        }) { render() }
    }

    private fun contentPack() {
        val status = GameContentManager.readStatus(this)
        log.text = "Content saat ini v${status.version} • ${status.source} • ${status.bytes} bytes\nDownload pack dari Firebase Storage path:\n${GameContentManager.DEFAULT_STORAGE_PATH}"
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        AlertDialog.Builder(this).setTitle("☁️ Download Content Pack v2").setMessage("Pack ini hanya JSON data, bukan executable code.\n\nPath: ${GameContentManager.DEFAULT_STORAGE_PATH}")
            .setView(progress).setNegativeButton("Tutup", null).setPositiveButton("Download", null).show().also { dialog ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    progress.visibility = View.VISIBLE
                    GameContentManager.downloadJsonPack(this, onProgress = { p -> runOnUiThread { progress.progress = p } }) { result ->
                        runOnUiThread { result.onSuccess { GameCatalog.reload(this); log.text = "✅ Content pack v${it.version} aktif."; render(); dialog.dismiss() }.onFailure { log.text = "❌ Download pack gagal: ${it.message}" } }
                    }
                }
            }
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
