package com.bittv.iptv.data

data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val group: String,
    val streamUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val epgId: String? = null,
    val country: String? = null
)
