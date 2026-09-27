# BITTV / RAPOPO MAXED REPAIR AUDIT

Tanggal audit: 27 September 2026
Input: `bittv-iptv-maxed-gameverse-v2.zip`
Target repo: `Plehooo/rapopo`

## Prinsip perubahan
- Struktur utama project, package, activity, worker, service, dan backend dipertahankan.
- Tidak menghapus modul game, social, market, TV, EWS, playlist, atau notification yang sudah ada.
- Perubahan difokuskan pada bug nyata, polish UI, respons sentuhan, insets Android modern, performa, reward, audio, multiplayer presence, dan ketahanan data.

## Perbaikan inti
1. `UiPolish` terpusat: edge-to-edge + system/IME/cutout insets, press animation, dan tap SFX.
2. Audio lokal `SoundFxManager` + SFX WAV ringan untuk tap/hit/success/error/level-up.
3. Reward menonton TV: +5 poin per 5 menit foreground playback, batas 50 poin/hari.
4. Kompatibilitas API: `PointsManager.getPoints()` ditambahkan sebagai alias kompatibilitas tanpa mengubah API lama.
5. Game content downloader: chaining `Task` dibetulkan dan cache JSON/asset divalidasi sebelum dipakai.
6. Virtual market: rumus quote offline disamakan dengan server dan zona waktu harian dipatok ke Asia/Jakarta.
7. Portfolio: pembacaan portofolio server ditambahkan; transaksi tetap server-authoritative.
8. Social invite: reward satu kali per target untuk request/accept dan tombol pencarian tidak lagi menumpuk.
9. Mabar: online heartbeat, indikator 🟢/⚪, anti double-move, dan anti duplicate reward claim.
10. Mabar Raid: online heartbeat dan audio feedback action.
11. Game Hub: indikator TV Watch Reward.
12. Deep RPG: arena animasi pseudo-3D/2.5D ringan berbasis Canvas tanpa engine/model berat, plus feedback audio.
13. Ads: pending banner ditahan dengan `WeakReference` dan lebar adaptive memakai ukuran layar aktual.

## Fitur yang dimaksimalkan
- Game RPG + arcade tetap ada.
- Arena visual bergerak bergaya pseudo-3D/2.5D untuk HP, bukan full 3D engine.
- Mabar menampilkan pemain dan status online real-time. Ini indikator presence, bukan streaming kamera.
- Virtual saham/investasi bersifat simulasi internal aplikasi, bukan uang nyata/cashout.
- Poin bisa didapat dari aktivitas TV dan game/invite sesuai sistem aplikasi.

## Validasi statis
- Parsing semua JSON proyek: PASS.
- Parsing XML proyek: PASS.
- `node --check functions/index.js`: PASS.
- Pemeriksaan referensi `R.id`: tidak ditemukan ID layout yang hilang.
- Nama callable Firebase dari client memiliki implementasi server yang sesuai untuk fungsi ekonomi/mabar yang diaudit.
- Tidak ditemukan diagnostik parser Kotlin seperti `expecting`, `unexpected tokens`, atau kurung tidak tertutup pada file yang diubah.

## Batas validasi build
Build APK penuh BELUM dapat divalidasi di environment audit ini karena cache Gradle dan Android SDK/`android.jar` tidak tersedia. Gradle wrapper 8.11.1 mencoba mengambil distribusi dari `services.gradle.org`, tetapi environment tidak menyediakan akses DNS/network untuk download tersebut.

Karena itu hasil ini jangan dianggap sebagai klaim bahwa APK final sudah berhasil ter-build di mesin audit. Source dan resource sudah diperiksa secara statis.

## Catatan Android modern
Project menargetkan `compileSdk 36` / `targetSdk 36`. Android 15 mewajibkan edge-to-edge untuk target 35+, dan Android 16 meniadakan opt-out untuk target 36; penanganan insets pada layar-layar programatik diperkuat di patch ini.
