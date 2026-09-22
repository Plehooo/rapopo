# BITTV realtime / EWS hardening audit

## Release

- Version: **3.1.3**
- versionCode: **34**
- WorkManager: **2.11.2**
- Firebase Android BoM: **34.19.0**
- Main Android module remains `:app`; the historical `android/` tree is not removed.

## Target behavior

1. GitHub `notif.json` changes trigger a data-only FCM event. Enrolled devices can receive that event while BITTV is not visibly open.
2. GitHub `adit.m3u` changes trigger the same FCM path. The app fetches and validates the authoritative playlist, updates the private cache, and broadcasts the new playlist to an already-running MainActivity.
3. If the currently playing logical channel keeps the same identity but receives a new stream URL, MainActivity reconnects the player automatically; no app restart or manual refresh is required.
4. Fresh installs do not subscribe to the shared FCM topic until a successful playlist baseline exists. The initial remote announcement and existing EWS hazards are recorded silently.
5. EWS keeps a persistent event-id history and notifies once per event. Repeated scans of the same event are suppressed.
6. BMKG CAP polygons are evaluated as real polygon areas for inclusion/boundary distance instead of being reduced to a bounding circle.
7. WorkManager remains as recovery/polling fallback because FCM delivery can be delayed or dropped under force-stop, OEM restrictions, offline periods, or expired message TTL.

## Remote-sync race control

- FCM events are serialized through one unique WorkManager chain.
- Content fingerprints remain the authority, so duplicate FCM deliveries and fallback polling do not create duplicate notifications.
- A push event is only an invalidation signal; the device fetches the current GitHub content before acting.
- `onDeletedMessages()` requests a full sync rather than trusting stale push state.
- Remote files are validated in GitHub Actions before an FCM event is sent.

## Validation performed

- XML resources: parser validation passed.
- JSON resources: parser validation passed.
- GitHub Actions YAML: parsed successfully during audit.
- Pure Kotlin M3U/diff tests: passed in the available local toolchain.
- Full Android Gradle build: not executable in this sandbox because the Gradle distribution/dependencies require external network access; GitHub Actions is configured as the authoritative build/test environment.

## Changed areas

No existing source file was deleted. Changes are concentrated in remote synchronization, playlist caching/diffing, EWS notification/geospatial handling, lifecycle wiring, WorkManager versioning, tests, CI validation, and release documentation.
