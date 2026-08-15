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
 *      "notes": "Perbaikan tampilan channel"
 *    }
 * 2. Ganti UPDATE_MANIFEST_URL di bawah sesuai repo kamu.
 * 3. Tiap kali mau rilis versi baru: naikkan versionCode di app/build.gradle,
 *    build APK, upload ke GitHub Releases, terus update update.json (versionCode,
 *    versionName, apkUrl). App akan otomatis nawarin update ke user, TANPA perlu
 *    user cari/download APK manual — cukup tap "Install" saat notifikasi muncul.
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
            val apkFile: File
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

                val currentVersionCode = BuildConfig.VERSION_CODE

                if (remoteVersionCode <= currentVersionCode) {
                    return@withContext UpdateResult.UpToDate
                }

                val apkFile = downloadApk(context, apkUrl)
                UpdateResult.Available(versionName, apkFile)
            } catch (e: Exception) {
                UpdateResult.Failed(e)
            }
        }

    private fun downloadApk(context: Context, apkUrl: String): File {
        val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        val destination = File(dir, "update.apk")

        val connection = URL(apkUrl).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connect()

        connection.inputStream.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        connection.disconnect()

        return destination
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
