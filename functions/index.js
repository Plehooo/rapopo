import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getDatabase } from "firebase-admin/database";
import { getMessaging } from "firebase-admin/messaging";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { setGlobalOptions } from "firebase-functions/v2/options";
import { defineSecret } from "firebase-functions/params";

initializeApp();
setGlobalOptions({
  region: "asia-southeast2",
  maxInstances: 10,
  timeoutSeconds: 30,
  memory: "256MiB"
});

const db = getFirestore();
const realtime = getDatabase();
const gameAdminKey = defineSecret("GAME_ADMIN_KEY");
const START_COINS = 1000;
const MAX_TRANSFER = 5000;
const MAX_SHARES_PER_TRADE = 100;

const STOCKS = {
  BITV: { name: "BITTV Network", sector: "Media", base: 120, phase: 0.2 },
  RAPO: { name: "RAPO Digital", sector: "Tech", base: 180, phase: 1.1 },
  NUSA: { name: "Nusantara Cloud", sector: "Cloud", base: 95, phase: 2.3 },
  LUME: { name: "Lumen Studio", sector: "Creative", base: 72, phase: 3.4 },
  KOTA: { name: "Kota Mart", sector: "Retail", base: 140, phase: 4.5 },
  ARCA: { name: "Arca Energy", sector: "Energy", base: 210, phase: 5.1 },
  JAYA: { name: "Jaya Food", sector: "Food", base: 88, phase: 0.9 },
  ORBI: { name: "Orbit Labs", sector: "Science", base: 260, phase: 2.8 }
};

function requireAuth(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication diperlukan.");
  return uid;
}

function cleanSymbol(value) {
  const symbol = String(value ?? "").trim().toUpperCase();
  if (!STOCKS[symbol]) throw new HttpsError("invalid-argument", "Saham virtual tidak ditemukan.");
  return symbol;
}

function quote(symbol, now = Date.now()) {
  const stock = STOCKS[symbol];
  const hour = Math.floor(now / 3600000);
  const day = Math.floor(now / 86400000);
  const wave = Math.sin(day * 0.47 + hour * 0.23 + stock.phase);
  const trend = Math.sin(day * 0.07 + stock.phase * 0.8) * 0.10;
  const change = wave * 0.08 + trend;
  return {
    symbol,
    name: stock.name,
    sector: stock.sector,
    price: Math.max(10, Math.round(stock.base * (1 + change))),
    changePct: Number((change * 100).toFixed(2))
  };
}

function dayStamp(now = new Date()) {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Jakarta", year: "numeric", month: "2-digit", day: "2-digit" }).format(now);
}

function toInt(value) {
  const n = Number(value);
  return Number.isFinite(n) ? Math.trunc(n) : 0;
}

