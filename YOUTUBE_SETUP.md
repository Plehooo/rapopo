# YouTube Data API setup for BITTV

The APK uses YouTube Data API v3 for **search/metadata** and the official YouTube embedded player for **video playback**. The Data API does not expose a raw playable media URL, so playback is not implemented by extracting YouTube streams.

## 1. Put the key into the build environment

Local Termux build:

```bash
cd ~/rapopo
./gradlew assembleDebug -PyoutubeApiKey=AIzaYOUR_KEY
```

or export it once:

```bash
export YOUTUBE_API_KEY='AIzaYOUR_KEY'
./gradlew assembleDebug
```

Do not commit the key. `local.properties` remains ignored by git.

## 2. Restrict the key

In Google Cloud Console, restrict the key to **Android apps**, then add the package/signing certificate pairs used by your builds. This project has `com.bittv.iptv` for release and `com.bittv.iptv.debug` for the debug build. The debug keystore fingerprint in this ZIP is:

```text
SHA-1: AE:AE:59:E2:34:70:4B:56:42:EC:A0:2E:3C:43:2A:BF:BD:B0:DD:C3
```

Also restrict the key to **YouTube Data API v3**.

## 3. Quota

Search uses `search.list` and requests up to 25 video results per search. Avoid adding automatic polling for YouTube search because the API has a daily quota for search requests.
