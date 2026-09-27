iwak tempek

## GAMEVERSE V2 — deep content & multiplayer
The project now contains a data-driven Gameverse layer under `app/src/main/java/com/bittv/iptv/game/`.
The bundled `app/src/main/assets/game/game_content.json` is the offline fallback catalogue.

### Content packs
Upload a larger JSON pack to Firebase Storage at:
`game-packs/v2/game_content.json`
Then use **RPG → Refresh Content Pack** in the app. The downloader verifies JSON structure and can optionally verify SHA-256 before replacing the cache. No downloaded content is executed as code.

For large art/audio files, keep them as separate Storage objects and add only their metadata/paths to the catalogue. This keeps the APK small and lets new seasons/events ship independently.

### Multiplayer
`Mabar Squad Raid` is a 2–4 player cooperative realtime room. Room creation, joining, starting, action resolution, reward claims, and leaderboard writes use Firebase callable functions. Realtime Database is only the state transport/read model for the room.

### Production Firebase checklist
Deploy `functions/`, `database.rules.json`, `firestore.rules`, and `storage.rules`. Register the Android app in Firebase, enable Authentication/Realtime Database/Firestore/Storage, and configure App Check with Play Integrity for release builds. Firebase documents App Check as a way to help ensure only the registered app accesses protected backend resources. See the official Firebase docs cited in the project audit notes.
