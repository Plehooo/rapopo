package com.bittv.iptv.util;

import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Android-native parser for header directives commonly used by Ditai Media
 * and IPTV M3U playlists. The existing Rapopo TV player remains responsible
 * for playback and consumes the returned per-channel request headers.
 */
public final class DitaiM3uHeaderParser {
    private DitaiM3uHeaderParser() {}

    public static Map<String, String> parseVlcOpt(String raw) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (raw == null) return out;
        String value = raw.trim();
        int eq = value.indexOf('=');
        if (eq <= 0) return out;

        String key = value.substring(0, eq).trim().toLowerCase(Locale.ROOT);
        String headerValue = unquote(value.substring(eq + 1).trim());
        if (headerValue.isEmpty()) return out;

        switch (key) {
            case "http-referrer":
            case "http-referer":
            case "referer":
            case "referrer":
                putHeader(out, "Referer", headerValue);
                break;
            case "http-user-agent":
            case "user-agent":
            case "useragent":
                putHeader(out, "User-Agent", headerValue);
                break;
            case "http-origin":
            case "origin":
                putHeader(out, "Origin", headerValue);
                break;
            case "http-cookie":
            case "cookie":
                putHeader(out, "Cookie", headerValue);
                break;
            case "http-header":
            case "http-headers":
                out.putAll(parseHeaderBlock(headerValue));
                break;
            default:
                // Accept generic VLC-style options such as http-accept and http-authorization.
                if (key.startsWith("http-") && key.length() > 5) {
                    putHeader(out, key.substring(5), headerValue);
                }
                break;
        }
        return out;
    }

    public static Map<String, String> parseHeaderBlock(String raw) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        String value = raw.trim();

        // Ditai/OTT playlists often encode #EXTHTTP as a JSON object.
        if (value.startsWith("{") && value.endsWith("}")) {
            try {
                readJsonObject(new JSONObject(value), out);
                if (!out.isEmpty()) return out;
            } catch (Exception ignored) {
                // Fall back to the key=value formats below.
            }
        }

        // Also supports Referer=https://...&User-Agent=..., pipes and newlines.
        // Split on the first '=' so token values containing '=' stay intact.
        String[] parts = value.split("[&|\\r\\n]+");
        for (String part : parts) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String key = unquote(part.substring(0, eq).trim());
            String headerValue = unquote(part.substring(eq + 1).trim());
            putHeader(out, key, headerValue);
        }
        return out;
    }

    private static void readJsonObject(JSONObject obj, LinkedHashMap<String, String> out) {
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object rawValue = obj.opt(key);
            if (rawValue instanceof JSONObject) {
                readJsonObject((JSONObject) rawValue, out);
                continue;
            }
            if ("headers".equalsIgnoreCase(key) && rawValue instanceof String) {
                String nested = ((String) rawValue).trim();
                if (nested.startsWith("{") && nested.endsWith("}")) {
                    try { readJsonObject(new JSONObject(nested), out); } catch (Exception ignored) {}
                    continue;
                }
            }
            if (rawValue == null || rawValue == JSONObject.NULL) continue;
            if (rawValue instanceof String || rawValue instanceof Number || rawValue instanceof Boolean) {
                putHeader(out, key, String.valueOf(rawValue));
            }
        }
    }

    private static void putHeader(LinkedHashMap<String, String> out, String rawKey, String rawValue) {
        if (rawKey == null || rawValue == null) return;
        String key = unquote(rawKey.trim());
        String value = unquote(rawValue.trim());
        if (key.isEmpty() || value.isEmpty()) return;
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http-")) lower = lower.substring(5);

        String canonical;
        switch (lower) {
            case "referrer":
            case "referer": canonical = "Referer"; break;
            case "user-agent":
            case "useragent": canonical = "User-Agent"; break;
            case "origin": canonical = "Origin"; break;
            case "cookie": canonical = "Cookie"; break;
            case "authorization": canonical = "Authorization"; break;
            case "accept": canonical = "Accept"; break;
            case "accept-language": canonical = "Accept-Language"; break;
            case "accept-encoding": canonical = "Accept-Encoding"; break;
            case "content-type": canonical = "Content-Type"; break;
            case "x-requested-with": canonical = "X-Requested-With"; break;
            default: canonical = titleCaseHeader(key);
        }

        String previous = null;
        for (String existing : out.keySet()) {
            if (existing.equalsIgnoreCase(canonical)) { previous = existing; break; }
        }
        if (previous != null) out.remove(previous);
        out.put(canonical, value);
    }

    private static String titleCaseHeader(String key) {
        String[] chunks = key.toLowerCase(Locale.ROOT).split("-");
        StringBuilder b = new StringBuilder();
        for (String chunk : chunks) {
            if (chunk.isEmpty()) continue;
            if (b.length() > 0) b.append('-');
            b.append(Character.toUpperCase(chunk.charAt(0)));
            if (chunk.length() > 1) b.append(chunk.substring(1));
        }
        return b.toString();
    }

    private static String unquote(String value) {
        if (value == null || value.length() < 2) return value == null ? "" : value;
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }
}
