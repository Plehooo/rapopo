package com.bittv.iptv.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.bittv.iptv.data.Channel
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

/**
 * Realtime viewer presence for the direct-stream player.
 *
 * The old implementation relied almost entirely on onDisconnect().removeValue().
 * That is good for clean disconnects, but stale records can still remain in
 * RTDB after abnormal client/network situations. This version adds:
 *  - a 25s heartbeat while genuinely watching;
 *  - .info/connected awareness;
 *  - server-time offset for clock-safe freshness filtering;
 *  - a 75s freshness window when counting viewers;
 *  - idempotent setWatching() so player callbacks cannot duplicate sessions.
 *
 * Result: an old ghost eye is ignored quickly even before RTDB cleanup catches up.
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
    private val serverOffsetRef: DatabaseReference? = database?.getReference(".info/serverTimeOffset")

    private val handler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            val ref = activeRef
            if (ref != null) {
                ref.child("lastSeen").setValue(ServerValue.TIMESTAMP)
                handler.postDelayed(this, HEARTBEAT_MS)
            }
        }
    }

    private val cacheRefreshRunnable = object : Runnable {
        override fun run() {
            lastSnapshot?.let(::emitCounts)
            if (started) handler.postDelayed(this, CACHE_REFRESH_MS)
        }
    }

    private var rootListener: ValueEventListener? = null
    private var connectedListener: ValueEventListener? = null
    private var offsetListener: ValueEventListener? = null
    private var activeRef: DatabaseReference? = null
    private var desiredChannelId: String? = null
    private var knownChannelIds = emptyMap<String, String>()
    private var started = false
    private var connected = false
    private var cleanupDone = false
    @Volatile
    private var serverOffsetMs = 0L
    private var lastSnapshot: DataSnapshot? = null

    fun setKnownChannels(channels: List<Channel>) {
        knownChannelIds = channels.associate { keyFor(it.id) to it.id }
        if (started) publishCurrentCountsFromCache()
    }

    fun start() {
        if (started) return
        started = true

        if (rootRef == null || auth == null || connectedRef == null) {
            Log.w(TAG, "Firebase viewer presence disabled: Firebase is not configured")
            onCountsChanged(emptyMap())
            return
        }

        rootListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                lastSnapshot = snapshot
                emitCounts(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Viewer count listener cancelled", error.toException())
                onCountsChanged(emptyMap())
            }
        }
        rootRef.addValueEventListener(rootListener!!)
        handler.removeCallbacks(cacheRefreshRunnable)
        handler.postDelayed(cacheRefreshRunnable, CACHE_REFRESH_MS)

        offsetListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                serverOffsetMs = snapshot.getValue(Long::class.java) ?: 0L
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Firebase server-time offset listener cancelled", error.toException())
            }
        }
        serverOffsetRef?.addValueEventListener(offsetListener!!)

        connectedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                connected = snapshot.getValue(Boolean::class.java) == true
                if (connected) {
                    // Re-queue disconnect cleanup after every actual connection.
                    clearActivePresence()
                    if (cleanupDone) activateDesiredIfPossible()
                } else {
                    stopHeartbeat()
                    // Never let a disconnected client keep showing as active.
                    onCountsChanged(emptyMap())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Firebase connection listener cancelled", error.toException())
                connected = false
                onCountsChanged(emptyMap())
            }
        }
        connectedRef.addValueEventListener(connectedListener!!)

        cleanupDone = false
        val currentUser = auth.currentUser
        if (currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    cleanupOwnPresence(it.user?.uid) {
                        cleanupDone = true
                        activateDesiredIfPossible()
                    }
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Anonymous viewer auth failed", error)
                    onCountsChanged(emptyMap())
                }
        } else {
            cleanupOwnPresence(currentUser.uid) {
                cleanupDone = true
                activateDesiredIfPossible()
            }
        }
    }

    /** Call with true only while the video is genuinely playing. */
    fun setWatching(channelId: String?, watching: Boolean) {
        val nextChannelId = channelId?.takeIf { watching && it.isNotBlank() }
        if (desiredChannelId == nextChannelId && (nextChannelId == null || activeRef != null)) {
            return
        }

        desiredChannelId = nextChannelId
        clearActivePresence()
        if (desiredChannelId != null) activateDesiredIfPossible()
        else onCountsChanged(emptyMap())
    }

    fun stop() {
        desiredChannelId = null
        clearActivePresence()
        stopHeartbeat()
        rootListener?.let { listener -> rootRef?.removeEventListener(listener) }
        connectedListener?.let { listener -> connectedRef?.removeEventListener(listener) }
        offsetListener?.let { listener -> serverOffsetRef?.removeEventListener(listener) }
        handler.removeCallbacks(cacheRefreshRunnable)
        rootListener = null
        connectedListener = null
        offsetListener = null
        started = false
        connected = false
        cleanupDone = false
        onCountsChanged(emptyMap())
    }


    private fun emitCounts(snapshot: DataSnapshot) {
        val counts = linkedMapOf<String, Int>()
        val now = System.currentTimeMillis() + serverOffsetMs
        val minFreshTimestamp = now - STALE_AFTER_MS

        for (channelSnapshot in snapshot.children) {
            val rawChannelId = knownChannelIds[channelSnapshot.key] ?: continue
            var freshCount = 0
            for (viewer in channelSnapshot.children) {
                val lastSeen = viewer.child("lastSeen").getValue(Long::class.java)
                    ?: viewer.getValue(Long::class.java)
                    ?: 0L
                if (lastSeen >= minFreshTimestamp) freshCount++
            }
            if (freshCount > 0) counts[rawChannelId] = freshCount
        }
        onCountsChanged(counts)
    }

    private fun cleanupOwnPresence(uid: String?, onDone: () -> Unit) {
        if (uid.isNullOrBlank()) {
            onDone()
            return
        }
        val root = rootRef ?: run {
            onDone()
            return
        }
        root.get()
            .addOnSuccessListener { snapshot ->
                val removals = snapshot.children.mapNotNull { channel ->
                    channel.child(uid).takeIf { it.exists() }
                }
                if (removals.isEmpty()) {
                    onDone()
                    return@addOnSuccessListener
                }
                var left = removals.size
                removals.forEach { oldPresence ->
                    oldPresence.ref.removeValue().addOnCompleteListener {
                        left--
                        if (left == 0) onDone()
                    }
                }
            }
            .addOnFailureListener {
                // Cleanup is best-effort; never block playback presence if a
                // transient database read fails.
                onDone()
            }
    }

    private fun activateDesiredIfPossible() {
        if (!connected) return
        val channelId = desiredChannelId ?: return
        val uid = auth?.currentUser?.uid ?: return
        val root = rootRef ?: return

        val ref = root.child(keyFor(channelId)).child(uid)
        // Queue cleanup BEFORE setting online state.
        runCatching { ref.onDisconnect().removeValue() }
        ref.child("lastSeen").setValue(ServerValue.TIMESTAMP)
        activeRef = ref
        startHeartbeat()
    }

    private fun clearActivePresence() {
        stopHeartbeat()
        activeRef?.let { ref ->
            runCatching { ref.onDisconnect().cancel() }
            runCatching { ref.removeValue() }
        }
        activeRef = null
    }

    private fun startHeartbeat() {
        handler.removeCallbacks(heartbeatRunnable)
        handler.postDelayed(heartbeatRunnable, HEARTBEAT_MS)
    }

    private fun stopHeartbeat() {
        handler.removeCallbacks(heartbeatRunnable)
    }

    private fun publishCurrentCountsFromCache() {
        onCountsChanged(emptyMap())
    }

    private fun keyFor(channelId: String): String =
        Base64.encodeToString(
            channelId.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

    companion object {
        private const val TAG = "ViewerPresence"
        private const val HEARTBEAT_MS = 25_000L
        private const val CACHE_REFRESH_MS = 20_000L
        private const val STALE_AFTER_MS = 75_000L
    }
}
