package com.bittv.iptv.util

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Bank soal "Tebak Gambar", diambil dari JSON remote biar bisa nambah soal
 * tanpa update APK. Sekali fetch, dicache di memory selama app hidup.
 */
object TebakGambarRepository {

    data class Item(
        val index: Int,
        val imageUrl: String,
        val answer: String,
        val description: String
    )

    private const val FEED_URL =
        "https://raw.githubusercontent.com/nazedev/database/refs/heads/master/games/tebakgambar.json"

    @Volatile
    private var cached: List<Item>? = null

    /** Panggil dari background thread. Hasil dicache, panggilan berikutnya instan. */
    fun fetch(): Result<List<Item>> {
        cached?.let { return Result.success(it) }

        return runCatching {
            val connection = URL(FEED_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android BITTV)")

            val raw = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            connection.disconnect()

            val array = JSONArray(raw)
            val items = ArrayList<Item>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val img = obj.optString("img").trim()
                val answer = obj.optString("jawaban").trim()
                if (img.isBlank() || answer.isBlank()) continue
                items += Item(
                    index = obj.optInt("index", i),
                    imageUrl = img,
                    answer = answer,
                    description = obj.optString("deskripsi").trim()
                )
            }
            if (items.isEmpty()) throw IllegalStateException("Tebak gambar feed kosong")
            cached = items
            items
        }
    }
}
