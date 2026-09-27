package com.bittv.iptv.util

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ServerValue
import java.util.Locale

/**
 * Lightweight Firebase Realtime Database room service for in-app Mabar.
 * Rooms are code-based, capped at four players, and support a shared raid boss
 * plus a tiny room chat. The client never depends on the TV/player pipeline.
 */
class MabarRepository(context: Context) {
    data class RoomPlayer(
        val uid: String,
        val name: String,
        val level: Int,
        val ready: Boolean
    )

    data class RoomSnapshot(
        val code: String,
        val hostUid: String,
        val status: String,
        val bossHp: Int,
        val bossMaxHp: Int,
        val players: List<RoomPlayer>,
        val messages: List<String>
    )

    private val app: FirebaseApp? = runCatching {
        FirebaseApp.getApps(context.applicationContext).firstOrNull()
            ?: FirebaseApp.initializeApp(context.applicationContext)
    }.getOrNull()
    private val auth: FirebaseAuth? = app?.let { runCatching { FirebaseAuth.getInstance(it) }.getOrNull() }
    private val db: FirebaseDatabase? = app?.let { runCatching { FirebaseDatabase.getInstance(it) }.getOrNull() }
    private val root: DatabaseReference? = db?.getReference("mabarRooms")

    private var roomListener: ValueEventListener? = null
    private var observedCode: String? = null

    fun currentUid(): String? = auth?.currentUser?.uid

    fun ensureAuth(onReady: (String?) -> Unit) {
        val authInstance = auth
        if (authInstance == null) {
            onReady(null)
            return
        }
        authInstance.currentUser?.uid?.let {
            onReady(it)
            return
        }
        authInstance.signInAnonymously()
            .addOnSuccessListener { onReady(it.user?.uid) }
            .addOnFailureListener { error ->
                Log.w(TAG, "Firebase auth untuk Mabar gagal", error)
                onReady(null)
            }
    }

    fun syncProfile(name: String, state: RpgGameStore.State) {
        ensureAuth { uid ->
            val ref = uid?.let { db?.getReference("gameProfiles")?.child(it) } ?: return@ensureAuth
            ref.updateChildren(mapOf(
                "name" to name.take(24),
                "level" to state.level,
                "xp" to state.xp,
                "updatedAt" to ServerValue.TIMESTAMP
            ))
        }
    }

    fun createRoom(name: String, state: RpgGameStore.State, callback: (String?, String?) -> Unit) {
        ensureAuth { uid ->
            val rootRef = root
            if (uid == null || rootRef == null) {
                callback(null, "Firebase belum siap")
                return@ensureAuth
            }
            val code = newRoomCode()
            val roomRef = rootRef.child(code)
            val roomData = mapOf<String, Any>(
                "hostUid" to uid,
                "status" to "lobby",
                "bossHp" to BOSS_HP,
                "bossMaxHp" to BOSS_HP,
                "createdAt" to ServerValue.TIMESTAMP
            )
            roomRef.setValue(roomData)
                .addOnSuccessListener {
                    val playerRef = roomRef.child("players").child(uid)
                    playerRef.setValue(playerMap(uid, name, state))
                        .addOnSuccessListener { callback(code, null) }
                        .addOnFailureListener { error -> callback(null, error.message ?: "Gagal masuk room") }
                }
                .addOnFailureListener { error -> callback(null, error.message ?: "Gagal membuat room") }
        }
    }

