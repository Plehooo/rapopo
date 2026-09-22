package com.bittv.iptv.util

import android.content.Context
import java.io.File

/**
 * Keeps the latest validated M3U in app-private storage so a background sync
 * can hand a fresh playlist to an already-open Activity without a second
 * network round-trip. The remote URL remains the source of truth.
 */
object PlaylistCacheStore {
    private const val DIR_NAME = "playlist_cache"
    private const val FILE_NAME = "latest.m3u"
    private const val META = "playlist_cache_meta"
    private const val KEY_CACHED_AT = "cached_at"
    private const val KEY_FINGERPRINT = "fingerprint"

    private fun file(context: Context): File =
        File(File(context.filesDir, DIR_NAME), FILE_NAME)

    fun write(context: Context, content: String, fingerprint: String) {
        if (content.isBlank()) return
        val target = file(context)
        target.parentFile?.mkdirs()
        val tmp = File(
            target.parentFile,
            "${target.name}.${Thread.currentThread().id}.${System.nanoTime()}.part"
        )
        tmp.writeText(content, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        context.getSharedPreferences(META, Context.MODE_PRIVATE).edit()
            .putLong(KEY_CACHED_AT, System.currentTimeMillis())
            .putString(KEY_FINGERPRINT, fingerprint)
            .apply()
    }

    fun read(context: Context): String? = runCatching {
        file(context).takeIf { it.isFile && it.length() > 0L }?.readText(Charsets.UTF_8)
    }.getOrNull()

    fun fingerprint(context: Context): String? =
        context.getSharedPreferences(META, Context.MODE_PRIVATE).getString(KEY_FINGERPRINT, null)

    fun cachedAt(context: Context): Long =
        context.getSharedPreferences(META, Context.MODE_PRIVATE).getLong(KEY_CACHED_AT, 0L)
}
