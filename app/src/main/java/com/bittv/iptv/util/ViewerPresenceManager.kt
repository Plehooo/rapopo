package com.bittv.iptv.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.bittv.iptv.data.Channel
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

/**
 * Realtime viewer presence. In addition to onDisconnect(), nodes have a short
 * TTL and a heartbeat so reconnects/process death cannot leave a viewer stuck.
 */
class ViewerPresenceManager(
    context: Context,
    private val onCountsChanged: (Map<String, Int>) -> Unit
) {
    private val app: FirebaseApp? = runCatching {
        FirebaseApp.getApps(context.applicationContext).firstOrNull()
            ?: FirebaseApp.initializeApp(context.applicationContext)
    }.getOrNull()

    private val auth: FirebaseAuth? = app?.let { runCatching { FirebaseAuth.getInstance(it) }.getOrNull() }
    private val database: FirebaseDatabase? = app?.let { runCatching { FirebaseDatabase.getInstance(it) }.getOrNull() }

    private val rootRef: DatabaseReference? = database?.getReference("viewerPresence")
    private val connectedRef: DatabaseReference? = database?.getReference(".info/connected")
    private var rootListener: ValueEventListener? = null
    private var connectedListener: ValueEventListener? = null
    private var activeRef: DatabaseReference? = null
    private var desiredChannelId: String? = null
    private var knownChannelIds = emptyMap<String, String>()
    private var started = false
    private val handler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (!started || desiredChannelId == null || activeRef == null) return
            refreshActivePresence()
            handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    fun setKnownChannels(channels: List<Channel>) {
        knownChannelIds = channels.associate { keyFor(it.id) to it.id }
        if (started) publishCurrentCountsFromCache()
    }

    fun start() {
        if (started) return
        started = true
        database?.goOnline()

        if (rootRef == null || auth == null || connectedRef == null) {
            Log.w(TAG, "Firebase viewer presence disabled: Firebase is not configured")
            return
        }

        rootListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val now = System.currentTimeMillis()
                val counts = linkedMapOf<String, Int>()
                for (channelSnapshot in snapshot.children) {
                    val rawChannelId = knownChannelIds[channelSnapshot.key] ?: continue
                    var count = 0
                    for (viewer in channelSnapshot.children) {
                        val timestamp = viewer.getValue(Long::class.java) ?: 0L
                        if (timestamp == 0L || now - timestamp <= PRESENCE_TTL_MS) {
                            count++
                        } else {
                            // Opportunistically purge old nodes so the database
                            // itself eventually becomes clean, not just the UI.
                            runCatching { viewer.ref.removeValue() }
                        }
                    }
                    if (count > 0) counts[rawChannelId] = count
                }
                onCountsChanged(counts)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Viewer count listener cancelled", error.toException())
            }
        }
        rootRef.addValueEventListener(rootListener!!)

        connectedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) == true
                if (!connected || !started) return
                // Reconnection can leave a server-side node from an old socket.
                // Remove every node for our UID, then publish exactly one current node.
                cleanupOwnNodes { activateDesiredIfPossible() }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Firebase connection state listener cancelled", error.toException())
            }
        }
        connectedRef.addValueEventListener(connectedListener!!)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            cleanupOwnNodes { activateDesiredIfPossible() }
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener { cleanupOwnNodes { activateDesiredIfPossible() } }
                .addOnFailureListener { error -> Log.w(TAG, "Anonymous viewer auth failed", error) }
        }
    }

    /** Call with true only while the video is genuinely playing. */
    fun setWatching(channelId: String?, watching: Boolean) {
        desiredChannelId = if (watching) channelId else null
        handler.removeCallbacks(heartbeatRunnable)
        clearActivePresence()
        if (watching) cleanupOwnNodes { activateDesiredIfPossible() }
    }

    fun stop() {
        desiredChannelId = null
        handler.removeCallbacks(heartbeatRunnable)
        clearActivePresence()
        connectedListener?.let { listener -> connectedRef?.removeEventListener(listener) }
        connectedListener = null
        rootListener?.let { listener -> rootRef?.removeEventListener(listener) }
        rootListener = null
        started = false
    }

    private fun activateDesiredIfPossible() {
        if (!started) return
        val channelId = desiredChannelId ?: return
        val uid = auth?.currentUser?.uid ?: return
        val root = rootRef ?: return
        val ref = root.child(keyFor(channelId)).child(uid)
        ref.onDisconnect().removeValue()
        ref.setValue(ServerValue.TIMESTAMP)
        activeRef = ref
        handler.removeCallbacks(heartbeatRunnable)
        handler.postDelayed(heartbeatRunnable, HEARTBEAT_MS)
    }

    private fun refreshActivePresence() {
        val ref = activeRef ?: return
        runCatching {
            ref.onDisconnect().removeValue()
            ref.setValue(ServerValue.TIMESTAMP)
        }
    }

    private fun cleanupOwnNodes(after: () -> Unit) {
        val uid = auth?.currentUser?.uid
        val root = rootRef
        if (uid.isNullOrBlank() || root == null || !started) {
            after()
            return
        }
        root.get().addOnSuccessListener { snapshot ->
            val removals = snapshot.children.mapNotNull { channel ->
                channel.child(uid).ref.takeIf { it.exists() }
            }
            if (removals.isEmpty()) {
                after()
                return@addOnSuccessListener
            }
            var remaining = removals.size
            removals.forEach { ref ->
                ref.removeValue().addOnCompleteListener {
                    remaining--
                    if (remaining <= 0) after()
                }
            }
        }.addOnFailureListener { after() }
    }

    private fun clearActivePresence() {
        activeRef?.let { ref ->
            runCatching { ref.onDisconnect().cancel() }
            runCatching { ref.removeValue() }
        }
        activeRef = null
    }

    private fun publishCurrentCountsFromCache() = onCountsChanged(emptyMap())

    private fun keyFor(channelId: String): String =
        Base64.encodeToString(
            channelId.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

    companion object {
        private const val TAG = "ViewerPresence"
        private const val HEARTBEAT_MS = 20_000L
        private const val PRESENCE_TTL_MS = 75_000L
    }
}
