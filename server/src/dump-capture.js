// Pretty-print the captured requests so we can reverse the exact wire protocol.
// Usage: npm run capture:dump
import { db } from './db.js';

const rows = db.prepare('SELECT * FROM capture_log ORDER BY id').all();
if (rows.length === 0) {
  console.log('No captures yet. Point the app here (HttpAdapter redirect) and drive the login/sync flows.');
  process.exit(0);
}
for (const r of rows) {
  console.log('\n=== #%d  %s  %s  %s', r.id, r.ts, r.method, r.path);
  try {
    const h = JSON.parse(r.headers);
    const interesting = ['authorization', 'anki-app-key', 'content-type', 'if-match', 'etag'];
    for (const k of interesting) if (h[k]) console.log('  %s: %s', k, h[k]);
  } catch {}
  if (r.body) console.log('  body: %s', r.body.length > 2000 ? r.body.slice(0, 2000) + '…' : r.body);
}
console.log('\n%d requests captured.', rows.length);
