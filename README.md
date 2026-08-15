# LIVE TV 3.0.0 — BITTV IPTV

Aplikasi Android **native (Kotlin)** untuk nonton siaran TV live via HLS (`.m3u8`) dan MPEG-DASH (`.mpd`), dengan penyimpanan playlist offline-first, deteksi perubahan remote otomatis, notifikasi update, EPG (jadwal siaran), favorit, dan riwayat tontonan.

> Package: `com.bittv.iptv` • Min SDK 23 • Target/Compile SDK 35 • Media3 `1.9.2`

---

## ✨ Fitur

- Player HLS & DASH berbasis **Media3/ExoPlayer**, dengan header HTTP per-channel (`User-Agent`, `Referer`, `Origin`, dll).
- **Offline-first**: playlist bawaan (`assets/dhanytv.m3u`) langsung dipakai saat pertama kali dibuka, tanpa perlu internet.
- Update playlist otomatis dari URL remote (foreground check + background lewat `WorkManager`), pakai HTTP conditional request (`ETag` / `Last-Modified`) supaya hemat kuota.
- Validasi playlist sebelum disimpan (format M3U valid, jumlah channel minimum, ukuran maksimum) — playlist rusak/kosong tidak akan menimpa cache yang valid.
- Fingerprint konten pakai native C++ (JNI) dengan fallback SHA-256 Kotlin kalau native library gagal dimuat.
- Notifikasi saat playlist berubah (butuh izin notifikasi Android 13+).
- EPG (XMLTV) dengan cache lokal, auto-refresh berkala.
- Pencarian, filter per kategori, favorit, riwayat, dan grid channel 2 kolom yang responsif (16:9 di portrait, fullscreen landscape).

## 📁 Struktur proyek yang relevan

```
app/src/main/
├── assets/
│   ├── config.json.enc      # konfigurasi terenkripsi (AES-GCM) — bukan secret store aman, hanya obfuscation
│   └── dhanytv.m3u          # playlist bawaan (offline baseline)
├── cpp/                     # native fingerprint (JNI)
├── java/com/bittv/iptv/
│   ├── config/               # baca & dekripsi config.json.enc
│   ├── data/                  # model Channel, parser M3U, diff playlist
│   ├── ui/                    # MainActivity, adapter, player, UI
│   ├── util/                  # repository playlist/EPG, notifikasi, loader logo
│   └── worker/                 # WorkManager: update playlist & EPG berkala
```

## ⚙️ Konfigurasi (`config.json.enc`)

File ini **terenkripsi** (AES/GCM) dan didekripsi saat runtime oleh `ConfigStore.kt`. Isinya metadata aplikasi & pengaturan update — **bukan** daftar channel. Contoh skema setelah didekripsi:

```json
{
  "app": {
    "name": "LIVE TV",
    "by": "ADITIYA",
    "version": "3.0.0",
    "playlistUrl": "https://raw.githubusercontent.com/<user>/<repo>/main/dhanytv.m3u",
    "playlistAsset": "dhanytv.m3u"
  },
  "update": {
    "enabled": true,
    "foregroundCheckSeconds": 60,
    "backgroundCheckMinutes": 15,
    "useConditionalHttp": true,
    "notifyOnChange": true
  },
  "cache": { "maxBytes": 8388608, "minimumChannels": 1 },
  "features": { "m3u": true, "hls": true, "dashMpd": true, "xmltvEpg": true, "notifications": true }
}
```

**Catatan keamanan:** kunci dekripsi ikut dibundel di dalam APK, jadi mekanisme ini hanya menyamarkan (obfuscation), bukan penyimpanan rahasia yang aman. Siapa pun yang membongkar APK tetap bisa mendekripsinya.

## ➕ Cara menambah / mengubah channel

Channel **tidak** diatur lewat `config.json`. Ada dua sumber:

1. **Playlist bawaan (offline)** — edit `app/src/main/assets/dhanytv.m3u` (format M3U standar, lihat contoh di bawah), lalu build ulang APK.
2. **Playlist remote (tanpa build ulang APK)** — update file M3U di URL yang tercantum pada `playlistUrl` di `config.json.enc` (mis. file `dhanytv.m3u` di repo GitHub). Aplikasi akan mendeteksi perubahan lewat pengecekan berkala dan mengunduh ulang secara otomatis.

Contoh entri channel:

```m3u
#EXTINF:-1 tvg-id="channel-baru" tvg-logo="https://example.com/logo.png" group-title="Indonesia",TV Baru
#EXTVLCOPT:http-referrer=https://example.com/
#EXTVLCOPT:http-user-agent=Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36
https://example.com/live.m3u8
```

Untuk DASH, cukup gunakan URL berakhiran `.mpd` — player otomatis mendeteksi tipe berdasarkan ekstensi.

## 🔄 Alur update playlist

1. Buka pertama kali → baca `dhanytv.m3u` bawaan dari `assets/` (langsung tampil, tanpa internet).
2. Cache lokal yang sudah pernah ter-update **selalu** dipakai lebih dulu daripada snapshot bawaan.
3. Foreground check tiap `foregroundCheckSeconds` (default 60 detik, minimal 30 detik) selama app dibuka.
4. Background check lewat `WorkManager` tiap `backgroundCheckMinutes` (default 15 menit, minimal 15 menit — batas Android).
5. Request pakai header `ETag` / `If-Modified-Since` — playlist penuh hanya diunduh kalau server bilang ada perubahan (atau validator tidak tersedia).
6. Playlist baru divalidasi (format M3U + jumlah channel minimum + ukuran maksimum) sebelum disimpan **atomically** — kalau gagal, cache lama tetap dipakai.
7. Notifikasi dikirim sekali per revisi baru (bukan saat sinkronisasi pertama kali).

## 🔨 Build

Buka folder ini di **Android Studio**, lalu `Build > Build APK(s)`, atau lewat CLI:

```bash
./gradlew :app:assembleDebug
```

APK debug ditandatangani dengan keystore debug bawaan (`app/keystore/`). Untuk build release, siapkan keystore sendiri dan sesuaikan `signingConfigs` di `app/build.gradle`.

### GitHub Actions

Workflow di `.github/workflows/build-apk.yml` otomatis build APK debug setiap push ke `main`, lalu menyimpannya sebagai artifact yang bisa diunduh dari halaman Actions.

## 🧩 Modul lain di repo ini

Repo ini juga menyertakan skeleton **Capacitor** (`android/`, `package.json`, `capacitor.settings.gradle`) dari eksperimen awal proyek. Modul ini **tidak** digunakan oleh build utama (`settings.gradle` hanya meng-include `:app`) — aman diabaikan atau dihapus kalau tidak diperlukan.
