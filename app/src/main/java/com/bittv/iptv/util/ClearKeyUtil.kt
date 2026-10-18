package com.bittv.iptv.util

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ubah nilai "#KODIPROP:inputstream.adaptive.license_key=..." (format umum
 * dipakai Kodi/inputstream.adaptive untuk ClearKey, biasanya "kid:key" dalam
 * hex, kadang base64) jadi JSON lisensi ClearKey yang dimengerti ExoPlayer
 * (format W3C EME ClearKey: {"keys":[{"kty":"oct","kid":"...","k":"..."}]}).
 *
 * Beberapa playlist punya lebih dari satu pasangan kid:key (misal buat video
 * dan audio track terpisah), dipisah pakai koma/ampersand/baris baru — semua
 * itu didukung di sini.
 */
object ClearKeyUtil {

    fun isClearKey(scheme: String?): Boolean =
        scheme != null && scheme.trim().equals("clearkey", ignoreCase = true)

    /** Return null kalau tidak ada satu pun pasangan kid:key yang valid. */
    fun buildLicenseJson(rawLicenseKey: String): String? {
        val pairs = rawLicenseKey
            .split(',', '&', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val keys = JSONArray()
        for (pair in pairs) {
            val sep = pair.indexOf(':')
            if (sep <= 0 || sep == pair.length - 1) continue

            val kidRaw = pair.substring(0, sep).trim()
            val keyRaw = pair.substring(sep + 1).trim()
            val kidB64 = toBase64Url(kidRaw) ?: continue
            val keyB64 = toBase64Url(keyRaw) ?: continue

            keys.put(
                JSONObject().apply {
                    put("kty", "oct")
                    put("kid", kidB64)
                    put("k", keyB64)
                }
            )
        }

        if (keys.length() == 0) return null

        return JSONObject().apply {
            put("keys", keys)
            put("type", "temporary")
        }.toString()
    }

    /**
     * kid/key di playlist IPTV biasanya hex 32 karakter (16 byte), tapi
     * sebagian sumber pakai base64/base64url langsung. Coba hex dulu (lebih
     * spesifik formatnya), baru fallback ke base64.
     */
    private fun toBase64Url(value: String): String? {
        val bytes = if (isHex(value)) {
            hexToBytes(value)
        } else {
            decodeBase64(value)
        } ?: return null

        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }

    private fun isHex(value: String): Boolean =
        value.isNotEmpty() &&
            value.length % 2 == 0 &&
            value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

    private fun hexToBytes(hex: String): ByteArray? = runCatching {
        ByteArray(hex.length / 2) { i ->
            val idx = i * 2
            ((Character.digit(hex[idx], 16) shl 4) + Character.digit(hex[idx + 1], 16)).toByte()
        }
    }.getOrNull()

    private fun decodeBase64(value: String): ByteArray? {
        val flagsToTry = intArrayOf(
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            Base64.URL_SAFE or Base64.NO_WRAP,
            Base64.DEFAULT
        )
        for (flags in flagsToTry) {
            runCatching { Base64.decode(value, flags) }.getOrNull()?.let { return it }
        }
        return null
    }
}
