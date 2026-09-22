# BITTV IPTV

Modern Android live-TV app with M3U/M3U8/MPD playback, Firebase viewer presence, BMKG/MAGMA EWS, GitHub remote playlist data, and Firebase Cloud Messaging for real-time remote updates.

## Remote update model

GitHub remains the source of truth. Changes to `adit.m3u` and/or `notif.json` on `main` trigger GitHub Actions, which publishes a data-only FCM invalidation event to topic `bittv-live-updates`. The installed app receives the event in foreground or background, synchronizes the changed data, deduplicates it, and refreshes the open channel list automatically.

See `REMOTE_SYNC_SETUP.md` for Firebase/GitHub setup and `RELEASE_CHECKLIST.md` for release hardening.
