# BITTV-IPTV / Rapopo — release build sekarang pakai minifyEnabled true +
# shrinkResources true (kode di-obfuscate & di-shrink biar APK release lebih
# susah di-reverse-engineer, sekalian ukuran lebih kecil).

# Wajib: method JNI (external fun) gak boleh di-rename/dihapus R8, soalnya
# native C++ ngebind ke nama method itu langsung. Kalau kena rename, native
# call bakal gagal (walaupun app tetap jalan karena ada fallback SHA-256 di
# NativePlaylist.safeFingerprint()).
-keepclasseswithmembernames,includedescriptorclasses class com.bittv.iptv.util.NativePlaylist {
    native <methods>;
}

# Simpan nomor baris di stack trace biar crash report tetap kebaca kalau ada
# error di Play Console / logcat, walaupun nama class/method udah di-obfuscate.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
