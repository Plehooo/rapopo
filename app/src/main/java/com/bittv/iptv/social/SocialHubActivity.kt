package com.bittv.iptv.social

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bittv.iptv.util.UiPolish
import com.bittv.iptv.R
import com.bittv.iptv.game.RpgWorldActivity
import com.bittv.iptv.market.VirtualMarketActivity
import com.bittv.iptv.util.AppProfileManager
import com.bittv.iptv.util.CloudEconomyManager
import com.bittv.iptv.util.GameNotification
import com.bittv.iptv.util.GameProgressManager
import com.bittv.iptv.util.PointsManager
import com.bittv.iptv.util.ViewerPresenceManager
import com.bittv.iptv.util.InviteRewardManager
import com.bittv.iptv.util.NotificationBranding
import com.google.firebase.database.DatabaseReference

class SocialHubActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var wallet: TextView
    private var myUid: String = ""
    private var myCode: String = ""
    private var db: com.google.firebase.database.FirebaseDatabase? = null
    private var rootRef: DatabaseReference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        FirebaseIdentity.ensureAnonymous(this) { auth ->
            runOnUiThread {
                val user = auth?.currentUser
                if (user == null) {
                    status.text = "Firebase belum aktif. Pastikan Authentication → Anonymous dinyalakan."
                    return@runOnUiThread
                }
                myUid = user.uid
                myCode = FirebaseIdentity.inviteCode(myUid)
                db = FirebaseIdentity.database(this)
                rootRef = db?.reference
                publishProfile()
                refreshFriends()
                refreshRequests()
                CloudEconomyManager.bootstrap(this) { result ->
                    runOnUiThread { result.onSuccess { renderWallet(it.coins, it.streak) }.onFailure { status.text = "Ekonomi server belum aktif: ${it.message ?: "error"}" } }
                }
            }
        }
    }

    private fun buildUi() {
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 18, 20, 30) }
        val scroll = ScrollView(this)
        scroll.addView(content)
        setContentView(scroll)
        UiPolish.setupEdgeToEdge(this, scroll)

        val title = TextView(this).apply { text = "👥 COMMUNITY HUB"; textSize = 24f; setTextColor(getColor(R.color.text_primary)); setTypeface(typeface, android.graphics.Typeface.BOLD) }
        wallet = TextView(this).apply { textSize = 15f; setTextColor(getColor(R.color.game_accent_light)) }
        status = TextView(this).apply { text = "Menghubungkan..."; textSize = 12f; setTextColor(getColor(R.color.text_secondary)) }
        content.addView(title)
        content.addView(wallet)
        content.addView(status)

        addSection("👤 PROFIL & INVITE")
        val codeText = TextView(this).apply { textSize = 18f; setTextColor(getColor(R.color.text_primary)); setTypeface(typeface, android.graphics.Typeface.BOLD) }
        content.addView(codeText)
        val share = Button(this).apply { text = "Bagikan Kode Teman"; isAllCaps = false }
        content.addView(share)
        share.setOnClickListener {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Gabung BITTV! Kode teman gue: $myCode") }, "Bagikan kode"))
        }
        val refreshCode = Button(this).apply { text = "Profil: ${AppProfileManager.getName(this@SocialHubActivity).ifBlank { "Tamu" }}"; isAllCaps = false }
        content.addView(refreshCode)
        codeText.tag = "codeText"

        addSection("🔎 CARI TEMAN")
        val input = EditText(this).apply { hint = "Masukkan kode invite (8 karakter)"; isSingleLine = true; setTextColor(getColor(R.color.text_primary)) }
        content.addView(input)
        val findButton = Button(this).apply { text = "Cari"; isAllCaps = false }
        content.addView(findButton)
        val resultText = TextView(this).apply { textSize = 14f; setTextColor(getColor(R.color.text_secondary)) }
        content.addView(resultText)
        val searchActionContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; tag = "searchActionContainer" }
        content.addView(searchActionContainer)
        findButton.setOnClickListener { findFriend(input.text.toString(), resultText) }

        val daily = Button(this).apply { text = "🎁 Check-in Harian RAPO Coin"; isAllCaps = false }
        content.addView(daily)
        daily.setOnClickListener {
            CloudEconomyManager.claimDaily(this) { result ->
                runOnUiThread { result.onSuccess { renderWallet(it.coins, it.streak); status.text = "✅ Check-in +${it.reward} coin • streak ${it.streak}"; GameNotification.show(this, "🎁 Check-in berhasil", "+${it.reward} RAPO Coin masuk ke wallet.") }.onFailure { status.text = "Check-in gagal: ${it.message ?: "sudah diambil"}" } }
            }
        }

        addSection("💸 TRANSFER VIRTUAL COIN")
        val transferCode = EditText(this).apply { hint = "Kode invite penerima"; isSingleLine = true }
        val transferAmount = EditText(this).apply { hint = "Jumlah coin"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; isSingleLine = true }
        val transferButton = Button(this).apply { text = "Transfer"; isAllCaps = false }
        content.addView(transferCode)
        content.addView(transferAmount)
        content.addView(transferButton)
        transferButton.setOnClickListener { resolveAndTransfer(transferCode.text.toString(), transferAmount.text.toString().toLongOrNull() ?: 0L) }

        addSection("📈 PASAR & GAME")
        val marketButton = Button(this).apply { text = "📈 Buka Pasar Virtual"; isAllCaps = false }
        val gameButton = Button(this).apply { text = "🎮 Daily Quest & RPG"; isAllCaps = false }
        content.addView(marketButton)
        content.addView(gameButton)
        marketButton.setOnClickListener { startActivity(Intent(this, VirtualMarketActivity::class.java)) }
        gameButton.setOnClickListener { startActivity(Intent(this, RpgWorldActivity::class.java)) }

        addSection("🤝 TEMAN")
        val friendsText = TextView(this).apply { textSize = 14f; setTextColor(getColor(R.color.text_primary)); tag = "friendsText" }
        content.addView(friendsText)
        addSection("📨 PERMINTAAN TEMAN")
        val requestsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "requestsContainer"
        }
        val requestsText = TextView(this).apply { textSize = 14f; setTextColor(getColor(R.color.text_primary)); tag = "requestsText" }
        requestsContainer.addView(requestsText)
        content.addView(requestsContainer)

        codeText.text = "Kode kamu: -"
        refreshCode.setOnClickListener { status.text = "Nama profil diambil dari profil lokal." }
        title.setOnClickListener { codeText.text = "Kode kamu: $myCode" }
        UiPolish.polish(scroll)
    }

    private fun addSection(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(getColor(R.color.game_accent_light))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 18, 0, 7)
        })
    }

    private fun publishProfile() {
        val db = rootRef ?: return
        val name = FirebaseIdentity.cleanName(AppProfileManager.getName(this).ifBlank { "Tamu" })
        db.child("users").child(myUid).updateChildren(
            mapOf("displayName" to name, "inviteCode" to myCode, "updatedAt" to com.google.firebase.database.ServerValue.TIMESTAMP)
        )
        db.child("inviteCodes").child(myCode).setValue(myUid)
            .addOnFailureListener { status.text = "Gagal mendaftarkan kode invite" }
        (content.findViewWithTag<TextView>("codeText"))?.text = "Kode kamu: $myCode"
    }

    private fun findFriend(raw: String, resultText: TextView) {
        val code = raw.trim().uppercase()
        if (code.length < 6) { resultText.text = "Kode terlalu pendek."; return }
        rootRef?.child("inviteCodes")?.child(code)?.get()?.addOnSuccessListener { snap ->
            val uid = snap.getValue(String::class.java)
            if (uid.isNullOrBlank()) { resultText.text = "Kode tidak ditemukan."; return@addOnSuccessListener }
            if (uid == myUid) { resultText.text = "Itu kode kamu sendiri."; return@addOnSuccessListener }
            rootRef?.child("users")?.child(uid)?.get()?.addOnSuccessListener { userSnap ->
                val name = userSnap.child("displayName").getValue(String::class.java).orEmpty().ifBlank { "Pengguna BITTV" }
                resultText.text = "$name\nKode $code\nUID ${uid.take(8)}…"
                val actionBox = content.findViewWithTag<LinearLayout>("searchActionContainer")
                actionBox?.removeAllViews()
                val add = Button(this).apply { text = "Kirim Permintaan"; isAllCaps = false }
                actionBox?.addView(add, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                UiPolish.polish(actionBox ?: add)
                add.setOnClickListener {
                    val payload = mapOf("fromUid" to myUid, "fromName" to AppProfileManager.getName(this), "createdAt" to com.google.firebase.database.ServerValue.TIMESTAMP)
                    rootRef?.child("friendRequests")?.child(uid)?.child(myUid)?.setValue(payload)
                        ?.addOnCompleteListener {
                            if (it.isSuccessful) {
                                val bonus = InviteRewardManager.grantSentInvite(this, uid)
                                status.text = "✅ Permintaan terkirim.${if (bonus > 0) " +$bonus poin" else ""}"
                            } else {
                                status.text = "Gagal mengirim permintaan."
                            }
                        }
                }
            }
        }
    }

    private fun refreshRequests() {
        rootRef?.child("friendRequests")?.child(myUid)?.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val box = content.findViewWithTag<LinearLayout>("requestsContainer") ?: return
                box.removeAllViews()
                if (!snapshot.exists()) {
                    box.addView(TextView(this@SocialHubActivity).apply {
                        text = "Belum ada permintaan."
                        textSize = 14f
                        setTextColor(getColor(R.color.text_secondary))
                    })
                    return
                }
                snapshot.children.forEach { node ->
                    val fromUid = node.child("fromUid").getValue(String::class.java) ?: node.key.orEmpty()
                    val fromName = node.child("fromName").getValue(String::class.java).orEmpty().ifBlank { "Pengguna BITTV" }
                    val row = LinearLayout(this@SocialHubActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }
                    row.addView(TextView(this@SocialHubActivity).apply {
                        text = "👤 $fromName"
                        textSize = 14f
                        setTextColor(getColor(R.color.text_primary))
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(Button(this@SocialHubActivity).apply {
                        text = "Terima"
                        isAllCaps = false
                        setOnClickListener { acceptFriend(fromUid, fromName) }
                    })
                    box.addView(row)
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                status.text = "Gagal membaca permintaan teman."
            }
        })
    }

    private fun acceptFriend(friendUid: String, friendName: String) {
        if (friendUid.isBlank() || friendUid == myUid) return
        val now = com.google.firebase.database.ServerValue.TIMESTAMP
        val updates = mapOf<String, Any?>(
            "friends/$myUid/$friendUid/displayName" to FirebaseIdentity.cleanName(friendName),
            "friends/$myUid/$friendUid/addedAt" to now,
            "friends/$friendUid/$myUid/displayName" to FirebaseIdentity.cleanName(AppProfileManager.getName(this).ifBlank { "Teman BITTV" }),
            "friends/$friendUid/$myUid/addedAt" to now,
            "friendRequests/$myUid/$friendUid" to null
        )
        rootRef?.updateChildren(updates)?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val bonus = InviteRewardManager.grantAcceptedFriend(this, friendUid)
                status.text = "✅ $friendName sekarang teman kamu.${if (bonus > 0) " +$bonus poin" else ""}"
                if (bonus > 0) GameNotification.show(this, "🤝 Teman baru", "+$bonus poin referral")
            } else {
                status.text = "Gagal menerima permintaan."
            }
        }
    }

    private fun refreshFriends() {
        rootRef?.child("friends")?.child(myUid)?.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val tv = content.findViewWithTag<TextView>("friendsText") ?: return
                if (!snapshot.exists()) { tv.text = "Belum ada teman. Cari pakai kode invite."; return }
                tv.text = snapshot.children.joinToString("\n") { node -> "• ${node.child("displayName").getValue(String::class.java) ?: node.key}" }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) { }
        })
    }

    private fun resolveAndTransfer(codeRaw: String, amount: Long) {
        val code = codeRaw.trim().uppercase()
        if (code.isBlank() || amount <= 0) { status.text = "Kode dan jumlah coin harus diisi."; return }
        rootRef?.child("inviteCodes")?.child(code)?.get()?.addOnSuccessListener { snap ->
            val uid = snap.getValue(String::class.java)
            if (uid.isNullOrBlank()) { status.text = "Kode penerima tidak ditemukan."; return@addOnSuccessListener }
            CloudEconomyManager.transfer(this, uid, amount) { result ->
                runOnUiThread { result.onSuccess { wallet.text = "💰 ${it.senderCoins} RAPO Coin"; status.text = "✅ Transfer ${it.amount} coin berhasil." }.onFailure { status.text = "Transfer gagal: ${it.message ?: "error"}" } }
            }
        }
    }

    private fun renderWallet(coins: Long, streak: Int) { wallet.text = "💰 $coins RAPO Coin • 🔥 Streak $streak" }
}
