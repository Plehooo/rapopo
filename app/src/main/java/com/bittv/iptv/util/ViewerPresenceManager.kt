package com.bittv.iptv.util

import android.content.Context
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
 * Realtime viewer presence for the existing direct-stream player.
 *
 * One anonymous Firebase UID represents one app installation. A UID lives
 * under exactly one channel while the user is actively watching it.
 * onDisconnect().removeValue() makes the session disappear even when the
 * process crashes or the network connection is lost unexpectedly.
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
    private var rootListener: ValueEventListener? = null
    private var activeRef: DatabaseReference? = null
    private var desiredChannelId: String? = null
    private var knownChannelIds = emptyMap<String, String>()
    private var started = false

    fun setKnownChannels(channels: List<Channel>) {
        knownChannelIds = channels.associate { keyFor(it.id) to it.id }
        if (started) publishCurrentCountsFromCache()
    }

    fun start() {
        if (started) return
        started = true

        if (rootRef == null || auth == null) {
            Log.w(TAG, "Firebase viewer presence disabled: Firebase is not configured")
            return
        }

        rootListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val counts = linkedMapOf<String, Int>()
                for (channelSnapshot in snapshot.children) {
                    val rawChannelId = knownChannelIds[channelSnapshot.key] ?: continue
                    val count = channelSnapshot.childrenCount.toInt()
                    if (count > 0) counts[rawChannelId] = count
                }
                onCountsChanged(counts)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Viewer count listener cancelled", error.toException())
            }
        }
        rootRef.addValueEventListener(rootListener!!)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            activateDesiredIfPossible()
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener { activateDesiredIfPossible() }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Anonymous viewer auth failed", error)
                }
        }
    }

    /** Call with true only while the video is genuinely playing. */
    fun setWatching(channelId: String?, watching: Boolean) {
        desiredChannelId = if (watching) channelId else null
        clearActivePresence()
        if (watching) activateDesiredIfPossible()
    }

    fun stop() {
        desiredChannelId = null
        clearActivePresence()
        rootListener?.let { listener -> rootRef?.removeEventListener(listener) }
        rootListener = null
        started = false
    }

    private fun activateDesiredIfPossible() {
        val channelId = desiredChannelId ?: return
        val uid = auth?.currentUser?.uid ?: return
        val root = rootRef ?: return

        val ref = root.child(keyFor(channelId)).child(uid)
        // Queue the server-side cleanup BEFORE marking this client online.
        ref.onDisconnect().removeValue()
        ref.setValue(ServerValue.TIMESTAMP)
        activeRef = ref
    }

    private fun clearActivePresence() {
        activeRef?.let { ref ->
            runCatching { ref.onDisconnect().cancel() }
            runCatching { ref.removeValue() }
        }
        activeRef = null
    }

    private fun publishCurrentCountsFromCache() {
        // A fresh Firebase snapshot will follow immediately; this simply
        // prevents stale UI from being treated as authoritative while the
        // playlist is replaced.
        onCountsChanged(emptyMap())
    }

    private fun keyFor(channelId: String): String =
        Base64.encodeToString(
            channelId.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

    companion object {
        private const val TAG = "ViewerPresence"
    }
}
