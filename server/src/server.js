// Self-hosted Anki Overdrive backend — capture-first scaffold.
//
// Phase A2 (capture): point the app here (via the HttpAdapter redirect patch). Every request is
//   logged to the capture_log table + console. Then refine the modeled routes below to match.
// Phase A3 (emulate): flesh out /accounts and /ankival from the captured shapes.
//
// Endpoint names mirror the native ServerConfig map: accounts, ankival, store, itemshop,
// virtualrewards. We mount under a per-service path prefix (the redirect maps
//   https://accounts.api.anki.com/1/...  ->  http://<server>/accounts/1/...).

import Fastify from 'fastify';
import { randomUUID, createHash } from 'node:crypto';
import { db } from './db.js';

const HOST = process.env.HOST || '0.0.0.0';
const PORT = Number(process.env.PORT || 8080);

const app = Fastify({ logger: true, bodyLimit: 8 * 1024 * 1024 });

// Capture raw bodies for ANY content type (JSON, form, or binary CLAD blobs).
app.addContentTypeParser('*', { parseAs: 'string' }, (_req, body, done) => done(null, body));

const insertCapture = db.prepare(
  'INSERT INTO capture_log (method, path, headers, body) VALUES (?,?,?,?)'
);

// Log every request — this is the capture mechanism.
app.addHook('preHandler', async (req) => {
  try {
    const body = typeof req.body === 'string' ? req.body : JSON.stringify(req.body ?? null);
    insertCapture.run(req.method, req.url, JSON.stringify(req.headers), body);
    req.log.info({ anki: true, method: req.method, url: req.url, body }, 'CAPTURE');
  } catch (e) {
    req.log.warn(e, 'capture failed');
  }
});

// ---------- helpers ----------
const etagOf = (s) => createHash('sha1').update(s).digest('hex');
const newToken = () => randomUUID().replace(/-/g, '');

// AccountUpdatedCallback fields (from CLAD). Exact JSON keys TBD from capture.
function accountView(a, token) {
  return {
    user_id: a.user_id,
    profile_id: a.profile_id,
    username: a.username,
    email: a.email,
    email_verified: !!a.email_verified,
    status: a.status,
    date_of_birth: a.date_of_birth,
    session_token: token ?? null,
    is_app_out_of_date: false,
  };
}

// ---------- ACCOUNTS service (accounts.api.anki.com/1/) ----------
// NOTE: paths + payloads are placeholders until capture confirms them.
app.post('/accounts/1/accounts', async (req, reply) => {
  const b = safeJson(req.body) ?? {};
  const userId = randomUUID();
  const profileId = randomUUID();
  db.prepare(
    `INSERT INTO accounts (user_id, profile_id, username, email, password_hash, status, date_of_birth)
     VALUES (?,?,?,?,?, 'active', ?)`
  ).run(userId, profileId, b.username ?? null, b.email ?? null, b.password ? etagOf(b.password) : null, b.dob ?? null);
  const token = newToken();
  db.prepare('INSERT INTO sessions (token, user_id) VALUES (?,?)').run(token, userId);
  return reply.code(201).send(accountView(db.prepare('SELECT * FROM accounts WHERE user_id=?').get(userId), token));
});

app.post('/accounts/1/sessions', async (req, reply) => {
  // login -> issue session token. Refine matching logic after capture.
  const b = safeJson(req.body) ?? {};
  const a = db.prepare('SELECT * FROM accounts WHERE username=? OR email=?').get(b.username ?? '', b.username ?? '');
  if (!a) return reply.code(401).send({ error: 'no_such_account' });
  const token = newToken();
  db.prepare('INSERT INTO sessions (token, user_id) VALUES (?,?)').run(token, a.user_id);
  return accountView(a, token);
});

app.get('/accounts/1/accounts/:id', async (req) => {
  const a = db.prepare('SELECT * FROM accounts WHERE user_id=? OR profile_id=?').get(req.params.id, req.params.id);
  return a ? accountView(a) : {};
});

// ---------- ANKIVAL key-value profile store (ankival.api.anki.com/1/) ----------
app.get('/ankival/1/*', async (req) => {
  const userId = userFromAuth(req);
  const key = req.params['*'];
  const row = db.prepare('SELECT value, etag, version FROM profile_kv WHERE user_id=? AND key=?').get(userId, key);
  return row ?? {};
});

app.put('/ankival/1/*', async (req, reply) => {
  const userId = userFromAuth(req);
  const key = req.params['*'];
  const value = typeof req.body === 'string' ? req.body : JSON.stringify(req.body);
  const etag = etagOf(value);
  db.prepare(
    `INSERT INTO profile_kv (user_id, key, value, etag, version) VALUES (?,?,?,?,1)
     ON CONFLICT(user_id, key) DO UPDATE SET value=excluded.value, etag=excluded.etag,
       version=profile_kv.version+1, updated_at=datetime('now')`
  ).run(userId, key, value, etag);
  return reply.header('etag', etag).code(200).send({ etag });
});

// ---------- catch-all: capture anything we haven't modeled yet, return 200 ----------
app.setNotFoundHandler((req, reply) => {
  req.log.warn({ url: req.url }, 'UNMODELED endpoint — captured; add a route after inspecting capture_log');
  reply.code(200).send({});
});

function safeJson(s) { try { return typeof s === 'string' ? JSON.parse(s) : s; } catch { return null; } }
function userFromAuth(req) {
  const tok = (req.headers.authorization || '').replace(/^Bearer\s+/i, '').trim();
  const s = tok && db.prepare('SELECT user_id FROM sessions WHERE token=?').get(tok);
  return s ? s.user_id : 'anonymous';
}

app.listen({ host: HOST, port: PORT })
  .then(() => app.log.info(`Overdrive backend listening on http://${HOST}:${PORT}`))
  .catch((e) => { app.log.error(e); process.exit(1); });
