// OverdriveX backend — clean custom API (we own client + server, so no Anki-protocol emulation).
//
// Responsibilities: accounts (signup/login/session), profile sync (the authoritative store of
// progression — coins/xp/stars/inventory/upgrades), and a store catalog. The reward economy runs
// client-side; the backend persists + syncs the profile blob and resolves auth.
//
// Run: npm start  (PORT env, default 8080). Reach from the tablet via `adb reverse tcp:8080 tcp:8080`.
// The earlier Anki-protocol capture scaffold lives in git history (pre-Phase-4).

import Fastify from 'fastify';
import websocket from '@fastify/websocket';
import { randomUUID, randomBytes, scryptSync, timingSafeEqual, createHash } from 'node:crypto';
import { db } from './db.js';
import { RoomManager } from './rooms.js';

const HOST = process.env.HOST || '0.0.0.0';
const PORT = Number(process.env.PORT || 8080);

const app = Fastify({ logger: true, bodyLimit: 4 * 1024 * 1024 });
await app.register(websocket);

// Multiplayer (Phase 12) — ephemeral race-room broker. See rooms.js + the MP build plan (ARTIFACTS.md).
const rooms = new RoomManager(app.log);

// ---------- helpers ----------
const newId = () => randomUUID();
const newToken = () => randomBytes(24).toString('hex');

function hashPassword(password) {
  const salt = randomBytes(16).toString('hex');
  return `${salt}:${scryptSync(password, salt, 32).toString('hex')}`;
}
function verifyPassword(password, stored) {
  const [salt, hash] = (stored || '').split(':');
  if (!salt || !hash) return false;
  const check = scryptSync(password, salt, 32).toString('hex');
  const a = Buffer.from(hash), b = Buffer.from(check);
  return a.length === b.length && timingSafeEqual(a, b);
}

const DEFAULT_PROFILE = { driverName: 'Driver 01', coins: 0, xp: 0, missions: {} };

function authUser(req, reply) {
  const h = req.headers.authorization || '';
  const token = h.startsWith('Bearer ') ? h.slice(7) : null;
  if (!token) { reply.code(401).send({ error: 'missing bearer token' }); return null; }
  const s = db.prepare('SELECT user_id FROM sessions WHERE token = ?').get(token);
  if (!s) { reply.code(401).send({ error: 'invalid token' }); return null; }
  return s.user_id;
}

function readProfile(userId) {
  const row = db.prepare('SELECT value, version FROM profile_kv WHERE user_id = ? AND key = ?')
    .get(userId, 'profile');
  return row ? { profile: JSON.parse(row.value), version: row.version }
             : { profile: DEFAULT_PROFILE, version: 0 };
}
const writeProfile = db.prepare(`
  INSERT INTO profile_kv (user_id, key, value, etag, version, updated_at)
  VALUES (@userId, 'profile', @value, @etag, @version, datetime('now'))
  ON CONFLICT(user_id, key) DO UPDATE SET
    value = excluded.value, etag = excluded.etag, version = excluded.version, updated_at = excluded.updated_at
`);
function saveProfile(userId, profile) {
  const cur = db.prepare('SELECT version FROM profile_kv WHERE user_id = ? AND key = ?').get(userId, 'profile');
  const version = (cur?.version || 0) + 1;
  const value = JSON.stringify(profile);
  writeProfile.run({ userId, value, etag: createHash('sha1').update(value).digest('hex'), version });
  return version;
}

// ---------- routes ----------
app.get('/health', async () => ({ ok: true, service: 'overdrivex-backend', time: new Date().toISOString() }));

app.post('/api/v1/signup', async (req, reply) => {
  const { username, password, email, driverName } = req.body || {};
  if (!username || !password) return reply.code(400).send({ error: 'username and password required' });
  if (db.prepare('SELECT 1 FROM accounts WHERE username = ?').get(username))
    return reply.code(409).send({ error: 'username taken' });

  const userId = newId();
  db.prepare('INSERT INTO accounts (user_id, profile_id, username, email, password_hash) VALUES (?,?,?,?,?)')
    .run(userId, newId(), username, email || null, hashPassword(password));
  const profile = { ...DEFAULT_PROFILE, driverName: driverName || username };
  const version = saveProfile(userId, profile);
  const token = newToken();
  db.prepare('INSERT INTO sessions (token, user_id) VALUES (?, ?)').run(token, userId);
  return { userId, token, profile, version };
});

app.post('/api/v1/login', async (req, reply) => {
  const { username, password } = req.body || {};
  const acct = db.prepare('SELECT * FROM accounts WHERE username = ?').get(username);
  if (!acct || !verifyPassword(password, acct.password_hash))
    return reply.code(401).send({ error: 'invalid credentials' });
  const token = newToken();
  db.prepare('INSERT INTO sessions (token, user_id) VALUES (?, ?)').run(token, acct.user_id);
  const { profile, version } = readProfile(acct.user_id);
  return { userId: acct.user_id, token, profile, version };
});

app.get('/api/v1/profile', async (req, reply) => {
  const userId = authUser(req, reply); if (!userId) return;
  return readProfile(userId);
});

// Sync the local profile up. Last-write-wins; returns the stored version.
app.put('/api/v1/profile', async (req, reply) => {
  const userId = authUser(req, reply); if (!userId) return;
  const profile = req.body?.profile;
  if (!profile || typeof profile !== 'object') return reply.code(400).send({ error: 'profile object required' });
  const version = saveProfile(userId, profile);
  return { profile, version };
});

// Store catalog — coin packs (free in local emulation).
const STORE_ITEMS = [
  { id: 'coins_1k', kind: 'coins', name: '1,000 Coins', price: 0, grantsCoins: 1000 },
  { id: 'coins_5k', kind: 'coins', name: '5,000 Coins', price: 0, grantsCoins: 5000 },
  { id: 'coins_15k', kind: 'coins', name: '15,000 Coins', price: 0, grantsCoins: 15000 },
];
app.get('/api/v1/store', async () => ({ items: STORE_ITEMS }));

// ---------- multiplayer ----------
// Debug: list active lobbies over plain HTTP (the device discovers rooms over WS via `listRooms`).
app.get('/api/v1/rooms', async () => ({ rooms: rooms.list() }));

// The race-room socket. Each connection is wrapped as a transport-agnostic `conn` for RoomManager.
app.get('/ws/room', { websocket: true }, (sockOrConn) => {
  const socket = sockOrConn.socket ?? sockOrConn; // @fastify/websocket v11 passes the raw socket; older wraps it
  const conn = { id: randomUUID(), send: (s) => { if (socket.readyState === 1) socket.send(s); } };
  rooms.register(conn);
  socket.on('message', (data) => rooms.handle(conn, data.toString()));
  socket.on('close', () => rooms.unregister(conn));
  socket.on('error', () => rooms.unregister(conn));
});

app.listen({ host: HOST, port: PORT })
  .then(() => app.log.info(`OverdriveX backend on http://${HOST}:${PORT}`))
  .catch((e) => { app.log.error(e); process.exit(1); });
