package com.bittv.iptv.util

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream

class EpgRepository(context: Context) {
    private val prefs = context.getSharedPreferences("bittv_epg", Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()

    fun cached(): String? =
        prefs.getString("xmltv", null)

    fun loadRemote(
        url: String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (url.isBlank()) return
        executor.execute {
            val result = refreshBlocking(url)
            result.onSuccess(onSuccess).onFailure { error ->
                val fallback = cached()
                if (!fallback.isNullOrBlank()) onSuccess(fallback) else onError(error)
            }
        }
    }

    fun refreshBlocking(url: String): Result<String> {
        if (url.isBlank()) return Result.failure(IllegalArgumentException("EPG URL is empty"))
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/xml, text/xml, */*")
            connection.setRequestProperty("Accept-Encoding", "gzip")

            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("EPG HTTP $code")
            }

            val input = if (connection.contentEncoding.equals("gzip", true)) {
                GZIPInputStream(connection.inputStream)
            } else {
                connection.inputStream
            }

            val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            require(text.contains("<tv", ignoreCase = true)) { "Invalid XMLTV response" }

            prefs.edit().putString("xmltv", text).apply()
            Result.success(text)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            connection?.disconnect()
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }
}
