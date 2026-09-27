package com.bittv.iptv.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object LogoLoader {
    private val cache = LruCache<String, Bitmap>(40)
    private val executor = Executors.newFixedThreadPool(3)

    fun load(url: String?, imageView: ImageView) {
        imageView.setImageDrawable(null)
        if (url.isNullOrBlank()) return
        val cached = synchronized(cache) { cache.get(url) }
        if (cached != null) {
            imageView.setImageBitmap(cached)
            return
        }
        imageView.tag = url
        executor.execute {
            val bitmap = runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android BITTV)")
                }
                connection.inputStream.use { BitmapFactory.decodeStream(it) }.also { connection.disconnect() }
            }.getOrNull()
            if (bitmap != null) synchronized(cache) { cache.put(url, bitmap) }
            imageView.post {
                if (imageView.tag == url && bitmap != null) imageView.setImageBitmap(bitmap)
            }
        }
    }
}