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
    <anonymous-uid>: <server-timestamp>
```

Satu UID = satu koneksi aplikasi pada satu channel. `onDisconnect().removeValue()` dipasang sebelum session ditulis online, sehingga session dibersihkan oleh server saat koneksi putus. Firebase mendokumentasikan pola ini untuk presence. 

## Catatan

Angka yang ditampilkan adalah jumlah **aplikasi BITTV yang sedang menonton channel tersebut**, bukan jumlah koneksi langsung ke CDN/stream provider. Karena player BITTV saat ini langsung menuju URL stream, jumlah penonton CDN yang tidak memakai BITTV tidak bisa diketahui dari sisi aplikasi.
