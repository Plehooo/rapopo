package com.bittv.iptv.util

import android.content.Context
import com.bittv.iptv.config.AppConfig
import com.bittv.iptv.data.M3uParser
import com.bittv.iptv.data.PlaylistDiff
import com.bittv.iptv.data.PlaylistDiffCalculator
import com.bittv.iptv.data.PlaylistSnapshot
import java.net.HttpURLConnection
import java.net.URL

sealed class PlaylistUpdateResult {

    data class Updated(
        val snapshot: PlaylistSnapshot,
        val diff: PlaylistDiff,
        val firstRemoteSync: Boolean,
        val totalChannels: Int
    ) : PlaylistUpdateResult()

    data class NotModified(
        val snapshot: PlaylistSnapshot
    ) : PlaylistUpdateResult()

    data class Failed(
        val error: Throwable
    ) : PlaylistUpdateResult()
}

class PlaylistRepository(
    context: Context,
    private val config: AppConfig
) {

    private val appContext = context.applicationContext
    private val statePrefs =
        appContext.getSharedPreferences(
            "playlist_remote_state",
            Context.MODE_PRIVATE
        )

    @Volatile
    private var memorySnapshot: PlaylistSnapshot? = null

    /**
     * Remote-only playlist loading.
     *
     * The M3U content is never written to disk. It is fetched from the
     * configured URL and kept only in memory for parsing/playback.
     */
    fun ensureLocal(): PlaylistSnapshot? {
        return fetchRemote().getOrNull()
    }

    /**
     * Checks the configured remote playlist directly.
     * No PlaylistStore/cache file is used. A small fingerprint/revision is
     * kept only to detect changes across background worker runs; the M3U
     * itself is never persisted.
     */
    fun checkForUpdate(): PlaylistUpdateResult {
        val previousMemory = memorySnapshot
        val previousFingerprint =
            statePrefs.getString(KEY_FINGERPRINT, null)

        return try {
            val fetched = fetchRemote().getOrThrow()
            val fingerprint = fetched.fingerprint

            val changed = previousFingerprint == null ||
                previousFingerprint != fingerprint

            if (!changed) {
                return PlaylistUpdateResult.NotModified(
                    fetched.copy(
                        revision = statePrefs.getLong(KEY_REVISION, 0L),
                        notModified = true
                    )
                )
            }

            val oldChannels = previousMemory?.let { snapshot ->
                runCatching {
                    M3uParser.parse(
                        snapshot.content,
                        config.playlistUrl
                    )
                }.getOrDefault(emptyList())
            } ?: emptyList()

            val newChannels = runCatching {
                M3uParser.parse(
                    fetched.content,
                    config.playlistUrl
                )
            }.getOrDefault(emptyList())

            if (newChannels.size < config.minimumChannels) {
                throw IllegalStateException(
                    "Remote playlist has too few valid channels"
                )
            }

            val diff = if (oldChannels.isNotEmpty()) {
                PlaylistDiffCalculator.compare(
                    oldChannels,
                    newChannels
                )
            } else {
                // Background workers intentionally do not retain the whole
                // previous M3U. A generic update notification is still emitted.
                PlaylistDiff(
                    added = 0,
                    removed = 0,
                    changed = 0,
                    unchanged = newChannels.size
                )
            }

            val oldRevision =
                statePrefs.getLong(KEY_REVISION, 0L)
            val newRevision = oldRevision + 1L

            val snapshot = fetched.copy(
                revision = newRevision,
                fromCache = false,
                notModified = false
            )

            statePrefs.edit()
                .putString(KEY_FINGERPRINT, fingerprint)
                .putString(KEY_ETAG, snapshot.etag)
                .putString(KEY_LAST_MODIFIED, snapshot.lastModified)
                .putLong(KEY_REVISION, newRevision)
                .apply()

            memorySnapshot = snapshot

            PlaylistUpdateResult.Updated(
                snapshot = snapshot,
                diff = diff,
                firstRemoteSync = previousFingerprint == null,
                totalChannels = newChannels.size
            )
        } catch (t: Throwable) {
            PlaylistUpdateResult.Failed(t)
        }
    }

    private fun fetchRemote(): Result<PlaylistSnapshot> {
        var connection: HttpURLConnection? = null

        return try {
            val remoteUrl = buildFreshUrl(config.playlistUrl)
            connection = URL(remoteUrl).openConnection() as HttpURLConnection

            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"

            connection.setRequestProperty(
                "User-Agent",
                "LIVE-TV/${config.version} (Android)"
            )
            connection.setRequestProperty(
                "Accept",
                "application/vnd.apple.mpegurl, " +
                    "application/x-mpegURL, " +
                    "audio/mpegurl, " +
                    "text/plain, */*"
            )

            // Force a fresh remote fetch. Nothing is persisted locally.
            connection.setRequestProperty(
                "Cache-Control",
                "no-cache, no-store, max-age=0"
            )
            connection.setRequestProperty(
                "Pragma",
                "no-cache"
            )

            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Playlist HTTP $code")
            }

            val contentLength = connection.contentLengthLong
            if (
                contentLength > 0L &&
                contentLength > config.maxPlaylistBytes
            ) {
                throw IllegalStateException(
                    "Remote playlist exceeds configured size limit"
                )
            }

            val content = connection.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { reader ->
                    val builder = StringBuilder()
                    val buffer = CharArray(16 * 1024)
                    var total = 0L

                    while (true) {
                        val count = reader.read(buffer)
                        if (count <= 0) break

                        total += count
                        if (total > config.maxPlaylistBytes) {
                            throw IllegalStateException(
                                "Remote playlist exceeds configured size limit"
                            )
                        }

                        builder.append(buffer, 0, count)
                    }

                    builder.toString()
                }

            if (content.isBlank()) {
                throw IllegalStateException("Remote playlist is empty")
            }

            if (!M3uParser.isValid(content, config.playlistUrl)) {
                throw IllegalStateException("Remote M3U failed validation")
            }

            val parsed = M3uParser.parse(
                content,
                config.playlistUrl
            )

            if (parsed.size < config.minimumChannels) {
                throw IllegalStateException(
                    "Remote playlist has too few valid channels"
                )
            }

            val snapshot = PlaylistSnapshot(
                content = content,
                fingerprint = NativePlaylist.safeFingerprint(content),
                etag = connection.getHeaderField("ETag"),
                lastModified = connection.getHeaderField("Last-Modified"),
                revision = statePrefs.getLong(KEY_REVISION, 0L),
                fromCache = false,
                notModified = false
            )

            Result.success(snapshot)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildFreshUrl(base: String): String {
        val separator = if (base.contains("?")) "&" else "?"
        return "$base${separator}__live_tv_refresh=${System.currentTimeMillis()}"
    }

    companion object {
        private const val KEY_FINGERPRINT = "fingerprint"
        private const val KEY_ETAG = "etag"
        private const val KEY_LAST_MODIFIED = "last_modified"
        private const val KEY_REVISION = "revision"
    }
}
