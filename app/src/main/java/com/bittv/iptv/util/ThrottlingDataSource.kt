package com.bittv.iptv.util

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec

/**
 * Pembungkus [DataSource] yang benar-benar membatasi kecepatan unduh
 * (byte/detik), dipakai buat mode "Hemat Data".
 *
 * Kenapa ini perlu: `DefaultTrackSelector.setMaxVideoBitrate()` yang dipakai
 * sebelumnya CUMA berlaku kalau stream-nya adaptive (ada beberapa rendition
 * kualitas yang bisa dipilih ExoPlayer, kayak HLS master playlist dengan
 * banyak varian bitrate). Banyak channel IPTV di playlist ini cuma punya
 * SATU kualitas per channel (non-adaptive) — buat kasus itu setMaxVideoBitrate
 * gak ngaruh sama sekali, datanya tetap disedot penuh sesuai bitrate asli
 * stream-nya. Class ini menahan laju baca byte mentahnya sendiri di level
 * jaringan, jadi berlaku ke SEMUA jenis stream, adaptive maupun bukan.
 *
 * Konsekuensi yang jujur: kalau bitrate asli stream lebih besar dari limit
 * yang dipilih, playback-nya bisa keputus-putus/buffering (karena datanya
 * sengaja ditahan supaya gak lewat limit), bukan otomatis turun kualitas
 * mulus kayak stream adaptive. Itu trade-off yang gak bisa dihindari kalau
 * mau batasnya beneran ditegakkan, bukan cuma "saran" ke player.
 */
class ThrottlingDataSource(
    private val upstream: DataSource,
    private val maxBytesPerSecond: Long
) : DataSource by upstream {

    private var windowStartMs = 0L
    private var bytesInWindow = 0L

    override fun open(dataSpec: DataSpec): Long {
        windowStartMs = System.currentTimeMillis()
        bytesInWindow = 0L
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val bytesRead = upstream.read(buffer, offset, length)
        if (bytesRead <= 0 || maxBytesPerSecond <= 0) return bytesRead

        bytesInWindow += bytesRead
        val elapsedMs = System.currentTimeMillis() - windowStartMs
        val expectedMs = (bytesInWindow * 1000L) / maxBytesPerSecond
        val sleepMs = expectedMs - elapsedMs
        // Reset jendela tiap ~4 detik biar kalau ada lonjakan singkat gak
        // "diutangin" ke masa depan terus-terusan (mencegah sleep menumpuk).
        if (elapsedMs > 4_000L) {
            windowStartMs = System.currentTimeMillis()
            bytesInWindow = 0L
        }
        if (sleepMs > 0) {
            try {
                Thread.sleep(sleepMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        return bytesRead
    }

    /**
     * [maxBytesPerSecondProvider] dibaca ULANG setiap kali sebuah DataSource
     * baru dibuat (tiap fetch manifest/segmen baru) — jadi kalau user ganti
     * level Hemat Data pas lagi nonton, limit barunya kepakai di request
     * berikutnya tanpa perlu ganti channel/restart player dulu.
     */
    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val maxBytesPerSecondProvider: () -> Long
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            val upstream = upstreamFactory.createDataSource()
            val bps = maxBytesPerSecondProvider()
            return if (bps > 0) ThrottlingDataSource(upstream, bps) else upstream
        }
    }
}
