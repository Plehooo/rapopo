package com.bittv.iptv.game

import androidx.appcompat.app.AlertDialog
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bittv.iptv.util.UiPolish
import com.bittv.iptv.R
import com.bittv.iptv.ads.AdManager
import com.bittv.iptv.util.GameNotification
import com.bittv.iptv.util.GameProfileManager
import com.bittv.iptv.util.PointsManager
import com.bittv.iptv.util.SoundFxManager
import kotlin.random.Random

class ArcadeActivity : AppCompatActivity() {
    private lateinit var log:TextView
    private val modes=listOf("memory","tap","sequence","logic","typing","quiz","math","word","number","scramble")
    private fun catalog()=GameCatalog.load(this)
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState); buildUi()}
    private fun buildUi(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,16,16,22);setBackgroundColor(getColor(R.color.bg_root))}
        val scroll=ScrollView(this);val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};scroll.addView(c);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
        UiPolish.setupEdgeToEdge(this, root)
        UiPolish.polish(root)
        c.addView(TextView(this).apply{text="🕹️ ARCADE COLLECTION";textSize=25f;setTextColor(getColor(R.color.text_primary));setTypeface(typeface,1)})
        c.addView(TextView(this).apply{text="Game cepat yang bisa dimainkan sendiri. Reward lokal untuk progres; RAPO Coin kompetitif tetap server.";textSize=12f;setTextColor(getColor(R.color.text_secondary));setPadding(0,4,0,12)})
        modes.forEach{ id -> val d=catalog().miniGames.firstOrNull{it.id==id}; c.addView(Button(this).apply{text="${d?.name ?: id}\n${d?.description ?: "Challenge"}";isAllCaps=false;textSize=13f;gravity=Gravity.CENTER_VERTICAL;setTextColor(getColor(R.color.text_primary));setBackgroundResource(R.drawable.bg_button_game);setOnClickListener{play(id)};layoutParams=LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT).apply{bottomMargin=7}})}
        log=TextView(this).apply{text="Pilih game.";textSize=13f;setTextColor(getColor(R.color.text_primary));setPadding(14,12,14,12);setBackgroundColor(getColor(R.color.surface))};c.addView(log);AdManager.attachBanner(this,c);UiPolish.polish(root)
    }
    private fun award(points:Int,msg:String){PointsManager.addPoints(this,points);SoundFxManager.play(this, SoundFxManager.Fx.SUCCESS);log.text="$msg\n+$points poin lokal";GameNotification.show(this,"🎮 Game selesai",msg)}
    private fun input(title:String,hint:String,answer:String,reward:Int,caseInsensitive:Boolean=false){val input=EditText(this).apply{this.hint=hint;inputType=InputType.TYPE_CLASS_TEXT;isSingleLine = true};AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Keluar",null).setPositiveButton("Jawab",null).show().also{d->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{val got=input.text.toString().trim();val ok=if(caseInsensitive)got.equals(answer,true) else got==answer;if(ok){d.dismiss();award(reward,"✅ Jawaban benar.")}else{SoundFxManager.play(this, SoundFxManager.Fx.ERROR);log.text="❌ Salah. Coba mode lain."}}}}
    private fun play(id:String){when(id){
        "memory" -> { val n=Random.nextInt(1000,9999).toString(); AlertDialog.Builder(this).setTitle("🧠 Memory Grid").setMessage("Ingat angka ini:\n\n$n\n\nTekan OK lalu tulis kembali.").setPositiveButton("Mulai"){_,_->input("Memory","angka 4 digit",n,40)}.show() }
        "tap" -> { val start=System.currentTimeMillis();var taps=0;val b=Button(this).apply{text="KLIK SECEPAT MUNGKIN\n10 detik";isAllCaps=false;textSize=20f};val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,20,20,20);addView(b)};val d=AlertDialog.Builder(this).setTitle("⚡ Tap Sprint").setView(box).setNegativeButton("Tutup",null).create();b.setOnClickListener{taps++;b.text="TAP: $taps\n${10-(System.currentTimeMillis()-start)/1000}s"};d.setOnShowListener{object:CountDownTimer(10000,1000){override fun onTick(m:Long){b.text="TAP: $taps\n${(m/1000)}s"};override fun onFinish(){d.dismiss();award((taps*2).coerceAtMost(60),"⚡ $taps tap dalam 10 detik")}}.start()};d.show() }
        "sequence" -> { val seq=buildString{repeat(5){append("${Random.nextInt(1,9)} ")}}.trim();AlertDialog.Builder(this).setTitle("🔢 Sequence Master").setMessage("Hafalkan:\n$seq").setPositiveButton("Mulai"){_,_->input("Sequence","ketik tanpa spasi",seq.replace(" ",""),45)}.show() }
        "logic" -> input("🧩 Logic Sprint","angka berikutnya","32",50)
        "typing" -> { val w=listOf("nusa","bittv","gameverse","kristal","petualang").random();input("⌨️ Type Storm","ketik kata",w,48,true) }
        "quiz" -> { val qs=listOf("Planet terdekat dengan Matahari?" to "merkurius","2+3×4 = ?" to "14","Lambang kimia air?" to "h2o","Benua tempat Indonesia berada?" to "asia");val q=qs.random();input("⚡ Quiz Kilat\n${q.first}","jawaban",q.second,30,true) }
        "math" -> {val a=Random.nextInt(4,20);val b=Random.nextInt(2,10);input("➗ Math Rush\n$a × $b = ?","angka",(a*b).toString(),35)}
        "word" -> {val w=listOf("bintang","sungai","kamera","sekolah","pelangi").random();input("🔤 Tebak Kata\nPetunjuk: kata 7-8 huruf","jawaban",w,32,true)}
        "number" -> {val target=Random.nextInt(1,21);input("🎯 Tebak Angka\nHint: 1–20","angka",target.toString(),28)}
        "scramble" -> {val w=listOf("kristal","petualang","televisi","teman","quest").random();val s=w.toList().shuffled().joinToString("");input("🔀 Susun Kata\n$s","jawaban",w,30,true)}
    }}
}
