package com.bittv.iptv.game

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bittv.iptv.util.UiPolish
import com.bittv.iptv.R
import com.bittv.iptv.ads.AdManager
import com.bittv.iptv.market.VirtualMarketActivity
import com.bittv.iptv.social.MabarActivity
import com.bittv.iptv.social.MabarRaidActivity
import com.bittv.iptv.social.SocialHubActivity
import com.bittv.iptv.util.CloudEconomyManager
import com.bittv.iptv.util.GameContentManager
import com.bittv.iptv.util.GameProfileManager
import com.bittv.iptv.util.WatchRewardManager

class GameHubActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16,16,16,22); setBackgroundColor(getColor(R.color.bg_root)) }
        val scroll = ScrollView(this); val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content); root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
        UiPolish.setupEdgeToEdge(this, root)
        UiPolish.polish(root)
        content.addView(TextView(this).apply { text="🎮 BITTV GAMEVERSE"; textSize=29f; setTextColor(getColor(R.color.text_primary)); typeface=Typeface.DEFAULT_BOLD })
        content.addView(TextView(this).apply { text="Satu hub untuk RPG, arcade, mabar, sosial, reward, dan content pack."; textSize=12f; setTextColor(getColor(R.color.text_secondary)); setPadding(0,4,0,14) })
        val p=GameProfileManager.get(this); val c=GameCatalog.load(this); val s=GameContentManager.readStatus(this)
        content.addView(TextView(this).apply { text="Lv ${p.level} • ⚡ ${p.energy}/30 • 🧩 ${p.unlockedCharacters.size}/${c.characters.size} karakter • Content v${s.version}\n📺 TV Watch Reward: ${WatchRewardManager.getEarned(this@GameHubActivity)}/50 hari ini • +5 tiap 5 menit"; textSize=14f; setTextColor(getColor(R.color.game_accent_light)); setPadding(14,12,14,12); setBackgroundResource(R.drawable.bg_button_game) })
        val cards = listOf(
            Triple("🌌","RPG Campaign","24 karakter • skill • dungeon • quest • inventory") to { startActivity(Intent(this,DeepRpgActivity::class.java)) },
            Triple("⚔️","RPG World","Mode battle/encounter lama tetap tersedia") to { startActivity(Intent(this,RpgWorldActivity::class.java)) },
            Triple("🕹️","Arcade Collection","10+ game solo: memory, logic, tap, sequence, typing, quiz") to { startActivity(Intent(this,ArcadeActivity::class.java)) },
            Triple("🤝","Mabar Squad Raid","2–4 pemain • quick match • boss energy • reward server") to { startActivity(Intent(this,MabarRaidActivity::class.java)) },
            Triple("⭕","Mabar Tic-Tac-Toe","Duel realtime room 2 pemain") to { startActivity(Intent(this,MabarActivity::class.java)) },
            Triple("👥","Friends & Community","Cari teman • invite • transfer virtual coin") to { startActivity(Intent(this,SocialHubActivity::class.java)) },
            Triple("📈","Virtual Market","Portfolio simulasi dengan RAPO Coin") to { startActivity(Intent(this,VirtualMarketActivity::class.java)) }
        )
        cards.forEach { (data,click) -> content.addView(card(data.first,data.second,data.third,click)) }
        content.addView(Button(this).apply { text="☁️ Content Pack Manager"; isAllCaps=false; setOnClickListener { startActivity(Intent(this@GameHubActivity,DeepRpgActivity::class.java).putExtra("content_only",true)) } })
        AdManager.attachBanner(this,content)
        UiPolish.polish(root)
        CloudEconomyManager.bootstrap(this) { }
    }
    private fun card(icon:String,title:String,desc:String,click:()->Unit)=Button(this).apply { text="$icon  $title\n$desc"; isAllCaps=false; textSize=14f; setTextColor(getColor(R.color.text_primary)); setBackgroundResource(R.drawable.bg_button_game); setPadding(18,14,18,14); setOnClickListener{click()}; layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{bottomMargin=8} }
}
