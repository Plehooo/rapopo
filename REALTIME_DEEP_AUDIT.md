# BITTV realtime deep audit

## Root cause found

The previous APK had two different baseline states:

1. `FreeNotification` stored the remote fingerprint in `bittv_free_notifications`.
2. `RemotePushManager` separately required `bittv_remote_push/baseline_ready=true` before it would subscribe to the FCM topic.

The startup flow called `FreeNotification.primeBaseline()` and then `RemotePushManager.ensureTopicSubscription()`, but it never called `RemotePushManager.markBaselineReady()`. Because of that, `isBaselineReady()` remained false forever on a fresh install, so topic subscription was silently skipped and the FCM realtime path could never become active.

The patch fixes this in two ways:

- The normal startup/worker baseline flow explicitly marks the baseline ready before subscribing.
- `RemotePushManager.isBaselineReady()` self-heals older installs when the fingerprint already exists but the flag is missing.

## Other reliability fixes

- FCM topic subscription no longer waits for `POST_NOTIFICATIONS` permission. FCM enrollment can happen while the Android 13+ permission dialog is pending; actual notification posting remains permission-gated.
- The publisher sends **data-only FCM** for visible announcements. High-priority data messages are handled by `RemoteMessagingService` in both foreground/background states, so the app has one notification-rendering path with persistent fingerprint dedupe. This avoids the background case where Android posts an automatic tray notification and the fallback worker later posts the same announcement again.
- The manifest declares the default FCM notification channel and icon.
- `RemoteMessagingService` accepts explicit data fields and falls back to `remoteMessage.notification` so Firebase Console tests work too.
- FCM enrollment failures are logged for diagnosis; token refresh continues to re-subscribe the device.

## Cross-repository trigger status

The source repo `Plehooo/ditz` currently does not show a `.github/workflows` directory in its public file listing. Therefore the `repository_dispatch` trigger described by this project is not active there unless it exists in another branch/private state.

The included `DITZ_REALTIME_TRIGGER_READY.yml` is the ready-to-copy workflow. It must be placed in:

`Plehooo/ditz/.github/workflows/notify-bittv.yml`

and the `RAPOPO_DISPATCH_TOKEN` Actions secret must be configured in `Plehooo/ditz` with permission to dispatch `Plehooo/rapopo`.

The 5-minute scheduled workflow in `rapopo` is only a fallback and cannot provide true push-on-commit realtime by itself.

## What “closed” means on Android

After one successful app launch, topic enrollment, and notification permission grant, a normal backgrounded/swiped-away app can receive FCM. Android/FCM documentation also notes that a user force-stopping the app from system settings can prevent messages until the app is manually opened again. That is an OS restriction, not something the APK can reliably bypass.

## Verification performed in this environment

- Source tree and all Android/FCM-related files were inspected.
- `google-services.json` package IDs match the app (`com.bittv.iptv` and debug `com.bittv.iptv.debug`).
- GitHub Actions publisher YAML parsed successfully.
- Embedded publisher Node.js code passed `node --check` syntax validation.
- Local Gradle build could not be executed because this isolated environment could not download Gradle 8.11.1 from `services.gradle.org` (`UnknownHostException`). The GitHub workflow already installs the required Android SDK components and remains the intended full build verification path.
