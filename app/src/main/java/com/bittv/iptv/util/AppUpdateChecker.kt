package com.bittv.iptv.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.bittv.iptv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Cek versi terbaru dari sebuah file JSON kecil di GitHub (raw), lalu kalau
 * versionCode di sana lebih besar dari versionCode APK yang terpasang,
 * download APK barunya dan langsung buka installer sistem.
 *
 * Cara pakai:
 * 1. Bikin file "update.json" di repo GitHub kamu (branch main), isinya:
 *    {
 *      "versionCode": 31,
 *      "versionName": "3.0.1",
 *      "apkUrl": "https://github.com/<user>/<repo>/releases/download/v3.0.1/app-release.apk",
 *      "notes": "Perbaikan tampilan channel",
 *      "mandatory": false,
 *      "sha256": "..."
 *    }
 *    Field "sha256" opsional tapi disarankan — hash SHA-256 dari file APK
 *    rilis (`sha256sum app-release.apk`). Kalau diisi, hasil download akan
 *    dicocokkan; kalau tidak sama, APK dianggap korup dan TIDAK akan
 *    ditawarkan untuk diinstal.
 * 2. Ganti UPDATE_MANIFEST_URL di bawah sesuai repo kamu.
 * 3. Tiap kali mau rilis versi baru: naikkan versionCode di app/build.gradle,
 *    build APK, upload ke GitHub Releases, terus update update.json (versionCode,
 *    versionName, apkUrl). App akan otomatis nawarin update ke user, TANPA perlu
 *    user cari/download APK manual — cukup tap "Install" saat notifikasi muncul.
 *
 * Field "mandatory" (opsional, default false):
 *    - false / tidak ada -> update biasa, cuma notifikasi, user boleh skip.
 *    - true               -> update WAJIB. App nampilin layar penuh "Update
 *                            Wajib" yang tidak bisa ditutup/di-skip; user
 *                            cuma bisa tap "Update Sekarang". App baru bisa
 *                            dipakai lagi setelah user beneran install versi
 *                            baru (dan buka ulang app-nya).
 *
 * Catatan: Android tetap mewajibkan konfirmasi tap "Install" dari user untuk
 * app pihak ketiga (ini proteksi keamanan sistem, tidak bisa dilewati) — tapi
 * seluruh proses cek+download+buka installer berjalan otomatis.
 */
object AppUpdateChecker {

    private const val UPDATE_MANIFEST_URL =
        "https://raw.githubusercontent.com/Plehooo/ditz/refs/heads/main/update.json"

    sealed class UpdateResult {
        data class Available(
            val versionName: String,
            val apkFile: File,
            val mandatory: Boolean
        ) : UpdateResult()

        object UpToDate : UpdateResult()
        data class Failed(val error: Throwable) : UpdateResult()
    }

    suspend fun checkAndDownload(context: Context): UpdateResult =
        withContext(Dispatchers.IO) {
            try {
                val manifestJson = URL(UPDATE_MANIFEST_URL).readText()
                val manifest = JSONObject(manifestJson)

                val remoteVersionCode = manifest.getInt("versionCode")
                val apkUrl = manifest.getString("apkUrl")
                val versionName = manifest.optString("versionName", "")
                val mandatory = manifest.optBoolean("mandatory", false)
                val expectedSha256 = manifest.optString("sha256", "")
                    .trim()
                    .takeIf { it.isNotBlank() }

                val currentVersionCode = BuildConfig.VERSION_CODE

                if (remoteVersionCode <= currentVersionCode) {
                    return@withContext UpdateResult.UpToDate
                }

                val apkFile = downloadApk(context, apkUrl, expectedSha256)
                UpdateResult.Available(versionName, apkFile, mandatory)
            } catch (e: Exception) {
                UpdateResult.Failed(e)
            }
        }

    // BUG FIX: dulu APK langsung ditulis ke nama final "update.apk" tanpa
    // pengecekan apa pun — kalau koneksi putus di tengah download (jaringan
    // IPTV/hp yang koneksinya gak stabil), file setengah jadi itu tetap
    // dianggap valid dan ditawarkan ke user buat diinstal, yang gagal parse
    // sebagai APK atau lebih buruk lagi APK setengah jadi yang somehow masih
    // valid untuk fitur tertentu tapi rusak di fitur lain.
    //
    // Sekarang: (1) download ke file sementara ".part" dulu, (2) ukuran yang
    // beneran ke-download dicocokkan ke Content-Length dari server (kalau
    // server ngasih tau), (3) kalau update.json menyertakan "sha256", hash
    // hasil download dicocokkan juga. File cuma dipindah jadi "update.apk"
    // (yang dipakai buat instal) SETELAH semua pengecekan itu lolos.
    private fun downloadApk(
        context: Context,
        apkUrl: String,
        expectedSha256: String?
    ): File {
        val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        val destination = File(dir, "update.apk")
        val tempFile = File(dir, "update.apk.part")

        val connection = URL(apkUrl).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000

        try {
            connection.connect()

            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Update APK HTTP $code")
            }

            // -1 kalau server tidak mengirim Content-Length (chunked, dll) —
            // di kasus itu pengecekan ukuran dilewati, cuma pengecekan
            // "tidak kosong" dan (kalau ada) sha256 yang tetap berlaku.
            val expectedLength = connection.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            var totalRead = 0L

            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        totalRead += read
                    }
                }
            }

            if (totalRead <= 0L) {
                throw IllegalStateException("Update APK download is empty")
            }

            if (expectedLength > 0L && totalRead != expectedLength) {
                throw IllegalStateException(
                    "Update APK download incomplete ($totalRead/$expectedLength bytes) — koneksi kemungkinan terputus"
                )
            }

            if (!expectedSha256.isNullOrBlank()) {
                val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    throw IllegalStateException("Update APK checksum mismatch, kemungkinan korup")
                }
            }

            if (destination.exists()) destination.delete()
            if (!tempFile.renameTo(destination)) {
                tempFile.copyTo(destination, overwrite = true)
                tempFile.delete()
            }

            return destination
        } catch (t: Throwable) {
            // Jangan tinggalkan file setengah jadi yang bisa nyasar kepasang
            // di run berikutnya.
            tempFile.delete()
            throw t
        } finally {
            connection.disconnect()
        }
    }

    fun installIntent(context: Context, apkFile: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun canRequestInstall(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }
}
