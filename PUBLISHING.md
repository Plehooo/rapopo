# BITTV-IPTV 4.0.0 — Publish Checklist

## Sudah terpasang
- Live TV/Media3, EPG, EWS, music, existing game bank, Point Shop, dan worker lama dipertahankan.
- Game Hub diperluas dengan RPG World, daily quest, realtime Mabar Tic-Tac-Toe, social/friend/invite, transfer RAPO Coin, dan virtual market.
- Economy sensitif diproses Cloud Functions + Firestore transaction. RAPO Coin adalah mata uang virtual di dalam aplikasi; tidak ada cash-out.
- FCM topic game + notifikasi lokal Game Hub + periodic game reminder.
- Google UMP + Mobile Ads adaptive banner/rewarded test integration.

## Firebase
1. Firebase Authentication: aktifkan Anonymous sign-in.
2. Deploy Realtime Database rules, Firestore rules, dan Functions dari folder `functions`.
3. Functions memakai Node.js 22 dan region `asia-southeast2`.
4. Untuk announcement admin, buat Firebase secret `GAME_ADMIN_KEY`. Secret sudah di-bind ke Cloud Function; jangan tanam key ke APK.

Contoh:
```bash
firebase functions:secrets:set GAME_ADMIN_KEY
cd functions
npm install
cd ..
firebase deploy --only functions,database,firestore
```

## AdMob
`AndroidManifest.xml` dan `AdManager.kt` masih memakai TEST AdMob IDs resmi. Ganti dengan App ID dan ad unit production milik publisher sebelum rilis.

## Play publish
Project saat ini menggunakan `compileSdk 36` + `targetSdk 36`. AGP sudah dinaikkan ke 8.9.1, versi minimum yang didokumentasikan Android untuk API 36, sementara Gradle wrapper tetap 8.11.1 yang memenuhi kebutuhan AGP 8.9.

## Verifikasi lokal
Jalankan `./gradlew clean assembleDebug` atau `./gradlew bundleRelease` pada mesin yang memiliki Android SDK/Build Tools dan akses Maven. Pada environment otomatis ini, Gradle wrapper tidak dapat mengunduh distribusi karena jaringan keluar diblokir, jadi build APK penuh belum dapat diverifikasi di container.