export const initializeWallet = onCall(async (request) => {
  const uid = requireAuth(request);
  const ref = db.collection("economyWallets").doc(uid);
  const snap = await ref.get();
  if (!snap.exists) {
    await ref.set({ coins: START_COINS, streak: 0, createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
  }
  const fresh = await ref.get();
  const data = fresh.data() ?? {};
  return { coins: Number(data.coins ?? START_COINS), streak: Number(data.streak ?? 0) };
});

export const getWallet = onCall(async (request) => {
  const uid = requireAuth(request);
  const ref = db.collection("economyWallets").doc(uid);
  const snap = await ref.get();
  if (!snap.exists) return { coins: START_COINS, streak: 0 };
  const data = snap.data() ?? {};
  return { coins: Number(data.coins ?? 0), streak: Number(data.streak ?? 0) };
});

export const claimDailyCoins = onCall(async (request) => {
  const uid = requireAuth(request);
  const ref = db.collection("economyWallets").doc(uid);
  const today = dayStamp();
  const result = await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const data = snap.exists ? snap.data() : {};
    const lastClaim = data?.lastClaimDay ?? "";
    if (lastClaim === today) throw new HttpsError("already-exists", "Check-in hari ini sudah diambil.");
    const previousStreak = Number(data?.streak ?? 0);
    const yesterday = dayStamp(new Date(Date.now() - 86400000));
    const streak = lastClaim === yesterday ? previousStreak + 1 : 1;
    const reward = Math.min(150, 50 + ((streak - 1) % 7) * 10);
    const coins = Number(data?.coins ?? START_COINS) + reward;
    tx.set(ref, { coins, streak, lastClaimDay: today, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    return { coins, reward, streak };
  });
  return result;
});

export const transferCoins = onCall(async (request) => {
  const uid = requireAuth(request);
  const targetUid = String(request.data?.targetUid ?? "").trim();
  const amount = toInt(request.data?.amount);
  if (!targetUid || targetUid === uid) throw new HttpsError("invalid-argument", "Penerima tidak valid.");
  if (amount <= 0 || amount > MAX_TRANSFER) throw new HttpsError("invalid-argument", `Maksimal transfer ${MAX_TRANSFER} coin.`);

  const senderRef = db.collection("economyWallets").doc(uid);
  const targetRef = db.collection("economyWallets").doc(targetUid);
  const logRef = db.collection("coinTransfers").doc();

  const result = await db.runTransaction(async (tx) => {
    const [senderSnap, targetSnap] = await Promise.all([tx.get(senderRef), tx.get(targetRef)]);
    const sender = senderSnap.exists ? senderSnap.data() : {};
    const target = targetSnap.exists ? targetSnap.data() : {};
    const senderCoins = Number(sender?.coins ?? START_COINS);
    const targetCoins = Number(target?.coins ?? START_COINS);
    if (senderCoins < amount) throw new HttpsError("failed-precondition", "Coin tidak cukup.");
    const newSender = senderCoins - amount;
    const newTarget = targetCoins + amount;
    tx.set(senderRef, { coins: newSender, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    tx.set(targetRef, { coins: newTarget, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    tx.set(logRef, { from: uid, to: targetUid, amount, createdAt: FieldValue.serverTimestamp() });
    return { senderCoins: newSender, amount };
  });
  return result;
});

export const getVirtualMarket = onCall(async (request) => {
  requireAuth(request);
  return { quotes: Object.keys(STOCKS).map((symbol) => quote(symbol)) };
});

export const getPortfolio = onCall(async (request) => {
  const uid = requireAuth(request);
  const walletSnap = await db.collection("economyWallets").doc(uid).get();
  const portfolioSnap = await db.collection("economyPortfolios").doc(uid).get();
  const wallet = walletSnap.data() ?? {};
  const holdings = portfolioSnap.data()?.holdings ?? {};
  return { coins: Number(wallet.coins ?? START_COINS), holdings };
});

export const tradeVirtualStock = onCall(async (request) => {
  const uid = requireAuth(request);
  const symbol = cleanSymbol(request.data?.symbol);
  const side = String(request.data?.side ?? "").trim().toUpperCase();
  const shares = toInt(request.data?.shares);
  if (!["BUY", "SELL"].includes(side)) throw new HttpsError("invalid-argument", "Side transaksi tidak valid.");
  if (shares <= 0 || shares > MAX_SHARES_PER_TRADE) throw new HttpsError("invalid-argument", "Jumlah lembar terlalu besar.");

  const price = quote(symbol).price;
  const walletRef = db.collection("economyWallets").doc(uid);
  const portfolioRef = db.collection("economyPortfolios").doc(uid);

  const result = await db.runTransaction(async (tx) => {
    const walletSnap = await tx.get(walletRef);
    const portfolioSnap = await tx.get(portfolioRef);
    const wallet = walletSnap.data() ?? {};
    const portfolio = portfolioSnap.data() ?? {};
    const holdings = { ...(portfolio.holdings ?? {}) };
    const current = holdings[symbol] ?? { shares: 0, avgCost: 0 };
    let coins = Number(wallet.coins ?? START_COINS);
    let nextShares = Number(current.shares ?? 0);
    let avgCost = Number(current.avgCost ?? 0);

    if (side === "BUY") {
      const cost = price * shares;
      if (coins < cost) throw new HttpsError("failed-precondition", "Coin tidak cukup.");
      const totalShares = nextShares + shares;
      avgCost = totalShares > 0 ? ((avgCost * nextShares) + price * shares) / totalShares : price;
      nextShares = totalShares;
      coins -= cost;
    } else {
      if (nextShares < shares) throw new HttpsError("failed-precondition", "Pegangan saham virtual tidak cukup.");
      nextShares -= shares;
      coins += price * shares;
      if (nextShares === 0) avgCost = 0;
    }

    holdings[symbol] = { shares: nextShares, avgCost };
    tx.set(walletRef, { coins, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    tx.set(portfolioRef, { holdings, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    return { coins, shares: nextShares, avgCost, price };
  });
  return result;
});



function tttWinner(board) {
  const wins = [[0,1,2],[3,4,5],[6,7,8],[0,3,6],[1,4,7],[2,5,8],[0,4,8],[2,4,6]];
  for (const [a,b,c] of wins) {
    if (board[a] !== '-' && board[a] === board[b] && board[b] === board[c]) return board[a];
  }
  return board.includes('-') ? '' : 'DRAW';
}

export const syncMabarRoom = onCall(async (request) => {
  const uid = requireAuth(request);
  const roomCode = String(request.data?.roomCode ?? '').trim().toUpperCase();
  if (!/^[A-Z0-9]{6,16}$/.test(roomCode)) throw new HttpsError('invalid-argument', 'Kode room tidak valid.');

  const roomRef = realtime.ref(`rooms/${roomCode}`);
  const snap = await roomRef.once('value');
  if (!snap.exists()) throw new HttpsError('not-found', 'Room tidak ditemukan.');
  const room = snap.val() ?? {};
  const players = room.players ?? {};
  if (!players[uid]) throw new HttpsError('permission-denied', 'Kamu bukan pemain room ini.');

  const ids = Object.keys(players).slice(0, 2);
  const ownerUid = String(room.ownerUid ?? ids[0] ?? '');
  if (!ownerUid || !ids.includes(ownerUid)) throw new HttpsError('failed-precondition', 'Owner room tidak valid.');

  const symbols = { ...(room.symbols ?? {}) };
  symbols[ownerUid] = 'X';
  const other = ids.find((id) => id !== ownerUid);
  if (other) symbols[other] = 'O';
  const currentStatus = String(room.status ?? 'waiting');
  const starting = ids.length >= 2 && currentStatus === 'waiting';
  const nextStatus = currentStatus === 'finished' || currentStatus.startsWith('finished:')
    ? currentStatus
    : (ids.length >= 2 ? 'playing' : 'waiting');
  const updates = { symbols, updatedAt: Date.now() };
  if (starting) {
    updates.status = 'playing';
    updates.turn = ownerUid;
  } else if (ids.length < 2 && currentStatus === 'waiting') {
    updates.status = 'waiting';
    updates.turn = ownerUid;
  }
  await roomRef.update(updates);
  return { ok: true, status: nextStatus, players: ids.length };
});

export const submitMabarMove = onCall(async (request) => {
  const uid = requireAuth(request);
  const roomCode = String(request.data?.roomCode ?? '').trim().toUpperCase();
  const index = toInt(request.data?.index);
  if (!/^[A-Z0-9]{6,16}$/.test(roomCode) || index < 0 || index > 8) {
    throw new HttpsError('invalid-argument', 'Move tidak valid.');
  }

  const roomRef = realtime.ref(`rooms/${roomCode}`);
  const moveId = crypto.randomUUID();
  const tx = await roomRef.transaction((current) => {
    if (!current || typeof current !== 'object') return current;
    const players = current.players ?? {};
    const symbols = current.symbols ?? {};
    const ids = Object.keys(players).slice(0, 2);
    if (ids.length !== 2 || !players[uid]) return current;
    const mySymbol = String(symbols[uid] ?? '');
    if (!['X','O'].includes(mySymbol) || String(current.status ?? '') !== 'playing') return current;
    if (String(current.turn ?? '') !== uid) return current;

    const board = String(current.board ?? '---------');
    if (board.length !== 9 || board[index] !== '-') return current;
    const nextBoard = board.split('');
    nextBoard[index] = mySymbol;
    const boardString = nextBoard.join('');
    const winner = tttWinner(boardString);
    const otherUid = ids.find((id) => id !== uid) ?? '';
    return {
      ...current,
      board: boardString,
      turn: winner ? uid : otherUid,
      status: winner ? `finished:${winner}` : 'playing',
      lastMoveBy: uid,
      lastMoveIndex: index,
      lastMoveId: moveId,
      updatedAt: Date.now()
    };
  });

  if (!tx.committed || String(tx.snapshot.child('lastMoveId').val() ?? '') !== moveId) {
    throw new HttpsError('failed-precondition', 'Giliran berubah atau kotak sudah terisi.');
  }
  const room = tx.snapshot.val() ?? {};
  return { board: String(room.board ?? '---------'), status: String(room.status ?? ''), turn: String(room.turn ?? '') };
});

export const claimMabarReward = onCall(async (request) => {
  const uid = requireAuth(request);
  const roomCode = String(request.data?.roomCode ?? "").trim().toUpperCase();
  if (!/^[A-Z0-9]{6,16}$/.test(roomCode)) {
    throw new HttpsError("invalid-argument", "Kode room tidak valid.");
  }

  const roomSnap = await realtime.ref(`rooms/${roomCode}`).once("value");
  if (!roomSnap.exists()) throw new HttpsError("not-found", "Room tidak ditemukan.");
  const room = roomSnap.val() ?? {};
  if (String(room.game ?? "") !== "ttt") throw new HttpsError("failed-precondition", "Game room tidak valid.");
  const players = room.players ?? {};
  if (!players[uid]) throw new HttpsError("permission-denied", "Kamu bukan pemain room ini.");

  const board = String(room.board ?? "---------");
  const status = String(room.status ?? "");
  if (!status.startsWith("finished:") || board.length !== 9) {
    throw new HttpsError("failed-precondition", "Match belum selesai.");
  }

  const symbols = room.symbols ?? {};
  const mySymbol = String(symbols[uid] ?? "");
  const result = status.substring("finished:".length);
  if (!mySymbol) throw new HttpsError("failed-precondition", "Simbol pemain belum ditetapkan.");

  const playerIds = Object.keys(players).slice(0, 2);
  if (playerIds.length < 2) throw new HttpsError("failed-precondition", "Dua pemain diperlukan.");

  const rewardDoc = db.collection("mabarRewards").doc(`${roomCode}_${uid}`);
  const walletRef = db.collection("economyWallets").doc(uid);
  const reward = result === "DRAW" ? 20 : (result === mySymbol ? 50 : 15);
  const role = result === "DRAW" ? "draw" : (result === mySymbol ? "winner" : "loser");

  const final = await db.runTransaction(async (tx) => {
    const already = await tx.get(rewardDoc);
    const walletSnap = await tx.get(walletRef);
    const wallet = walletSnap.data() ?? {};
    let coins = Number(wallet.coins ?? START_COINS);
    if (already.exists) {
      return { coins, reward: 0, role: "already_claimed" };
    }
    coins += reward;
    tx.set(walletRef, { coins, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    tx.set(rewardDoc, { uid, roomCode, reward, role, createdAt: FieldValue.serverTimestamp() });
    return { coins, reward, role };
  });

  return final;
});

export const sendGameAnnouncement = onCall({ secrets: [gameAdminKey] }, async (request) => {
  requireAuth(request);
  if (request.data?.adminKey !== gameAdminKey.value()) throw new HttpsError("permission-denied", "Admin only.");
  const title = String(request.data?.title ?? "BITTV Game Event").trim().slice(0, 80);
  const body = String(request.data?.body ?? "Ada event baru di Game Hub.").trim().slice(0, 240);
  if (!title || !body) throw new HttpsError("invalid-argument", "Judul dan pesan wajib diisi.");
  const messageId = await getMessaging().send({
    topic: "bittv_game_events",
    notification: { title, body },
    data: { kind: "game", title, body },
    android: { notification: { channelId: "game_events" } }
  });
  return { ok: true, topic: "bittv_game_events", messageId };
});

// ========================== GAMEVERSE V2 ==========================
const RAID_MAX_PLAYERS = 4;
const RAID_MAX_ROUNDS = 12;
const RAID_ACTIONS = {
  FOCUS: 46,
  GUARD: 24,
  SUPPORT: 30,
  SCAN: 34
};

function cleanDisplayName(value) {
  return String(value ?? "Pemain")
    .trim()
    .replace(/\s+/g, " ")
    .slice(0, 24) || "Pemain";
}

function randomRoomCode(length = 8) {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  let out = "";
  for (let i = 0; i < length; i += 1) out += chars[Math.floor(Math.random() * chars.length)];
  return out;
}

async function uniqueRaidCode() {
  for (let i = 0; i < 12; i += 1) {
    const code = randomRoomCode();
    const snap = await realtime.ref(`raidRooms/${code}`).once("value");
    if (!snap.exists()) return code;
  }
  throw new HttpsError("resource-exhausted", "Server sedang padat, coba buat room lagi.");
}

function raidPlayerMap(room) {
  return room?.players && typeof room.players === "object" ? room.players : {};
}

export const createRaidRoom = onCall(async (request) => {
  const uid = requireAuth(request);
  const difficulty = ["normal", "hard", "nightmare"].includes(String(request.data?.difficulty ?? "normal").toLowerCase())
    ? String(request.data?.difficulty ?? "normal").toLowerCase()
    : "normal";
  const code = await uniqueRaidCode();
  const energyByDifficulty = { normal: 900, hard: 1250, nightmare: 1650 };
  const snap = await db.collection("gameProfiles").doc(uid).get();
  const profile = snap.data() ?? {};
  const name = cleanDisplayName(profile.displayName || request.data?.displayName);
  await realtime.ref(`raidRooms/${code}`).set({
    game: "raid",
    ownerUid: uid,
    difficulty,
    maxPlayers: RAID_MAX_PLAYERS,
    status: "waiting",
    round: 0,
    bossEnergy: energyByDifficulty[difficulty],
    actions: {},
    scores: {},
    players: { [uid]: { name, ready: true, joinedAt: Date.now() } },
    createdAt: Date.now(),
    updatedAt: Date.now()
  });
  return { roomCode: code, status: "waiting" };
});

export const joinRaidRoom = onCall(async (request) => {
  const uid = requireAuth(request);
  const code = String(request.data?.roomCode ?? "").trim().toUpperCase();
  if (!/^[A-Z2-9]{8}$/.test(code)) throw new HttpsError("invalid-argument", "Kode raid harus 8 karakter.");
  const ref = realtime.ref(`raidRooms/${code}`);
  const snap = await ref.once("value");
  if (!snap.exists()) throw new HttpsError("not-found", "Room raid tidak ditemukan.");
  const room = snap.val() ?? {};
  const players = raidPlayerMap(room);
  if (players[uid]) return { roomCode: code, status: String(room.status ?? "waiting") };
  if (String(room.status ?? "") !== "waiting") throw new HttpsError("failed-precondition", "Raid sudah dimulai.");
  if (Object.keys(players).length >= RAID_MAX_PLAYERS) throw new HttpsError("resource-exhausted", "Room penuh.");
  const profileSnap = await db.collection("gameProfiles").doc(uid).get();
  const profile = profileSnap.data() ?? {};
  const name = cleanDisplayName(profile.displayName || request.data?.displayName);
  await ref.child("players").child(uid).set({ name, ready: true, joinedAt: Date.now() });
  await ref.update({ updatedAt: Date.now() });
  return { roomCode: code, status: "waiting" };
});

export const startRaidRoom = onCall(async (request) => {
  const uid = requireAuth(request);
  const code = String(request.data?.roomCode ?? "").trim().toUpperCase();
  const ref = realtime.ref(`raidRooms/${code}`);
  const tx = await ref.transaction((room) => {
    if (!room || typeof room !== "object") return room;
    const players = raidPlayerMap(room);
    if (String(room.ownerUid ?? "") !== uid || Object.keys(players).length < 2 || String(room.status ?? "") !== "waiting") return room;
    return { ...room, status: "playing", round: 1, actions: {}, startedAt: Date.now(), updatedAt: Date.now() };
  });
  if (!tx.committed || String(tx.snapshot.child("status").val() ?? "") !== "playing") throw new HttpsError("failed-precondition", "Raid belum memenuhi syarat start.");
  return { ok: true };
});

export const submitRaidAction = onCall(async (request) => {
  const uid = requireAuth(request);
  const code = String(request.data?.roomCode ?? "").trim().toUpperCase();
  const action = String(request.data?.action ?? "").trim().toUpperCase();
  if (!RAID_ACTIONS[action]) throw new HttpsError("invalid-argument", "Action raid tidak valid.");
  if (!/^[A-Z2-9]{8}$/.test(code)) throw new HttpsError("invalid-argument", "Kode raid tidak valid.");
  const ref = realtime.ref(`raidRooms/${code}`);
  const tx = await ref.transaction((room) => {
    if (!room || typeof room !== "object") return room;
    const players = raidPlayerMap(room);
    const ids = Object.keys(players).slice(0, RAID_MAX_PLAYERS);
    if (!ids.includes(uid) || String(room.status ?? "") !== "playing") return room;
    const actions = { ...(room.actions ?? {}) };
    if (actions[uid]) return room;
    actions[uid] = { action, createdAt: Date.now() };
    if (Object.keys(actions).length < ids.length) return { ...room, actions, updatedAt: Date.now() };

    const scores = { ...(room.scores ?? {}) };
    let teamPower = 0;
    const uniqueActions = new Set();
    for (const id of ids) {
      const item = actions[id] ?? {};
      const kind = String(item.action ?? "FOCUS");
      uniqueActions.add(kind);
      const contribution = RAID_ACTIONS[kind] ?? 20;
      teamPower += contribution;
      scores[id] = Number(scores[id] ?? 0) + contribution;
    }
    const comboBonus = uniqueActions.size >= 3 ? 28 : uniqueActions.size === 2 ? 12 : 0;
    const round = Number(room.round ?? 1);
    const difficulty = String(room.difficulty ?? "normal");
    const difficultyScale = difficulty === "nightmare" ? 1.15 : difficulty === "hard" ? 1.08 : 1;
    const resolvedPower = Math.max(1, Math.round((teamPower + comboBonus + round * 4) / difficultyScale));
    const bossEnergy = Math.max(0, Number(room.bossEnergy ?? 0) - resolvedPower);
    const nextRound = round + 1;
    if (bossEnergy <= 0) {
      return { ...room, bossEnergy: 0, scores, actions, status: "finished:win", finishedAt: Date.now(), updatedAt: Date.now() };
    }
    if (round >= RAID_MAX_ROUNDS) {
      return { ...room, bossEnergy, scores, actions, status: "finished:timeout", finishedAt: Date.now(), updatedAt: Date.now() };
    }
    return { ...room, bossEnergy, scores, actions: {}, round: nextRound, updatedAt: Date.now() };
  });

  if (!tx.committed) throw new HttpsError("failed-precondition", "Action tidak diterima. Coba lagi.");
  const room = tx.snapshot.val() ?? {};
  return { bossEnergy: Number(room.bossEnergy ?? 0), round: Number(room.round ?? 0), status: String(room.status ?? "") };
});

export const claimRaidReward = onCall(async (request) => {
  const uid = requireAuth(request);
  const code = String(request.data?.roomCode ?? "").trim().toUpperCase();
  if (!/^[A-Z2-9]{8}$/.test(code)) throw new HttpsError("invalid-argument", "Kode raid tidak valid.");
  const roomSnap = await realtime.ref(`raidRooms/${code}`).once("value");
  if (!roomSnap.exists()) throw new HttpsError("not-found", "Room raid tidak ditemukan.");
  const room = roomSnap.val() ?? {};
  const players = raidPlayerMap(room);
  if (!players[uid]) throw new HttpsError("permission-denied", "Kamu bukan anggota room.");
  const status = String(room.status ?? "");
  if (!status.startsWith("finished:")) throw new HttpsError("failed-precondition", "Raid belum selesai.");
  const scores = room.scores ?? {};
  const contribution = Number(scores[uid] ?? 0);
  const outcome = status === "finished:win" ? "winner" : "survivor";
  const reward = Math.min(220, 80 + Math.floor(contribution / 2) + (outcome === "winner" ? 70 : 20));
  const rewardDoc = db.collection("raidRewards").doc(`${code}_${uid}`);
  const walletRef = db.collection("economyWallets").doc(uid);
  const leaderboardRef = db.collection("gameLeaderboards").doc("raid").collection("players").doc(uid);
  const final = await db.runTransaction(async (tx) => {
    const [alreadySnap, walletSnap] = await Promise.all([tx.get(rewardDoc), tx.get(walletRef)]);
    const wallet = walletSnap.data() ?? {};
    const coins = Number(wallet.coins ?? START_COINS);
    if (alreadySnap.exists) return { coins, reward: 0, role: "already_claimed" };
    const nextCoins = coins + reward;
    tx.set(walletRef, { coins: nextCoins, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    tx.set(rewardDoc, { uid, roomCode: code, reward, contribution, role: outcome, createdAt: FieldValue.serverTimestamp() });
    tx.set(leaderboardRef, { displayName: cleanDisplayName(players[uid]?.name), score: FieldValue.increment(reward), wins: FieldValue.increment(outcome === "winner" ? 1 : 0), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    return { coins: nextCoins, reward, role: outcome };
  });
  return final;
});

export const findRaidMatch = onCall(async (request) => {
  const uid = requireAuth(request);
  const mineRef = db.collection("raidMatchQueue").doc(uid);
  const mineSnap = await mineRef.get();
  const mine = mineSnap.data() ?? {};
  if (mine.status === "matched" && mine.roomCode) return { roomCode: mine.roomCode, status: "matched", queued: false };

  const waitingSnap = await db.collection("raidMatchQueue").where("status", "==", "waiting").limit(50).get();
  const now = Date.now();
  const partner = waitingSnap.docs
    .map((d) => ({ ref: d.ref, data: d.data() }))
    .filter((entry) => entry.ref.id !== uid && entry.data.mode === "raid" && Number(entry.data.createdAt ?? 0) > now - 120000)
    .sort((a, b) => Number(a.data.createdAt ?? 0) - Number(b.data.createdAt ?? 0))[0];

  if (!partner) {
    await mineRef.set({ uid, mode: "raid", status: "waiting", displayName: cleanDisplayName(request.data?.displayName), createdAt: now, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    return { roomCode: "", status: "queued", queued: true };
  }

  const roomCode = await uniqueRaidCode();
  let paired = false;
  await db.runTransaction(async (tx) => {
    const [aSnap, bSnap] = await Promise.all([tx.get(mineRef), tx.get(partner.ref)]);
    const a = aSnap.data() ?? {};
    const b = bSnap.data() ?? {};
    if ((a.status && a.status !== "waiting") || b.status !== "waiting") return;
    tx.set(mineRef, { status: "matched", roomCode, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    tx.set(partner.ref, { status: "matched", roomCode, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    paired = true;
  });
  if (!paired) return { roomCode: "", status: "queued", queued: true };

  const p1Name = cleanDisplayName((mine.displayName ?? request.data?.displayName));
  const p2Name = cleanDisplayName((partner.data.displayName ?? "Pemain"));
  await realtime.ref(`raidRooms/${roomCode}`).set({
    game: "raid", ownerUid: partner.ref.id, difficulty: "normal", maxPlayers: RAID_MAX_PLAYERS,
    status: "waiting", round: 0, bossEnergy: 900, actions: {}, scores: {},
    players: {
      [partner.ref.id]: { name: p2Name, ready: true, joinedAt: Date.now() },
      [uid]: { name: p1Name, ready: true, joinedAt: Date.now() }
    }, createdAt: Date.now(), updatedAt: Date.now()
  });
  return { roomCode, status: "matched", queued: false };
});

export const getRaidLeaderboard = onCall(async (request) => {
  requireAuth(request);
  const snap = await db.collection("gameLeaderboards").doc("raid").collection("players").orderBy("score", "desc").limit(25).get();
  return { players: snap.docs.map((doc) => ({ uid: doc.id, ...(doc.data() ?? {}) })) };
});
