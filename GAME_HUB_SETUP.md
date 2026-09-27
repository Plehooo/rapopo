# BITTV Game Hub

Game Hub ditambahkan ke Activity yang sama; jalur TV/ExoPlayer/playlist tidak diganti.

## Fitur

- Profil wajib nama saat pertama masuk. Nama tersimpan lokal dan dipakai di Game/Mabar.
- RPG offline-first: class Warrior/Mage/Ranger, level/XP, gold, HP, stamina, potion, monster, skill, jelajah, dungeon boss, quest progress, heal, daily claim.
- Mabar realtime Firebase: room 6 karakter, maksimal 4 pemain, Ready, host start raid, shared boss HP, hit/power hit, dan chat room.
- Settings di Game Hub: edit nama, Hemat Data, reset progres RPG, info versi.
- Daily Claim: +100 gold, +50 XP, +1 potion sekali per tanggal perangkat.

## Firebase

Aktifkan Anonymous Authentication dan Realtime Database pada project yang sama dengan `google-services.json`, lalu deploy `database.rules.json`.

Struktur data tambahan:

```text
gameProfiles/<uid>
mabarRooms/<ROOM>/hostUid
mabarRooms/<ROOM>/status
mabarRooms/<ROOM>/players/<uid>
mabarRooms/<ROOM>/messages/<id>
mabarRooms/<ROOM>/bossHp
mabarRooms/<ROOM>/bossMaxHp
mabarRooms/<ROOM>/logs/<id>
```

RPG solo tetap playable tanpa jaringan. Firebase hanya dipakai untuk sinkronisasi profil dan Mabar; bila Firebase gagal, menu RPG tidak ikut crash.
