# BITTV GitHub Actions — automation fixed

The project now contains real GitHub Actions under `.github/workflows/`.

## 1. Automatic APK build

`.github/workflows/build-apk.yml` runs on:
- push to `main`
- pull request to `main`
- manual `workflow_dispatch`

It builds `assembleDebug` and uploads the result as the `BITTV-APK` artifact.

## 2. Automatic realtime FCM

`.github/workflows/realtime-update.yml` listens for the custom
`repository_dispatch` event type `remote-live-update`.

Required Actions secret in `Plehooo/rapopo`:

`FIREBASE_SERVICE_ACCOUNT_JSON`

Value: the full Firebase service-account JSON allowed to send FCM for the Firebase project used by the app.

The workflow publishes a data-only FCM message to:

`bittv_live_updates`

The Android client already subscribes to that topic through `RemotePushManager`.

## 3. Cross-repository trigger

The source repository `Plehooo/ditz` still needs its own workflow at:

`.github/workflows/notify-bittv.yml`

Use the provided `DITZ_REALTIME_TRIGGER.yml.example`.

Required Actions secret in `Plehooo/ditz`:

`RAPOPO_DISPATCH_TOKEN`

It must have permission to call `repository_dispatch` on `Plehooo/rapopo`.

Flow:

`Plehooo/ditz push -> repository_dispatch -> Plehooo/rapopo realtime workflow -> FCM -> BITTV`

A YAML file sitting at the repository root does not become an Action by itself.

## 4. First check after pushing

Open the GitHub `Actions` tab and confirm these workflows are visible:
- Build BITTV APK
- BITTV Realtime Update
- Validate BITTV

After a normal source push to `main`, `Build BITTV APK` and `Validate BITTV` should start automatically.

After a `remote-live-update` dispatch from `ditz`, `BITTV Realtime Update` should run.

## 5. Secrets

Never commit the Firebase service-account JSON or a GitHub token into the repository. Store both as GitHub Actions secrets.
