package com.bittv.iptv.social

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.security.MessageDigest

/** Shared Firebase bootstrap. Keeps the existing anonymous Firebase flow intact. */
object FirebaseIdentity {
    private const val TAG = "BITTV-Firebase"

    fun app(context: Context): FirebaseApp? = runCatching {
        FirebaseApp.getApps(context.applicationContext).firstOrNull()
            ?: FirebaseApp.initializeApp(context.applicationContext)
    }.getOrNull()

    fun auth(context: Context): FirebaseAuth? = app(context)?.let {
        runCatching { FirebaseAuth.getInstance(it) }.getOrNull()
    }

    fun database(context: Context): FirebaseDatabase? = app(context)?.let {
        runCatching { FirebaseDatabase.getInstance(it) }.getOrNull()
    }

    fun ensureAnonymous(context: Context, onReady: (FirebaseAuth?) -> Unit) {
        val auth = auth(context)
        if (auth == null) {
            Log.w(TAG, "Firebase Auth is not configured")
            onReady(null)
            return
        }
        auth.currentUser?.let {
            onReady(auth)
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { onReady(auth) }
            .addOnFailureListener {
                Log.w(TAG, "Anonymous Firebase sign-in failed", it)
                onReady(null)
            }
    }

    fun inviteCode(uid: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
        return digest.take(6).joinToString("") { "%02X".format(it) }
            .take(8)
    }

    fun cleanName(value: String): String =
        value.trim().replace(Regex("\\s+"), " ").take(24)
}
