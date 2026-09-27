# Real-time Viewer Count

Fitur ini memakai Firebase Realtime Database + Anonymous Authentication untuk menghitung penonton aktif per channel tanpa mengubah player/playlist architecture.

## Firebase

1. Tambahkan aplikasi Android dengan package `com.bittv.iptv` ke Firebase.
2. Download `google-services.json` lalu letakkan di `app/google-services.json`.
3. Aktifkan **Authentication -> Sign-in method -> Anonymous**.
4. Aktifkan **Realtime Database**.
5. Deploy isi `database.rules.json` sebagai Realtime Database Rules.

Struktur data yang dipakai:

```text
viewerPresence/
  <channel-key>/
    <anonymous-uid>:
    lastSeen: <server-timestamp>
```

Satu UID = satu koneksi aplikasi pada satu channel. `onDisconnect().removeValue()` tetap dipasang sebelum session ditulis online, tetapi client baru juga mengirim heartbeat 25 detik. UI hanya menghitung session dengan `lastSeen` maksimal 75 detik; jadi record lama/ghost tidak lagi membuat ikon mata muncul tanpa penonton.

Record timestamp numerik dari APK lama tetap dibaca untuk kompatibilitas selama rules lama belum dibersihkan.

## Catatan

Angka yang ditampilkan adalah jumlah **aplikasi BITTV yang sedang menonton channel tersebut**, bukan jumlah koneksi langsung ke CDN/stream provider. Karena player BITTV saat ini langsung menuju URL stream, jumlah penonton CDN yang tidak memakai BITTV tidak bisa diketahui dari sisi aplikasi.