    fun joinRoom(codeInput: String, name: String, state: RpgGameStore.State, callback: (String?, String?) -> Unit) {
        ensureAuth { uid ->
            val rootRef = root
            val code = normalizeCode(codeInput)
            if (uid == null || rootRef == null) {
                callback(null, "Firebase belum siap")
                return@ensureAuth
            }
            if (code.length != 6) {
                callback(null, "Kode room harus 6 karakter")
                return@ensureAuth
            }
            val roomRef = rootRef.child(code)
            roomRef.get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.exists()) {
                        callback(null, "Room tidak ditemukan")
                        return@addOnSuccessListener
                    }
                    val players = snapshot.child("players").children.toList()
                    if (players.none { it.key == uid } && players.size >= MAX_PLAYERS) {
                        callback(null, "Room sudah penuh (maks. 4 pemain)")
                        return@addOnSuccessListener
                    }
                    roomRef.child("players").child(uid).setValue(playerMap(uid, name, state))
                        .addOnSuccessListener { callback(code, null) }
                        .addOnFailureListener { error -> callback(null, error.message ?: "Gagal bergabung") }
                }
                .addOnFailureListener { error -> callback(null, error.message ?: "Gagal membaca room") }
        }
    }

    fun observeRoom(code: String, onUpdate: (RoomSnapshot) -> Unit, onError: (String) -> Unit) {
        stopObserving()
        val ref = root?.child(normalizeCode(code))
        if (ref == null) {
            onError("Firebase belum dikonfigurasi")
            return
        }
        observedCode = normalizeCode(code)
        roomListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    onError("Room sudah ditutup")
                    return
                }
                onUpdate(parseSnapshot(observedCode.orEmpty(), snapshot))
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        }
        ref.addValueEventListener(roomListener!!)
    }

    fun setReady(code: String, ready: Boolean) {
        val uid = auth?.currentUser?.uid ?: return
        root?.child(normalizeCode(code))?.child("players")?.child(uid)?.child("ready")?.setValue(ready)
    }

    fun startRaid(code: String) {
        val uid = auth?.currentUser?.uid ?: return
        val roomRef = root?.child(normalizeCode(code)) ?: return
        roomRef.child("hostUid").get().addOnSuccessListener { host ->
            if (host.getValue(String::class.java) != uid) return@addOnSuccessListener
            roomRef.updateChildren(mapOf(
                "status" to "raid",
                "bossHp" to BOSS_HP,
                "bossMaxHp" to BOSS_HP
            ))
        }
    }

    fun attackBoss(code: String, damage: Int, onFinished: (Boolean, Int) -> Unit) {
        val roomRef = root?.child(normalizeCode(code)) ?: return
        val safeDamage = damage.coerceIn(1, 250)
        roomRef.child("bossHp").runTransaction(object : com.google.firebase.database.Transaction.Handler {
            override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                val current = currentData.getValue(Int::class.java) ?: BOSS_HP
                val next = (current - safeDamage).coerceAtLeast(0)
                currentData.value = next
                return com.google.firebase.database.Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                val hp = currentData?.getValue(Int::class.java) ?: BOSS_HP
                if (error != null || !committed) {
                    onFinished(false, hp)
                    return
                }
                val uid = auth?.currentUser?.uid.orEmpty()
                val log = root?.child(normalizeCode(code))?.child("logs")?.push()
                log?.setValue(mapOf(
                    "uid" to uid,
                    "damage" to safeDamage,
                    "at" to ServerValue.TIMESTAMP
                ))
                if (hp <= 0) {
                    root?.child(normalizeCode(code))?.child("status")?.setValue("ended")
                }
                onFinished(true, hp)
            }
        })
    }

    fun sendMessage(code: String, name: String, text: String) {
        val uid = auth?.currentUser?.uid ?: return
        val clean = text.trim().replace("\\s+".toRegex(), " ").take(160)
        if (clean.isBlank()) return
        root?.child(normalizeCode(code))?.child("messages")?.push()?.setValue(mapOf(
            "uid" to uid,
            "name" to name.take(24),
            "text" to clean,
            "at" to ServerValue.TIMESTAMP
        ))
    }

    fun leaveRoom(code: String) {
        val uid = auth?.currentUser?.uid ?: return
        val roomRef = root?.child(normalizeCode(code)) ?: return
        roomRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                stopObserving()
                return@addOnSuccessListener
            }
            val hostUid = snapshot.child("hostUid").getValue(String::class.java).orEmpty()
            val players = snapshot.child("players").children.mapNotNull { it.key }.filter { it != uid }
            if (hostUid == uid) {
                if (players.isEmpty()) {
                    // Host owns the room root, so it can safely close an empty room.
                    roomRef.removeValue()
                } else {
                    // Handover keeps the room alive when the creator leaves.
                    roomRef.child("hostUid").setValue(players.first())
                    roomRef.child("players").child(uid).removeValue()
                }
            } else {
                roomRef.child("players").child(uid).removeValue()
            }
            stopObserving()
        }
    }

    fun stopObserving() {
        val code = observedCode
        val listener = roomListener
        if (code != null && listener != null) {
            root?.child(code)?.removeEventListener(listener)
        }
        roomListener = null
        observedCode = null
    }

    private fun parseSnapshot(code: String, snapshot: DataSnapshot): RoomSnapshot {
        val players = snapshot.child("players").children.mapNotNull { child ->
            val uid = child.key ?: return@mapNotNull null
            RoomPlayer(
                uid = uid,
                name = child.child("name").getValue(String::class.java).orEmpty().ifBlank { "Player" },
                level = child.child("level").getValue(Int::class.java) ?: 1,
                ready = child.child("ready").getValue(Boolean::class.java) ?: false
            )
        }.sortedWith(compareBy<RoomPlayer> { !it.ready }.thenBy { it.name.lowercase(Locale.ROOT) })

        val messages = snapshot.child("messages").children.toList().takeLast(30).map { child ->
            val name = child.child("name").getValue(String::class.java).orEmpty().ifBlank { "Player" }
            val text = child.child("text").getValue(String::class.java).orEmpty()
            "$name: $text"
        }

        return RoomSnapshot(
            code = code,
            hostUid = snapshot.child("hostUid").getValue(String::class.java).orEmpty(),
            status = snapshot.child("status").getValue(String::class.java).orEmpty().ifBlank { "lobby" },
            bossHp = snapshot.child("bossHp").getValue(Int::class.java) ?: BOSS_HP,
            bossMaxHp = snapshot.child("bossMaxHp").getValue(Int::class.java) ?: BOSS_HP,
            players = players,
            messages = messages
        )
    }

    private fun playerMap(uid: String, name: String, state: RpgGameStore.State): Map<String, Any> = mapOf(
        "name" to name.take(24),
        "level" to state.level,
        "ready" to false,
        "joinedAt" to ServerValue.TIMESTAMP
    )

    private fun newRoomCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(6) { repeat(6) { append(alphabet.random()) } }
    }

    private fun normalizeCode(code: String): String = code.trim().uppercase(Locale.ROOT)

    companion object {
        const val BOSS_HP = 1200
        const val MAX_PLAYERS = 4
        private const val TAG = "MabarRepository"
    }
}
