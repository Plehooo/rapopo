# BITTV GAMEVERSE V2 — Expansion Audit

## Added without removing the existing project tree
- Data-driven game catalogue with 24 characters, 20 encounters, 24 skills, 36 items, 30 quests, 12 dungeons and 10+ solo modes.
- `GameHubActivity` as a dedicated game front door.
- Deep RPG profile, roster, quest board, dungeon ladder, inventory, skill codex, energy, crystals and local progression.
- Arcade collection: memory, tap sprint, sequence, logic, typing, quiz, math, word, number and scramble.
- Mabar Squad Raid for 2–4 players with server-authoritative room lifecycle, turn/round resolution, score, rewards, quick match and leaderboard.
- Existing Tic-Tac-Toe Mabar remains intact.
- Firebase Storage content-pack downloader with private-app cache and optional SHA-256 verification.
- Optional large asset storage path for art/audio/cutscenes so the APK does not need to ship every future season.
- App Check bootstrap: Debug provider for debug builds, Play Integrity provider for release builds; enforcement remains a Firebase Console deployment decision.
- Split game notification channels for events, mabar, quests/rewards and content updates.
- Fixed FCM routing so game-only pushes no longer wake the playlist sync pipeline.
- Storage security rules: authenticated reads for `game-packs/**`, no client uploads.
- Existing TV, EPG, EWS, playlist, music, economy, friends and point-shop files remain present.

## Server-authoritative boundary
Firebase callable functions own:
- raid room creation/join/start
- raid action validation and round resolution
- raid reward claims and idempotency
- matchmaking ticket pairing
- raid leaderboard writes
- existing virtual coin transfer/market logic from the previous build

Realtime Database carries the live room read model/presence. Firestore stores durable server-side reward/economy records. Client-only RPG XP, collection and cosmetic/local progression are explicitly treated as local cache and are not a source of real-money value.

## Content-publishing model
The bundled JSON at `app/src/main/assets/game/game_content.json` is the offline fallback. Production content can be uploaded as `game-packs/v2/game_content.json`. Separate larger files can be stored under `game-packs/v2/art/`, `audio/`, and `cutscenes/` and downloaded on demand.

Downloaded content is treated as data only; the client does not execute downloaded code.
