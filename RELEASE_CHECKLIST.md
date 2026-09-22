# BITTV release checklist

## Project

- Version: 3.1.3 / versionCode 34
- `compileSdk` / `targetSdk`: 35
- WorkManager: 2.11.2
- FCM Messaging is enabled.
- Production keystore remains outside Git.

## CI

GitHub Actions performs JVM unit tests, debug build, debug lint, optional signed release build, APK verification, artifact cleanup, and a separate FCM remote-sync job for changes to `adit.m3u`/`notif.json`.

## Required GitHub secrets

- `FCM_SERVICE_ACCOUNT_JSON`
- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

## Manual smoke test

Fresh install must silently baseline the currently published M3U and `notif.json` before FCM enrollment. After enrollment, `notif.json` changes must be able to notify the app while it is in background. `adit.m3u` changes must sync in background and immediately refresh an already-open Activity; a changed active stream URL must reconnect automatically. Same-content FCM events must not ring twice. Same EWS event IDs must not ring twice.
