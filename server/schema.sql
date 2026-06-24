-- Anki Overdrive backend (revived) — minimal schema.
-- Field names/shapes are BEST-GUESS from Ghidra (CLAD AccountUpdatedCallback fields) and
-- WILL be refined once Phase A2 live-capture reveals the exact request/response JSON.

PRAGMA journal_mode = WAL;

CREATE TABLE IF NOT EXISTS accounts (
  user_id        TEXT PRIMARY KEY,
  profile_id     TEXT UNIQUE,
  username       TEXT UNIQUE,
  email          TEXT,
  password_hash  TEXT,
  status         TEXT DEFAULT 'active',   -- active | guest | deleted | purged
  email_verified INTEGER DEFAULT 1,
  date_of_birth  TEXT,
  created_at     TEXT DEFAULT (datetime('now')),
  updated_at     TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS sessions (
  token       TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL REFERENCES accounts(user_id),
  created_at  TEXT DEFAULT (datetime('now')),
  expires_at  TEXT
);

-- Profile game data lives in the `ankival` key-value store: one versioned blob per
-- (account, key). Native sends JSON (boost ptree), version-stamped + hashed (etag) for
-- the conflict-resolution path (overrideLocal / overrideOnline).
CREATE TABLE IF NOT EXISTS profile_kv (
  user_id    TEXT NOT NULL REFERENCES accounts(user_id),
  key        TEXT NOT NULL,   -- e.g. app.user_profile_mapping , $profile_id
  value      TEXT NOT NULL,   -- JSON profile blob
  etag       TEXT NOT NULL,   -- hash/version used for conflict detection
  version    INTEGER DEFAULT 1,
  updated_at TEXT DEFAULT (datetime('now')),
  PRIMARY KEY (user_id, key)
);

-- Raw capture of every request the app makes — the source of truth for reversing the
-- exact wire protocol during Phase A2. Inspect with `npm run capture:dump`.
CREATE TABLE IF NOT EXISTS capture_log (
  id      INTEGER PRIMARY KEY AUTOINCREMENT,
  ts      TEXT DEFAULT (datetime('now')),
  method  TEXT,
  path    TEXT,
  headers TEXT,   -- JSON
  body    TEXT
);
