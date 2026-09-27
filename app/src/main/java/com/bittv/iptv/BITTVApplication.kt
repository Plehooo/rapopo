package com.bittv.iptv

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.messaging.FirebaseMessaging
import com.bittv.iptv.util.GameNotification

/** Central app bootstrap. Firebase/App Check stays optional for offline/local builds. */
class BITTVApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching {
            val app = FirebaseApp.initializeApp(this) ?: FirebaseApp.getInstance()
            val appCheck = FirebaseAppCheck.getInstance(app)
            if (BuildConfig.DEBUG) appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            else appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
            FirebaseMessaging.getInstance().subscribeToTopic("bittv_game_events")
        }.onFailure { Log.w("BITTV-App", "Optional Firebase/App Check bootstrap unavailable", it) }
        GameNotification.ensureChannel(this)
    }
}
