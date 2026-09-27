package com.bittv.iptv.util

object NativePlaylist {
    private val nativeAvailable: Boolean = runCatching {
        System.loadLibrary("bittv_native")
        true
    }.getOrDefault(false)

    @JvmStatic
    external fun fingerprint(text: String): String

    fun safeFingerprint(text: String): String {
        if (nativeAvailable) {
            runCatching { fingerprint(text) }.getOrNull()?.let { return it }
        }
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
