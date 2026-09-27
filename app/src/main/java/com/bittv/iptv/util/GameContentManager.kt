package com.bittv.iptv.util

import android.content.Context
import android.util.Log
import com.bittv.iptv.social.FirebaseIdentity
import com.google.firebase.storage.FirebaseStorage
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Versioned game-content cache.
 *
 * The shipped JSON is always the offline fallback. A larger content pack can
 * be downloaded from Firebase Storage without changing the APK code. Files are
 * written to app-private storage and are never executed as code.
 */
object GameContentManager {
    private const val TAG = "BITTV-GameContent"
    const val LOCAL_ASSET = "game/game_content.json"
    const val DEFAULT_STORAGE_PATH = "game-packs/v2/game_content.json"
    private const val CACHE_DIR = "game-content"
    private const val CACHE_FILE = "game_content.json"
    private const val META_FILE = "game_content.meta"

    data class ContentStatus(val version: Int, val source: String, val bytes: Long)

    fun readJson(context: Context): String {
        val cached = File(context.filesDir, "$CACHE_DIR/$CACHE_FILE")
        if (cached.exists() && cached.length() > 64) {
            val cachedJson = runCatching { cached.readText() }.getOrNull()
            if (!cachedJson.isNullOrBlank() && runCatching { validateContentJson(JSONObject(cachedJson)) }.isSuccess) {
                return cachedJson
            }
            // Jangan biarkan cache rusak membuat GameCatalog crash saat launch.
            runCatching { cached.delete() }
        }
        return loadAsset(context)
    }

    fun readStatus(context: Context): ContentStatus {
        val dir = File(context.filesDir, CACHE_DIR)
        val file = File(dir, CACHE_FILE)
        val raw = readJson(context)
        val json = runCatching { JSONObject(raw) }.getOrNull()
        val usingCache = file.exists() && runCatching { validateContentJson(JSONObject(file.readText())) }.isSuccess
        val source = if (usingCache) "Firebase Storage cache" else "APK offline pack"
        return ContentStatus(json?.optInt("version", 1) ?: 1, source, raw.toByteArray(Charsets.UTF_8).size.toLong())
    }

    fun loadAsset(context: Context): String = context.assets.open(LOCAL_ASSET).bufferedReader().use { it.readText() }

    fun clearCache(context: Context) {
        File(context.filesDir, CACHE_DIR).deleteRecursively()
    }

    fun downloadAsset(
        context: Context,
        storagePath: String,
        localName: String,
        maxBytes: Long = 150L * 1024L * 1024L,
        onProgress: (Int) -> Unit = {},
        onDone: (Result<File>) -> Unit
    ) {
        val app = FirebaseIdentity.app(context)
        if (app == null) { onDone(Result.failure(IllegalStateException("Firebase belum tersedia"))); return }
        val targetDir = File(context.filesDir, "$CACHE_DIR/assets").apply { mkdirs() }
        val safeName = localName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
        val temp = File(targetDir, "$safeName.part")
        val target = File(targetDir, safeName)
        val task = runCatching { FirebaseStorage.getInstance(app).reference.child(storagePath).getFile(temp) }
            .getOrElse {
                onDone(Result.failure(it))
                return
            }
        task.addOnProgressListener { snap ->
            if (snap.totalByteCount > 0) {
                onProgress(((snap.bytesTransferred * 100L) / snap.totalByteCount).toInt().coerceIn(0, 100))
            }
        }.addOnSuccessListener {
            try {
                if (temp.length() > maxBytes || temp.length() == 0L) {
                    temp.delete()
                    onDone(Result.failure(IllegalStateException("Asset terlalu besar/kosong")))
                } else if (!temp.renameTo(target)) {
                    temp.delete()
                    onDone(Result.failure(IllegalStateException("Gagal memasang asset cache")))
                } else {
                    onProgress(100)
                    onDone(Result.success(target))
                }
            } catch (t: Throwable) {
                temp.delete()
                onDone(Result.failure(t))
            }
        }.addOnFailureListener {
            temp.delete()
            onDone(Result.failure(it))
        }
    }

    fun downloadJsonPack(
        context: Context,
        storagePath: String = DEFAULT_STORAGE_PATH,
        expectedSha256: String? = null,
        onProgress: (Int) -> Unit = {},
        onDone: (Result<ContentStatus>) -> Unit
    ) {
        val app = FirebaseIdentity.app(context)
        if (app == null) {
            onDone(Result.failure(IllegalStateException("Firebase belum tersedia")))
            return
        }
        val storage = runCatching { FirebaseStorage.getInstance(app) }.getOrElse {
            onDone(Result.failure(it))
            return
        }
        val root = File(context.filesDir, CACHE_DIR).apply { mkdirs() }
        val temp = File(root, "$CACHE_FILE.part")
        val target = File(root, CACHE_FILE)
        storage.reference.child(storagePath).getFile(temp)
            .addOnProgressListener { snap ->
                val total = snap.totalByteCount
                val progress = if (total > 0) ((snap.bytesTransferred * 100L) / total).toInt() else 0
                onProgress(progress.coerceIn(0, 100))
            }
            .addOnSuccessListener {
                try {
                    if (temp.length() <= 64L || temp.length() > 150L * 1024L * 1024L) throw IllegalStateException("Content pack kosong, rusak, atau terlalu besar")
                    val bytes = temp.readBytes()
                    if (!expectedSha256.isNullOrBlank()) {
                        val actual = sha256(bytes)
                        if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) {
                            throw SecurityException("Checksum content pack tidak cocok")
                        }
                    }
                    val parsed = JSONObject(bytes.toString(Charsets.UTF_8))
                    val version = validateContentJson(parsed)
                    FileOutputStream(target).use { it.write(bytes) }
                    File(root, META_FILE).writeText("$version\n${sha256(bytes)}\n")
                    temp.delete()
                    Log.i(TAG, "Installed game content v$version (${bytes.size} bytes)")
                    onProgress(100)
                    onDone(Result.success(ContentStatus(version, "Firebase Storage", bytes.size.toLong())))
                } catch (t: Throwable) {
                    temp.delete()
                    onDone(Result.failure(t))
                }
            }
            .addOnFailureListener { temp.delete(); onDone(Result.failure(it)) }
    }

    private fun validateContentJson(json: JSONObject): Int {
        val version = json.optInt("version", 0)
        if (version < 1) throw IllegalStateException("Versi content pack tidak valid")
        val required = arrayOf("characters", "enemies", "skills", "items", "quests", "dungeons", "miniGames")
        required.forEach { key ->
            val array = json.optJSONArray(key) ?: throw IllegalStateException("Field $key tidak tersedia")
            if (array.length() == 0) throw IllegalStateException("Field $key kosong")
        }
        return version
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
