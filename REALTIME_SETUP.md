# BITTV realtime remote update

Struktur aplikasi utama tetap sama. Yang diperkuat adalah jalur notifikasi dan sinkronisasi remote yang sudah ada:

- FCM **data-only + HIGH priority** menjadi jalur realtime untuk pengumuman yang harus tampil saat APK tidak dibuka.
- `RemoteMessagingService` langsung menampilkan notif dari payload FCM; tidak menunggu Activity atau download `notif.json`.
- WorkManager tetap menjadi fallback/recovery untuk sinkronisasi M3U dan fallback announcement.
- `adit.m3u` dan `notif.json` tetap berasal dari `Plehooo/ditz`.
- Fresh install melakukan silent baseline `notif.json` sebelum device subscribe topic.
- Startup/resume tidak lagi memanggil `FreeNotification.checkAndShow()`. Jadi membuka APK tidak memicu ulang notif lama.
- Perubahan URL pada channel yang sama tetap dianggap `changed`, bukan remove/add.
- Bila APK sedang terbuka, snapshot baru dibroadcast ke `MainActivity` dan channel aktif dapat reconnect ke URL baru tanpa restart/manual refresh.
- EWS tetap periodik, memakai ID event stabil dan silent baseline agar satu event hanya diberi satu notif.

## 1. Firebase

Pastikan Firebase Messaging aktif pada project yang sama dengan `app/google-services.json`. Android 13+ membutuhkan izin `POST_NOTIFICATIONS` dari user. Device juga harus pernah membuka aplikasi minimal sekali agar FCM registration/topic enrollment selesai.

## 2. GitHub secret pada `Plehooo/rapopo`

Buat Actions secret:

`FIREBASE_SERVICE_ACCOUNT_JSON`

Isinya full JSON service account yang boleh mengirim FCM. Jangan commit credential ke repository.

## 3. Trigger realtime dari `Plehooo/ditz`

**Ini wajib untuk realtime lintas-repo.** Repository `ditz` harus benar-benar memiliki file workflow di `.github/workflows/notify-bittv.yml`; file contoh yang berada di repo `rapopo` tidak dieksekusi oleh `ditz`.

Copy `DITZ_REALTIME_TRIGGER.yml.example` ke:

`Plehooo/ditz/.github/workflows/notify-bittv.yml`

Buat secret `RAPOPO_DISPATCH_TOKEN` di `Plehooo/ditz`. Token harus punya izin untuk membuat `repository_dispatch` pada `Plehooo/rapopo`; GitHub mendokumentasikan fine-grained token dengan permission Contents: write untuk endpoint ini.

Alurnya:

`ditz push -> repository_dispatch -> rapopo workflow -> FCM -> device`

## 4. Notifikasi remote

Edit `Plehooo/ditz/notif.json` langsung dari GitHub web lalu tekan **Commit changes**. Tidak perlu Termux.

Contoh:

```json
{
  "id": 4,
  "enabled": true,
  "title": "Update Penting",
  "message": "GTV sudah normal kembali."
}
```

Payload dikirim langsung di FCM. Device membandingkan fingerprint `id + enabled + title + message`. Nilai yang sama tidak diposting ulang.

## 5. Update M3U

Edit `Plehooo/ditz/adit.m3u`, commit dari GitHub web. Device menerima FCM dan menjalankan existing `PlaylistUpdateWorker` secara expedited bila kuota memungkinkan. Snapshot baru kemudian dibroadcast ke `MainActivity`.

Kalau GTV sedang diputar dan hanya URL stream berubah, identitas channel tetap sama sehingga player dapat reconnect ke URL baru tanpa keluar APK atau menekan refresh.

## 6. Fresh install

Urutan: `install -> open pertama -> silent baseline notif.json -> subscribe FCM` (dan playlist berjalan pada flow yang sudah ada). Isi notif yang sudah ada sebelum baseline tidak dianggap sebagai event baru. Perubahan setelah enrollment baru menghasilkan push.

## 7. EWS

EWS tetap memakai BMKG/MAGMA source yang sudah ada. Event disimpan berdasarkan ID stabil; scan pertama silent baseline. Worker periodic memakai `KEEP` agar job tidak dibatalkan/restart oleh trigger berulang.

## 8. Fallback

Jika FCM delayed/offline, `FreeNotificationWorker` tetap melakukan recovery berkala. Jalur ini bukan jalur utama dan tidak dijadwalkan 10 detik setelah membuka APK.

## 9. Validasi

Build CI memakai `android-actions/setup-android@v4` dan tidak meminta package SDK deprecated `tools`. Local Gradle build tetap perlu diverifikasi oleh GitHub Actions bila environment tidak memiliki distribution Gradle yang dibutuhkan.
