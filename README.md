# 📺 LIVE TV — BITTV-IPTV (Rapopo)

Aplikasi **IPTV native Android** (Kotlin) dengan fitur jauh lebih dari sekadar streaming — dilengkapi sistem peringatan dini bencana, pemutar musik latar, game interaktif, dan banyak lagi. Dibangun tanpa framework hybrid (Capacitor/WebView) — 100% native buat performa & stabilitas maksimal di TV box maupun HP.

![platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![language](https://img.shields.io/badge/kotlin-native-7F52FF?logo=kotlin&logoColor=white)
![min sdk](https://img.shields.io/badge/minSdk-23-blue)
![version](https://img.shields.io/badge/version-3.0.0-orange)

---

## ✨ Fitur Utama

### 📡 Live TV & Playlist
- Streaming channel dari playlist M3U (`EXTHTTP`, `KODIPROP`, `VLCOPT` didukung)
- Dukungan **DRM ClearKey** (inline `kid:key` → lisensi JSON W3C EME)
- EPG (jadwal program) real-time
- Deteksi perubahan playlist otomatis (diff channel akurat lintas proses)
- Mode **Hemat Data**: pilih bitrate 2 Mbps s/d 150 Kbps lewat custom `ThrottlingDataSource`, ngaruh juga ke stream non-adaptive

### 🚨 EWS — Peringatan Dini Bencana
- Multi-hazard: gempa & tsunami (BMKG), cuaca ekstrem (BMKG CAP), gunung api (MAGMA/PVMBG)
- Berbasis lokasi pengguna + radius per jenis bahaya
- Notifikasi sekali per event (anti-spam, dedup by signature)
- Worker background persisten + prioritas *expedited* + exemption battery-optimization, biar tetap jalan meski app ditutup

### 🎵 Musik Latar
- Cari lagu langsung dari YouTube, resolve ke MP3
- Diputar via `MediaSessionService` di background (lanjut kayak Spotify)
- Layar **Now Playing** full screen dengan seek bar & kontrol next/prev

### 👀 Viewer Presence
- Menampilkan jumlah penonton real-time per channel
- Firebase Realtime Database + Anonymous Auth, otomatis bersih saat disconnect

### 🎮 Game & Poin
- **Tebak Gambar**: tebak gambar dalam waktu terbatas
- Sistem **poin persisten** lintas fitur — didapat dari jawaban benar Tebak Gambar *dan* dari pencarian musik yang berhasil

### 🔔 Notifikasi & Update
- Notifikasi realtime (data-only FCM) untuk pengumuman
- Auto-update checker dengan validasi checksum SHA-256 + ukuran file, download ke `.part` dulu sebelum dipasang
- Overlay **Update Wajib** yang tidak bisa ditutup selain lewat tombol update

---

## 🛠️ Tech Stack

| Layer | Teknologi |
|---|---|
| Bahasa | Kotlin + native C++ (fingerprint FNV1a+mix64, fallback SHA-256) |
| Player | Media3 (ExoPlayer) |
| Background | WorkManager (playlist, EWS, update, EPG, notifikasi) |
| Realtime | Firebase Cloud Messaging + Realtime Database |
| Build | Gradle (AGP), NDK + CMake |

---

## 📂 Struktur Proyek

```
app/src/main/java/com/bittv/iptv/
├── ui/          → Activity & Adapter (MainActivity, ChannelAdapter, MusicAdapter, ...)
├── ews/         → Logika peringatan dini bencana
├── worker/      → Background job (WorkManager)
├── service/     → Foreground/media service & FCM listener
├── receiver/    → BroadcastReceiver (mis. reschedule worker setelah reboot)
├── data/        → Model & parser playlist (M3U, diff, snapshot)
├── config/      → Penyimpanan konfigurasi lokal
└── util/        → Repository & helper (Music, Playlist, EPG, Notification, Points, dll.)
```

---

## 🚀 Build Lokal

```bash
git clone https://github.com/Plehooo/rapopo.git
cd rapopo
./gradlew assembleDebug
```

APK hasil build ada di `app/build/outputs/apk/debug/`.

> Signing config release dibaca dari environment variable (GitHub Secrets), bukan hardcode — pastikan variabel yang dibutuhkan sudah di-set kalau mau build release sendiri.

## ⚙️ Konfigurasi yang Dibutuhkan

- `app/google-services.json` — kredensial Firebase (FCM, Realtime DB)
- Izin runtime yang diminta app: lokasi (untuk EWS), notifikasi (Android 13+), dan pengecualian battery optimization (khusus keandalan EWS di background)

---

## 📌 Catatan

Proyek ini terus berkembang — sebagian besar perbaikan dan fitur baru dikerjakan secara iteratif berdasarkan laporan bug/permintaan fitur langsung dari penggunaan sehari-hari, bukan roadmap formal. Lihat riwayat commit untuk detail perubahan tiap versi.
