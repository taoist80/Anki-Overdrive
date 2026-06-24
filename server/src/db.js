import Database from 'better-sqlite3';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const dbPath = process.env.DB_PATH || join(here, '..', 'overdrive.db');

export const db = new Database(dbPath);
db.pragma('journal_mode = WAL');
db.exec(readFileSync(join(here, '..', 'schema.sql'), 'utf8'));
