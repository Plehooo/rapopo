# BITTV realtime remote update

Arsitektur aplikasi tetap sama. Perubahan hanya memperkuat jalur yang sudah ada:

- FCM data-only menjadi trigger realtime.
- WorkManager tetap menjadi fallback/recovery.
- `adit.m3u` tetap dibaca dari sumber remote yang sudah dipakai aplikasi.
- `notif.json` tetap dibaca dari sumber remote yang sudah dipakai aplikasi.
- First install melakukan silent baseline sebelum device subscribe topic.
- Playlist diff memakai identitas channel, bukan URL stream, sehingga rotasi URL GTV/MPD/M3U8 menjadi `changed` dan player bisa reconnect tanpa restart APK.
- EWS menyimpan event ID yang sudah pernah dinotifikasi dan melakukan silent baseline pada scan pertama.

## 1. Firebase

Pastikan Firebase Messaging aktif pada Firebase project yang sama dengan `app/google-services.json`.

Android 13+ tetap membutuhkan izin `POST_NOTIFICATIONS` dari user sebelum notifikasi dapat ditampilkan.

## 2. GitHub secret

Pada repository aplikasi `Plehooo/rapopo` buat Actions secret:

`FIREBASE_SERVICE_ACCOUNT_JSON`

Nilainya adalah seluruh JSON service-account Google Cloud/Firebase. Jangan commit file service-account ke repository.

Service account harus memiliki permission untuk mengirim Firebase Cloud Messaging, termasuk `cloudmessaging.messages.create`.

## 3. Trigger realtime dari repository data

Aplikasi saat ini membaca data dari `Plehooo/ditz`. Workflow di repository aplikasi sudah menyediakan tiga jalur:

1. `repository_dispatch` untuk realtime.
2. `push` jika file data dipindahkan ke repository aplikasi.
3. schedule 5 menit sebagai fallback jika trigger realtime belum dipasang.

Untuk benar-benar realtime ketika `Plehooo/ditz` berubah, buat workflow berikut di repository `Plehooo/ditz`:

```yaml
name: Trigger BITTV realtime

on:
  push:
    branches: ["main"]
    paths:
      - "adit.m3u"
      - "notif.json"

jobs:
  dispatch:
    runs-on: ubuntu-latest
    steps:
      - name: Wake BITTV devices
        env:
          GH_TOKEN: ${{ secrets.RAPOPO_DISPATCH_TOKEN }}
        run: |
          set -euo pipefail
          gh api repos/Plehooo/rapopo/dispatches \
            -f event_type=remote-live-update \
            -f 'client_payload[kind]=sync'
```

Kemudian tambahkan Actions secret pada repository `Plehooo/ditz`:

`RAPOPO_DISPATCH_TOKEN`

Token tersebut harus boleh membuat repository dispatch pada `Plehooo/rapopo`.

## 4. Alur setelah terpasang

### Perubahan notif

Edit `notif.json`, commit, push.

`ditz` -> `repository_dispatch` -> `rapopo` -> FCM -> device -> fetch `notif.json` -> fingerprint berubah -> satu notif.

### Perubahan M3U

Edit `adit.m3u`, commit, push.

`ditz` -> `repository_dispatch` -> `rapopo` -> FCM -> device -> fetch M3U -> fingerprint berubah -> cache state/index diperbarui -> broadcast lokal -> MainActivity menerapkan playlist baru.

Jika channel aktif memiliki identitas yang sama tetapi URL/headers/DRM berubah, player melakukan reconnect ke konfigurasi baru tanpa user keluar dari APK.

## 5. Fresh install

Urutan yang diharapkan:

`install -> fetch playlist -> prime notif baseline -> subscribe FCM`

Data yang sudah ada sebelum install tidak dianggap sebagai event baru.

## 6. EWS

EWS tetap periodik karena FCM tidak menggantikan sumber hazard. Worker EWS menggunakan unique work `KEEP`; event disimpan berdasarkan ID stabil dan scan pertama melakukan baseline silent.

Jadwal Android WorkManager bersifat inexact. FCM adalah jalur realtime; WorkManager menjadi fallback ketika delivery push tertunda atau perangkat sementara offline.

## 7. Termux

Setelah file project ditimpa dengan versi ini:

```bash
cd ~/rapopo
git add -A
git commit -m "release: realtime FCM playlist EWS hardening"
git push origin main
```

Build debug:

```bash
cd ~/rapopo
./gradlew :app:assembleDebug --no-daemon
```

Build release tetap memakai signing secrets yang sudah digunakan workflow.
