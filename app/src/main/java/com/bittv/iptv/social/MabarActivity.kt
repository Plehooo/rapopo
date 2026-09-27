package com.bittv.iptv.social

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bittv.iptv.util.UiPolish
import com.bittv.iptv.R
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ServerValue
import com.bittv.iptv.util.CloudEconomyManager
import com.bittv.iptv.util.GameNotification
import com.bittv.iptv.util.SoundFxManager
import java.util.Locale

/** Simple realtime 2-player Tic-Tac-Toe room as the first real Mabar mode. */
class MabarActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var boardGrid: GridLayout
    private val cells = Array(9) { "-" }.toMutableList()
    private var uid = ""
    private var name = ""
    private var roomCode = ""
    private var roomRef: com.google.firebase.database.DatabaseReference? = null
    private var symbols = emptyMap<String, String>()
    private var mySymbol = ""
    private lateinit var shareButton: Button
    private lateinit var playersInfo: TextView
    private val heartbeat = Handler(Looper.getMainLooper())
    private var moveBusy = false
    private var rewardClaimed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 18, 20, 24); setBackgroundColor(getColor(R.color.bg_root)) }
        root.addView(TextView(this).apply { text = "🎮 MABAR REALTIME"; textSize = 24f; setTextColor(getColor(R.color.text_primary)); setTypeface(typeface, 1) })
        status = TextView(this).apply { text = "Login Firebase..."; textSize = 13f; setTextColor(getColor(R.color.text_secondary)) }
        root.addView(status)
        playersInfo = TextView(this).apply { textSize = 13f; setTextColor(getColor(R.color.text_primary)); setPadding(12, 10, 12, 10); setBackgroundResource(R.drawable.bg_button_game) }
        root.addView(playersInfo, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8 })
        val codeInput = EditText(this).apply { hint = "Kode room 6 karakter"; isSingleLine = true }
        root.addView(codeInput)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val create = Button(this).apply { text = "Buat Room"; isAllCaps = false }
        val join = Button(this).apply { text = "Gabung"; isAllCaps = false }
        shareButton = Button(this).apply { text = "Bagikan Room"; isAllCaps = false; visibility = android.view.View.GONE }
        buttons.addView(create, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(join, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttons)
        root.addView(shareButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        shareButton.setOnClickListener {
            if (roomCode.isNotBlank()) {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Mabar BITTV 🎮 Room: $roomCode\nGabung dari Game Hub.")
                }, "Bagikan room"))
            }
        }
        boardGrid = GridLayout(this).apply { rowCount = 3; columnCount = 3; visibility = android.view.View.GONE; alignmentMode = GridLayout.ALIGN_BOUNDS }
        root.addView(boardGrid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        UiPolish.setupEdgeToEdge(this, root)

        FirebaseIdentity.ensureAnonymous(this) { auth ->
            val user = auth?.currentUser
            if (user == null) { status.text = "Firebase Auth belum aktif."; return@ensureAnonymous }
            uid = user.uid
            name = com.bittv.iptv.util.AppProfileManager.getName(this).ifBlank { "Pemain" }
            create.setOnClickListener { createRoom() }
            join.setOnClickListener { joinRoom(codeInput.text.toString()) }
        }
        UiPolish.polish(root)
    }

    private fun createRoom() {
        roomCode = randomCode()
        val db = FirebaseIdentity.database(this) ?: return
        roomRef = db.getReference("rooms").child(roomCode)
        val data = mapOf(
            "game" to "ttt",
            "ownerUid" to uid,
            "status" to "waiting",
            "board" to "---------",
            "turn" to uid,
            "updatedAt" to ServerValue.TIMESTAMP,
            "players" to mapOf(uid to mapOf("name" to name, "ready" to true))
        )
        roomRef?.setValue(data)?.addOnCompleteListener { task ->
            status.text = if (task.isSuccessful) "Room $roomCode dibuat. Kirim kodenya ke teman." else "Gagal membuat room."
            if (task.isSuccessful) {
                rewardClaimed = false
                shareButton.visibility = android.view.View.VISIBLE
                setOnline(true)
                CloudEconomyManager.syncMabarRoom(this, roomCode) { observeRoom() }
            }
        }
    }

    private fun joinRoom(raw: String) {
        val code = raw.trim().uppercase(Locale.US)
        if (code.length < 6) { status.text = "Kode room salah."; return }
        val db = FirebaseIdentity.database(this) ?: return
        val ref = db.getReference("rooms").child(code)
        ref.get().addOnSuccessListener { snap ->
            if (!snap.exists()) { status.text = "Room tidak ditemukan."; return@addOnSuccessListener }
            val players = snap.child("players")
            if (!players.hasChild(uid) && players.childrenCount >= 2) { status.text = "Room sudah penuh."; return@addOnSuccessListener }
            roomCode = code
            roomRef = ref
            rewardClaimed = false
            ref.child("players").child(uid).setValue(mapOf("name" to name, "ready" to true)).addOnCompleteListener { task ->
                if (!task.isSuccessful) { status.text = "Gagal bergabung ke room."; return@addOnCompleteListener }
                setOnline(true)
                CloudEconomyManager.syncMabarRoom(this, code) { result ->
                    runOnUiThread { result.onFailure { status.text = "Room belum bisa dimulai: ${it.message ?: "error"}" }; observeRoom() }
                }
            }
        }
    }

    private fun observeRoom() {
        boardGrid.visibility = android.view.View.VISIBLE
        boardGrid.removeAllViews()
        repeat(9) { index ->
            val cell = Button(this).apply { text = ""; textSize = 28f; setAllCaps(false) }
            boardGrid.addView(cell, GridLayout.LayoutParams().apply {
                width = 0; height = 0; rowSpec = GridLayout.spec(index / 3, 1f); columnSpec = GridLayout.spec(index % 3, 1f)
                setMargins(4, 4, 4, 4)
            })
            cell.setOnClickListener { play(index) }
        }
        roomRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val board = snapshot.child("board").getValue(String::class.java).orEmpty().ifBlank { "---------" }
                board.forEachIndexed { i, c -> cells[i] = c.toString() }
                symbols = snapshot.child("symbols").children.associate { it.key.orEmpty() to it.getValue(String::class.java).orEmpty() }
                mySymbol = symbols[uid].orEmpty()
                renderBoard()
                val playerLines = snapshot.child("players").children.map { node ->
                    val playerName = node.child("name").getValue(String::class.java).orEmpty().ifBlank { "Pemain" }
                    val onlineAt = (node.child("onlineAt").value as? Number)?.toLong() ?: 0L
                    val online = onlineAt > 0L && System.currentTimeMillis() - onlineAt < 20_000L
                    "${if (online) "🟢" else "⚪"} $playerName"
                }
                playersInfo.text = "Pemain ${playerLines.size}/2\n${playerLines.joinToString("  ")}"
                val turn = snapshot.child("turn").getValue(String::class.java).orEmpty()
                val statusValue = snapshot.child("status").getValue(String::class.java).orEmpty()
                status.text = "Room $roomCode • Kamu $mySymbol • ${if (turn == uid) "Giliran kamu" else "Giliran teman"} • $statusValue"
                if (statusValue.startsWith("finished:") && !rewardClaimed) {
                    rewardClaimed = true
                    CloudEconomyManager.claimMabarReward(this@MabarActivity, roomCode) { reward ->
                        runOnUiThread {
                            reward.onSuccess {
                                val message = if (it.reward > 0) "+${it.reward} RAPO Coin • ${it.role}" else "Reward sudah di-claim"
                                status.text = "Room $roomCode • $message • saldo ${it.coins}"
                                if (it.reward > 0) GameNotification.show(this@MabarActivity, "🎮 Mabar selesai", message)
                            }.onFailure { /* Jangan mengganggu board kalau reward backend sedang offline. */ }
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) { status.text = "Room terputus: ${error.message}" }
        })
        // Firebase Function mengunci assignment simbol/status di server.
        CloudEconomyManager.syncMabarRoom(this, roomCode) { }
    }

    private fun play(index: Int) {
        if (moveBusy) return
        moveBusy = true
        CloudEconomyManager.submitMabarMove(this, roomCode, index) { result ->
            runOnUiThread {
                result.onSuccess { move ->
                    SoundFxManager.play(this@MabarActivity, if (move.status.startsWith("finished:")) SoundFxManager.Fx.SUCCESS else SoundFxManager.Fx.HIT)
                    status.text = "Room $roomCode • Kamu $mySymbol • ${if (move.status.startsWith("finished:")) move.status else "Move diterima"}"
                }.onFailure { SoundFxManager.play(this@MabarActivity, SoundFxManager.Fx.ERROR); status.text = "Move ditolak: ${it.message ?: "coba lagi"}" }
                moveBusy = false
            }
        }
    }

    private fun renderBoard() {
        for (i in 0 until minOf(9, boardGrid.childCount)) (boardGrid.getChildAt(i) as? Button)?.text = cells[i].takeUnless { it == "-" }.orEmpty()
    }

    private fun winner(board: String): String {
        val wins = arrayOf(intArrayOf(0,1,2), intArrayOf(3,4,5), intArrayOf(6,7,8), intArrayOf(0,3,6), intArrayOf(1,4,7), intArrayOf(2,5,8), intArrayOf(0,4,8), intArrayOf(2,4,6))
        wins.forEach { (a,b,c) -> if (board[a] != '-' && board[a] == board[b] && board[b] == board[c]) return board[a].toString() }
        return if (board.all { it != '-' }) "DRAW" else ""
    }

    private fun setOnline(online: Boolean) {
        val ref = roomRef?.child("players")?.child(uid) ?: return
        ref.updateChildren(mapOf("online" to online, "onlineAt" to ServerValue.TIMESTAMP))
        heartbeat.removeCallbacksAndMessages(null)
        if (online) heartbeat.postDelayed(object : Runnable {
            override fun run() {
                if (!isFinishing && !isDestroyed && roomRef != null) {
                    ref.updateChildren(mapOf("online" to true, "onlineAt" to ServerValue.TIMESTAMP))
                    heartbeat.postDelayed(this, 8_000L)
                }
            }
        }, 8_000L)
    }

    override fun onStart() {
        super.onStart()
        if (roomRef != null) setOnline(true)
    }

    override fun onStop() {
        roomRef?.child("players")?.child(uid)?.updateChildren(mapOf("online" to false, "onlineAt" to ServerValue.TIMESTAMP))
        heartbeat.removeCallbacksAndMessages(null)
        super.onStop()
    }

    private fun randomCode(): String = List(6) { "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".random() }.joinToString("")

    override fun onDestroy() {
        super.onDestroy()
        // Do not delete the room itself here: the other player may still be in it.
        roomRef?.child("players")?.child(uid)?.removeValue()
    }
}
