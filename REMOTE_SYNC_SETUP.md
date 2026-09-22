# BITTV — GitHub → FCM real-time sync

## Target behaviour

1. **Fresh install**: the current `adit.m3u` and `notif.json` are recorded silently as the baseline. No historical announcement is shown. FCM topic enrollment happens only after both baselines succeed, closing the fresh-install notification race.
2. **Later `notif.json` change**: GitHub Actions sends a data-only FCM event. The installed app can receive it while BITTV is not visibly open; WorkManager then fetches the current file and compares its fingerprint. Only an actual content revision produces an announcement.
3. **Later `adit.m3u` change**: GitHub Actions sends an FCM event. The app fetches and validates the latest M3U, stores it in app-private cache, and broadcasts a package-scoped update. An open Activity applies the cache immediately. If the active channel identity is unchanged but its stream URL changed, the Media3 player reconnects automatically without leaving the app or using manual refresh.
4. **EWS**: BMKG/MAGMA polling remains a background safety feature. Each stable hazard event ID is recorded once; repeated polling, changed distance, or reordered hazard lists do not create another notification for that same event.
5. **Fallbacks**: FCM is the real-time path. Existing WorkManager polling and foreground checks remain as recovery paths for delayed/missed push delivery.

## Firebase / FCM setup

The Android project already contains `app/google-services.json` for package `com.bittv.iptv`. The Firebase Messaging service is declared in the manifest. A fresh installation subscribes to `bittv-live-updates` only after both the playlist baseline and the silent `notif.json` baseline have completed successfully.

FCM topic messages can be sent with the FCM HTTP v1 API. The service account used by GitHub Actions needs the `cloudmessaging.messages.create` permission; Google documents `roles/firebasecloudmessaging.admin` as the current Firebase Cloud Messaging Admin role.

### Required GitHub Actions secret

Open **GitHub → Settings → Secrets and variables → Actions** and create:

`FCM_SERVICE_ACCOUNT_JSON`

Set it to the complete Google service-account JSON. Never commit the JSON/private key into the repository.

If the secret is missing, the Android build still runs, but the `remote-sync` job intentionally skips FCM delivery.

## Publish a remote announcement

Edit `notif.json` on the `main` branch:

```json
{
  "id": 1,
  "enabled": true,
  "title": "Update Penting",
  "message": "GTV sudah normal kembali."
}
```

Commit and push. The workflow detects `notif.json` and sends the FCM invalidation event. The app fetches the latest file; the Git commit itself is not treated as the notification body.

- Changing title/message/enabled creates a new content fingerprint.
- Changing only `id` also creates a new fingerprint, which gives the publisher an explicit revision knob.
- A whitespace-only/unrelated commit does not replay the same notification.
- `enabled: false` disables the announcement but still advances the baseline so it will not replay forever.

## Update channels without rebuilding the APK

Edit `adit.m3u`, commit, and push to `main`. The workflow sends FCM. The app downloads the newest validated M3U in the background.

When the APK is already open, the package-scoped update broadcast is received immediately. The channel list redraws from the private cache. A playing channel is matched by EPG ID, explicit channel ID, or name/group; when its URL changed, the player is prepared again automatically. This is the path for replacing an expired/broken stream URL without asking users to close or manually refresh BITTV.

## EWS anti-spam model

The first successful EWS scan is silent and becomes the baseline. After that, the app notifies only unseen stable event IDs. A single notification can contain multiple new hazards, and the state is kept in a compact persistent ledger.

This intentionally means a severity/distance update under the **same** event ID is not a second notification. A genuinely new event ID can notify once.

## Recovery behaviour

Firebase documents that an app may receive `onDeletedMessages()` after an excessive pending-message backlog. BITTV responds by scheduling a full content sync with no push event ID, so current content fingerprints decide what is genuinely new rather than replaying a stale alert.

## Expected delivery caveats

FCM is the correct real-time delivery mechanism, but no Android push system provides a mathematical instant-delivery guarantee. Force-stop, OEM background restrictions, offline periods, and expired message TTL can delay or suppress delivery. The project therefore keeps a low-frequency WorkManager fallback and a foreground check as safety nets.

## Release test sequence

1. Install fresh APK and allow notifications.
2. Wait for initial playlist to load; do not expect an old `notif.json` message.
3. Put the app in background. Change `notif.json` and push. Verify one notification.
4. Keep the app backgrounded and change `adit.m3u`. Verify the next open shows the new channel state even if push arrived while not visible.
5. Keep BITTV open and playing GTV. Change only GTV's stream URL in M3U and push. Verify the player reconnects without manual refresh.
6. Repeat the same notification content: it must not ring again.
7. Re-run the same EWS event: it must not create a second alert.
