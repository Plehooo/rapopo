package com.bittv.iptv.data

import android.content.Context
import java.io.File
import com.bittv.iptv.util.NativePlaylist

class PlaylistStore(context: Context) {

    private val appContext = context.applicationContext

    private val playlistDir: File
        get() = File(appContext.filesDir, "playlist").apply {
            if (!exists()) {
                mkdirs()
            }
        }

    private val playlistFile: File
        get() = File(playlistDir, "dhanytv.m3u")

    private val metadataFile: File
        get() = File(playlistDir, "metadata.properties")

    fun exists(): Boolean {
        return playlistFile.exists() && playlistFile.length() > 0L
    }

    fun read(): String? {
        if (!exists()) return null

        return try {
            playlistFile.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun snapshot(): PlaylistSnapshot? {
        val content = read() ?: return null

        val cachedFingerprint = getMetadata("fingerprint")
        val effectiveFingerprint = cachedFingerprint
            ?.takeIf { it.isNotBlank() }
            ?: fingerprint(content)

        return PlaylistSnapshot(
            content = content,
            fingerprint = effectiveFingerprint,
            etag = getMetadata("etag"),
            lastModified = getMetadata("lastModified"),
            revision = getMetadata("revision")?.toLongOrNull() ?: 0L,
            fromCache = true,
            notModified = false
        )
    }

    fun ensureBundledPlaylist(): PlaylistSnapshot? {
        val current = snapshot()

        if (current != null) {
            return current
        }

        return try {
            val bundled = appContext.assets.open("dhanytv.m3u").use {
                it.readBytes().toString(Charsets.UTF_8)
            }

            if (bundled.isBlank()) {
                null
            } else {
                saveRemote(
                    content = bundled,
                    etag = null,
                    lastModified = null
                )

                snapshot()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun save(content: String): Boolean {
        return saveRemote(
            content = content,
            etag = null,
            lastModified = null
        )
    }

    fun saveRemote(
        content: String,
        etag: String?,
        lastModified: String?
    ): Boolean {

        if (content.isBlank()) return false

        return try {
            val tempFile = File(
                playlistDir,
                "dhanytv.m3u.tmp"
            )

            tempFile.writeText(
                content,
                Charsets.UTF_8
            )

            if (!tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                return false
            }

            if (!tempFile.renameTo(playlistFile)) {
                // Some Android filesystems refuse replace-over-existing.
                // Keep a backup while swapping so a failed rename does not
                // leave the device without a valid playlist.
                val backupFile = File(
                    playlistDir,
                    "dhanytv.m3u.bak"
                )

                runCatching { backupFile.delete() }

                val hadOld = playlistFile.exists()
                if (hadOld && !playlistFile.renameTo(backupFile)) {
                    tempFile.delete()
                    return false
                }

                if (!tempFile.renameTo(playlistFile)) {
                    runCatching { backupFile.renameTo(playlistFile) }
                    tempFile.delete()
                    return false
                }

                runCatching { backupFile.delete() }
            }

            val oldRevision =
                getMetadata("revision")
                    ?.toLongOrNull()
                    ?: 0L

            val newRevision = oldRevision + 1L

            setMetadata(
                "etag",
                etag
            )

            setMetadata(
                "lastModified",
                lastModified
            )

            setMetadata(
                "revision",
                newRevision.toString()
            )

            setMetadata(
                "fingerprint",
                fingerprint(content)
            )

            true
        } catch (_: Exception) {
            false
        }
    }

    fun rememberValidators(
        etag: String?,
        lastModified: String?
    ) {
        try {
            setMetadata("etag", etag)
            setMetadata("lastModified", lastModified)
        } catch (_: Exception) {
            // Ignore metadata failures.
        }
    }

    fun clear() {
        try {
            playlistFile.delete()
            metadataFile.delete()
        } catch (_: Exception) {
            // Ignore cleanup errors.
        }
    }

    fun size(): Long {
        return if (playlistFile.exists()) {
            playlistFile.length()
        } else {
            0L
        }
    }

    fun lastModified(): Long {
        return if (playlistFile.exists()) {
            playlistFile.lastModified()
        } else {
            0L
        }
    }

    private fun fingerprint(content: String): String =
        NativePlaylist.safeFingerprint(content)

    private fun getMetadata(
        key: String
    ): String? {

        if (!metadataFile.exists()) {
            return null
        }

        return try {
            metadataFile
                .readLines(Charsets.UTF_8)
                .firstOrNull {
                    it.startsWith("$key=")
                }
                ?.substringAfter("=")
                ?.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun setMetadata(
        key: String,
        value: String?
    ) {

        val values = mutableMapOf<String, String>()

        if (metadataFile.exists()) {
            metadataFile
                .readLines(Charsets.UTF_8)
                .forEach { line ->

                    val separator = line.indexOf("=")

                    if (separator > 0) {
                        val k = line.substring(
                            0,
                            separator
                        )

                        val v = line.substring(
                            separator + 1
                        )

                        values[k] = v
                    }
                }
        }

        if (value == null) {
            values.remove(key)
        } else {
            values[key] = value
        }

        metadataFile.writeText(
            values.entries.joinToString("\n") {
                "${it.key}=${it.value}"
            },
            Charsets.UTF_8
        )
    }
}
