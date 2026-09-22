package com.bittv.iptv.config

import android.content.Context

data class AppConfig(
    val appName: String,
    val producer: String,
    val version: String,
    val playlistUrl: String,
    val foregroundCheckSeconds: Long,
    val backgroundCheckMinutes: Long,
    val firstBackgroundDelaySeconds: Long,
    val maxPlaylistBytes: Long,
    val minimumChannels: Int,
    val notificationsEnabled: Boolean,
    val autoUpdateEnabled: Boolean,
    val useConditionalHttp: Boolean
)

object ConfigStore {
    // Dulu konfigurasi ini disimpan di assets/config.json.enc, dienkripsi
    // AES-GCM dengan key yang ikut ditanam di kode (lihat riwayat git kalau
    // butuh). Itu cuma obfuscation, BUKAN proteksi beneran — siapa pun yang
    // extract APK-nya (apktool/jadx) bakal nemu key-nya juga di kelas ini,
    // karena app perlu bisa mendekripsinya sendiri saat runtime. Itu batasan
    // fundamental semua "secret" yang ditanam di client, bukan soal kuat-
    // lemahnya algoritma enkripsi.
    //
    // Sekarang nilainya langsung jadi konstanta Kotlin, dikompilasi ke DEX,
    // dan ikut di-obfuscate/di-shrink oleh R8 pas build release (minifyEnabled
    // sudah aktif di app/build.gradle). Gak ada lagi file config.json.enc
    // yang keliatan jelas namanya di dalam APK dan bisa langsung didekripsi
    // manual di luar app — harus bongkar bytecode dulu buat nemu nilainya.
    // Playlist bawaan offline (dhanytv.m3u) juga sudah gak dipakai lagi;
    // app ini remote-only.
    fun load(context: Context): AppConfig = AppConfig(
        appName = "LIVE TV",
        producer = "ADITIYA",
        version = "3.1.3",
        playlistUrl = RemoteSyncConfig.PLAYLIST_URL,
        foregroundCheckSeconds = 60L,
        backgroundCheckMinutes = 15L,
        firstBackgroundDelaySeconds = 10L,
        maxPlaylistBytes = 8L * 1024L * 1024L,
        minimumChannels = 1,
        notificationsEnabled = true,
        autoUpdateEnabled = true,
        useConditionalHttp = true
    )
}
