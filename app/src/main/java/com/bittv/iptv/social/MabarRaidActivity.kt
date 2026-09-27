package com.bittv.iptv.social

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bittv.iptv.util.UiPolish
import com.bittv.iptv.util.SoundFxManager
import com.bittv.iptv.R
import com.bittv.iptv.util.GameCloudManager
import com.bittv.iptv.util.GameNotification
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.util.Locale

/** 2-4 player cooperative raid. All state-changing actions are server-side. */
class MabarRaidActivity : AppCompatActivity() {
    private lateinit var status:TextView
    private lateinit var roomInfo:TextView
    private lateinit var bossInfo:TextView
    private lateinit var playersInfo:TextView
    private val heartbeat = Handler(Looper.getMainLooper())
    private lateinit var startButton:Button
    private lateinit var actionRow:LinearLayout
    private var roomCode=""
    private var roomRef:com.google.firebase.database.DatabaseReference?=null
    private var listening=false
    private var rewardClaimed=false

    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);buildUi()}
    private fun buildUi(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,16,16,22);setBackgroundColor(getColor(R.color.bg_root))}
        val scroll=ScrollView(this);val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};scroll.addView(c);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
        UiPolish.setupEdgeToEdge(this, root)
        c.addView(TextView(this).apply{text="🤝 MABAR SQUAD RAID";textSize=25f;setTextColor(getColor(R.color.text_primary));setTypeface(typeface,1)})
        c.addView(TextView(this).apply{text="2–4 pemain • room realtime • quick match • action divalidasi Firebase";textSize=12f;setTextColor(getColor(R.color.text_secondary));setPadding(0,4,0,14)})
        status=TextView(this).apply{text="Login...";textSize=13f;setTextColor(getColor(R.color.game_accent_light))};c.addView(status)
        val input=EditText(this).apply{hint="Kode room";singleLine=true;isAllCaps=true};c.addView(input)
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val create=Button(this).apply{text="Buat";isAllCaps=false};val join=Button(this).apply{text="Gabung";isAllCaps=false};val quick=Button(this).apply{text="Quick Match";isAllCaps=false}
        row.addView(create,LinearLayout.LayoutParams(0,-2,1f));row.addView(join,LinearLayout.LayoutParams(0,-2,1f));row.addView(quick,LinearLayout.LayoutParams(0,-2,1f));c.addView(row)
        roomInfo=TextView(this).apply{text="Belum ada room.";textSize=14f;setTextColor(getColor(R.color.text_primary));setPadding(14,14,14,14);setBackgroundResource(R.drawable.bg_button_game)};c.addView(roomInfo,LinearLayout.LayoutParams(-1,-2).apply{topMargin=10})
        bossInfo=TextView(this).apply{text="Boss Energy: —";textSize=18f;setTextColor(getColor(R.color.text_primary));setPadding(14,18,14,18);setBackgroundResource(R.drawable.bg_channel)};c.addView(bossInfo,LinearLayout.LayoutParams(-1,-2).apply{topMargin=10})
        playersInfo=TextView(this).apply{text="Pemain: —";textSize=13f;setTextColor(getColor(R.color.text_primary));setPadding(14,10,14,10);setBackgroundResource(R.drawable.bg_button_game)};c.addView(playersInfo,LinearLayout.LayoutParams(-1,-2).apply{topMargin=8})
        startButton=Button(this).apply{text="🚀 Mulai Raid";isAllCaps=false;visibility=View.GONE};c.addView(startButton)
        actionRow=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE};c.addView(actionRow)
        val leaderboard=Button(this).apply{text="🏆 Leaderboard Raid";isAllCaps=false;setOnClickListener{showLeaderboard()}};c.addView(leaderboard)
        val share=Button(this).apply{text="Bagikan Room";isAllCaps=false;setOnClickListener{share()}};c.addView(share)
        status.text="Siap. Buat room atau Quick Match."
        create.setOnClickListener{createRoom()};join.setOnClickListener{join(input.text.toString())};quick.setOnClickListener{quickMatch()};startButton.setOnClickListener{GameCloudManager.startRaid(this,roomCode){r->runOnUiThread{status.text=r.fold({"Raid dimulai."},{"Start gagal: ${it.message}"})}}}
    }
    private fun createRoom(){GameCloudManager.createRaidRoom(this){r->runOnUiThread{r.onSuccess{roomCode=it.roomCode;status.text="Room $roomCode dibuat. Bagikan kodenya.";attachRoom()}.onFailure{status.text="Gagal: ${it.message}"}}}}
    private fun join(raw:String){val code=raw.trim().uppercase(Locale.US);if(code.length!=8){status.text="Kode room harus 8 karakter.";return};GameCloudManager.joinRaidRoom(this,code){r->runOnUiThread{r.onSuccess{roomCode=it.roomCode;status.text="Berhasil masuk room.";attachRoom()}.onFailure{status.text="Gagal: ${it.message}"}}}}
    private fun quickMatch(){status.text="Mencari squad...";GameCloudManager.quickMatch(this){r->runOnUiThread{r.onSuccess{result->if(result.roomCode.isNotBlank()){roomCode=result.roomCode;status.text="Match ketemu • room $roomCode";attachRoom()}else status.text="Ticket matchmaking aktif. Tekan Quick Match lagi untuk cek pasangan baru."}.onFailure{status.text="Matchmaking gagal: ${it.message}"}}}}
    private fun attachRoom(){
        val db=FirebaseIdentity.database(this) ?: run {status.text="Database Firebase belum aktif.";return};roomRef=db.getReference("raidRooms").child(roomCode);if(listening)return;listening=true
        setOnline(true)
        roomRef?.addValueEventListener(object:ValueEventListener{
            override fun onDataChange(snapshot:DataSnapshot){
                val players=snapshot.child("players");val names=players.children.map{it.child("name").getValue(String::class.java).orEmpty().ifBlank{"Pemain"}};val presence=players.children.map{node -> val n=node.child("name").getValue(String::class.java).orEmpty().ifBlank{"Pemain"}; val at=(node.child("onlineAt").value as? Number)?.toLong() ?: 0L; "${if(at>0L && System.currentTimeMillis()-at<20_000L) "🟢" else "⚪"} $n"};val st=snapshot.child("status").getValue(String::class.java).orEmpty().ifBlank{"waiting"};val energy=snapshot.child("bossEnergy").getValue(Long::class.java)?.toInt() ?: 0;val round=snapshot.child("round").getValue(Long::class.java)?.toInt() ?: 0
                roomInfo.text="ROOM $roomCode\nPemain ${names.size}/4\n${names.joinToString(" • ")}\nMode: $st • Round $round"
                playersInfo.text="👥 ${presence.joinToString("   ")}"
                bossInfo.text="🔷 Boss Energy: $energy"
                startButton.visibility=if(st=="waiting" && names.size>=2)View.VISIBLE else View.GONE
                if(st=="playing")showActions() else actionRow.visibility=View.GONE
                if(st.startsWith("finished")){actionRow.visibility=View.GONE;if(!rewardClaimed){rewardClaimed=true;GameCloudManager.claimRaidReward(this@MabarRaidActivity,roomCode){reward->runOnUiThread{reward.onSuccess{if(it.reward>0)GameNotification.show(this@MabarRaidActivity,"🏆 Raid selesai","+${it.reward} RAPO Coin • ${it.role}");status.text="Raid selesai • +${it.reward} coin • ${it.role}"}}}}}
            }
            override fun onCancelled(error:DatabaseError){status.text="Realtime terputus: ${error.message}"}
        })
    }
    private fun showActions(){actionRow.visibility=View.VISIBLE;if(actionRow.childCount>0)return;val actions=listOf("FOCUS" to "⚡ Focus","GUARD" to "🛡️ Guard","SUPPORT" to "💚 Support","SCAN" to "🔎 Scan")
        actions.forEach{(id,label)->actionRow.addView(Button(this).apply{text=label;isAllCaps=false;setOnClickListener{actionRow.childrenEnable(false);GameCloudManager.actionRaid(this@MabarRaidActivity,roomCode,id){r->runOnUiThread{actionRow.childrenEnable(true);r.onSuccess{SoundFxManager.play(this@MabarRaidActivity, SoundFxManager.Fx.HIT);status.text="Aksi $id diterima • round ${it.round}"}.onFailure{SoundFxManager.play(this@MabarRaidActivity, SoundFxManager.Fx.ERROR);status.text="Aksi ditolak: ${it.message}"}}}};layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=6}})}
        UiPolish.polish(actionRow)
    }
    private fun LinearLayout.childrenEnable(enabled:Boolean){for(i in 0 until childCount)getChildAt(i).isEnabled=enabled}
    private fun showLeaderboard(){
        status.text="Memuat leaderboard..."
        GameCloudManager.getRaidLeaderboard(this){result->runOnUiThread{
            result.onSuccess { entries ->
                val body=entries.take(15).mapIndexed { i,e -> "${i+1}. ${e.displayName} • ${e.score} score • ${e.wins} win" }.joinToString("\n").ifBlank{"Belum ada data."}
                AlertDialog.Builder(this).setTitle("🏆 Raid Leaderboard").setMessage(body).setPositiveButton("Tutup",null).show()
                status.text="Leaderboard siap."
            }.onFailure{status.text="Leaderboard gagal: ${it.message}"}
        }}
    }
    private fun setOnline(online:Boolean){
        val ref=roomRef?.child("players")?.child(FirebaseIdentity.auth(this)?.currentUser?.uid.orEmpty()) ?: return
        heartbeat.removeCallbacksAndMessages(null)
        ref.updateChildren(mapOf("online" to online,"onlineAt" to com.google.firebase.database.ServerValue.TIMESTAMP))
        if(online) heartbeat.postDelayed(object:Runnable{override fun run(){if(!isFinishing&&!isDestroyed&&roomRef!=null){ref.updateChildren(mapOf("online" to true,"onlineAt" to com.google.firebase.database.ServerValue.TIMESTAMP));heartbeat.postDelayed(this,8_000L)}}},8_000L)
    }

    override fun onStart(){
        super.onStart()
        if (roomRef != null) setOnline(true)
    }

    override fun onStop(){
        roomRef?.child("players")?.child(FirebaseIdentity.auth(this)?.currentUser?.uid.orEmpty())?.updateChildren(mapOf("online" to false,"onlineAt" to com.google.firebase.database.ServerValue.TIMESTAMP))
        heartbeat.removeCallbacksAndMessages(null)
        super.onStop()
    }

    private fun share(){if(roomCode.isBlank())return;startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"🤝 Mabar BITTV Squad Raid\nRoom: $roomCode\nBuka Game Hub → Mabar Squad Raid → Gabung")},"Bagikan room"))}
}
