# BITTV / RAPOPO — UI MAXED REPAIR

Tanggal: 27 September 2026

## Fokus
Memperbaiki tampilan UI yang sebelumnya masih mencampur custom purple/blue UI dengan widget default AppCompat berwarna abu-abu, terutama dialog input dan tombol pada layar Game/Social/Market/Mabar.

## Perubahan
- Seluruh `AlertDialog` yang dipakai fitur aplikasi dialihkan ke `androidx.appcompat.app.AlertDialog` agar mengikuti tema AppCompat aplikasi.
- Tema global sekarang memakai bahasa visual purple/blue yang sama dengan kartu Game dan Settings.
- Tombol default programatik mendapat gradient `bg_button_game`, teks putih, ukuran sentuh minimal 48dp, dan tidak lagi tampil seperti tombol stok abu-abu.
- `EditText` default mendapat surface `bg_input`, teks terang, hint yang lebih terbaca, dan ukuran sentuh yang konsisten.
- Dialog dibuat gelap, rounded, lebih lebar, dengan border, tombol custom, dan dim layar yang lebih ringan supaya UI di belakang tidak terasa mati total.
- `MabarRaidActivity` sekarang benar-benar menjalankan `UiPolish.polish(root)` seperti layar game/social lainnya.
- Duplikasi penjadwalan `GameNotificationWorker` yang sama dua kali di startup `MainActivity` dihapus agar tidak membuat enqueue yang tidak perlu.
- `gradlew` dibuat executable untuk distribusi zip/Termux.

## Yang sengaja tidak diubah
Struktur package/activity/worker/service/backend, playlist/player, game data, Firebase flow, serta resource UI yang sudah custom dipertahankan. Tidak ada modul utama yang dihapus.

## Validasi
- Semua XML di `app/src/main` berhasil diparse.
- Delimiter Kotlin pada file Kotlin yang disentuh lolos pemeriksaan statis.
- Diff source terhadap input hanya menyentuh 4 import AlertDialog, 1 pemanggilan UiPolish yang hilang, 1 duplikasi worker schedule, tema global, dan 2 drawable dialog baru.
- Build APK penuh belum dapat dijalankan di environment ini karena Gradle Wrapper membutuhkan distribusi Gradle dari `services.gradle.org`, sementara akses DNS/network ke host tersebut tidak tersedia.
