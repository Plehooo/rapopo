package com.bittv.iptv.data

data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val group: String,
    val streamUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val epgId: String? = null,
    val country: String? = null,
    // DRM ClearKey, diambil dari baris "#KODIPROP:" di M3U (format umum yang
    // dipakai Kodi/inputstream.adaptive). drmScheme biasanya "clearkey";
    // drmLicenseKey isinya "kid:key" (hex atau base64), lihat ClearKeyUtil.
    val drmScheme: String? = null,
    val drmLicenseKey: String? = null
)
