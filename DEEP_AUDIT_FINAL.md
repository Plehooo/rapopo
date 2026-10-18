# BITTV / rapopo — Deep realtime audit

## Root causes found

1. Remote announcements came from the separate `Plehooo/ditz` repository, but that repository had no active workflow that dispatched a change to `Plehooo/rapopo`. The live `ditz` Actions page only showed the Pages deployment workflow.
2. The old Activity/startup path could call the notification feed checker, so opening the APK could cause an announcement to appear even when no new remote event happened.
3. `RemoteMessagingService` did not render the announcement directly from an FCM payload. It only queued background work, so the Activity/fallback path could become the apparent trigger.
4. Baseline/enrollment was coupled too closely to the playlist path. A broken M3U could therefore interfere with notification readiness.
5. FCM payload construction used an incorrect `collapseKey` spelling in an earlier workflow revision; the Android HTTP v1 field is `collapse_key`.
6. Playlist change detection needed stable channel identity independent of the stream URL so a URL rotation is treated as a channel change and not remove+add.
7. EWS needed stable event IDs plus a silent first-seen baseline and `KEEP` work policies to prevent repeat notifications caused by repeated scans.

## Final behavior

- `Plehooo/ditz/notif.json` remains the notification source.
- `Plehooo/ditz/adit.m3u` remains the remote playlist source.
- A fresh install baselines the current notification feed silently before FCM enrollment.
- FCM data-only messages render visible announcements directly from the payload, so `MainActivity` does not need to exist.
- A remote playlist event queues the existing `PlaylistUpdateWorker`; when the Activity is alive, its existing in-process broadcast path applies the snapshot and can reconnect an active channel to a new URL.
- Foreground catch-up updates data without creating a second notification.
- Duplicate notification fingerprints are ignored.
- EWS events are deduplicated by stable event identity and first scan is silent.
- Fallback workers remain in place for recovery, but are not the primary realtime trigger.

## Required external wiring

### In `Plehooo/rapopo`

Set Actions secret:

`FIREBASE_SERVICE_ACCOUNT_JSON`

### In `Plehooo/ditz`

Copy `DITZ_REALTIME_TRIGGER.yml.example` to:

`.github/workflows/notify-bittv.yml`

Then set Actions secret:

`RAPOPO_DISPATCH_TOKEN`

The token must be allowed to call the `repository_dispatch` endpoint on `Plehooo/rapopo`.

## Edit flow

No Termux commands are needed for daily remote operations.

1. Open `Plehooo/ditz/notif.json` in GitHub web and use **Edit -> Commit changes**.
2. The `ditz` workflow sends `repository_dispatch` to `rapopo`.
3. `rapopo` publishes FCM to topic `bittv_live_updates`.
4. Installed BITTV instances receive the event.

The same sequence applies to `Plehooo/ditz/adit.m3u` for playlist changes.

## Important platform limits

FCM high priority is used only for visible announcement payloads. Playlist sync messages use normal priority because Firebase documents normal priority as the appropriate mode for background data/UI synchronization; high priority is intended for time-sensitive user-visible content. Android/FCM delivery is not an absolute guarantee if the user force-stops the app, disables notifications, the device is offline, or an OEM aggressively restricts background activity.
