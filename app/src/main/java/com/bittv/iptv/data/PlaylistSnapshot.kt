package com.bittv.iptv.data

data class PlaylistSnapshot(
    val content: String,
    val fingerprint: String,
    val etag: String? = null,
    val lastModified: String? = null,
    val revision: Long = 0L,
    val fromCache: Boolean = true,
    val notModified: Boolean = false
)
