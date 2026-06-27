# Overdrive backend (self-hosted revival)

Replaces Anki's dead cloud (`accounts.api.anki.com`, `ankival.api.anki.com`) so the game's
original account + profile-sync works against a server you control — enabling backup/restore,
multiple profiles, multi-device sync, and a DB you can put a dashboard on.

**Stack:** Node + [Fastify](https://fastify.dev/docs/latest/) + [better-sqlite3](https://github.com/WiseLibs/better-sqlite3)
(SQLite). Planned frontend: React + DaisyUI (see Docs). One language end-to-end; Fastify gives
JSON-schema validation + OpenAPI docs (the reason we chose it over splitting into Python/FastAPI).

## Run

```bash
cd server
npm install
npm start          # listens on 0.0.0.0:8080 (override with PORT / HOST / DB_PATH)
```

## Capture-first workflow (Phase A2 → A3)

This server starts as a **capture tool**: it logs every request the app makes (the real wire
protocol is not publicly documented — we reverse it by observation).

1. Patch `HttpAdapter.startRequest` to redirect `*.api.anki.com` here — see
   `../patches/httpadapter-capture-redirect.md`. Rebuild/sign/install the APK (PLAN.md Phase 3).
2. Drive the app's create-account / login / play / sync flows on the Tab S10+.
3. `npm run capture:dump` — inspect captured method/path/headers/body.
4. Flesh out the `/accounts/1/*` and `/ankival/1/*` routes in `src/server.js` to match what was
   captured (current routes are best-guess placeholders from the Ghidra/CLAD field names).

Routing model: the redirect maps `https://accounts.api.anki.com/1/…` → `http://<server>/accounts/1/…`
and `ankival.…` → `/ankival/1/…`. The catch-all logs anything not yet modeled and returns `200`.

## Multiplayer race-room (Phase 12)

A `WebSocket` broker for local-Wi-Fi multiplayer, **host-authoritative**: one phone is the
basestation (owns all cars over BLE + runs the race in `RaceEngine`); this server owns the lobby
roster and relays room messages. Rooms are in-memory (ephemeral); only profiles persist. Full
design + the RE provenance (3.4.0 `GameLobbyDef` / 2.6 Mongoose `HostGameLobby`) is in the
[multiplayer build plan](../ARTIFACTS.md). Code: `src/rooms.js`, wired in `src/server.js`.

- **`GET /ws/room`** — the race-room socket. JSON messages, each tagged with `t`.
- **`GET /api/v1/rooms`** — debug: list active lobbies over plain HTTP.

Message tags (faithful subset of 3.4.0's `PlayerMessage`): `createRoom` · `listRooms` · `joinRoom`
· `selectVehicle` · `setMode` · `setTrack` · `setReady` · `startGame` · `raceStarted` · `control`
(client→host) · `raceState` (host→all) · `backToLobby` · `raceResults` · `emote` · `ping`. Server
emits `roomCreated` / `roomJoined` / `roomList` / `gameLobbyUpdate` / `gameLobbyStateUpdate` /
`lobbySyncPlayerReady` / `lobbySyncAllPlayersReady` / `hostReadyForGameStart` / `results` /
`playerDisconnected` / `roomClosed` / `roomError`.

**Two-device test:** start the server, then point each device's `BackendClient.baseUrl` at the
Mac's LAN IP (Settings → Dev). The host device may instead use `adb reverse tcp:8080 tcp:8080`
(→ `127.0.0.1:8080`); remote devices must use the LAN IP. Smoke test the broker headless with
`scratchpad/test-rooms.mjs` (copy into `server/` to resolve `ws`).

## Schema

`accounts`, `sessions`, `profile_kv` (the ankival KV blob store, etag-versioned), and
`capture_log`. See `schema.sql`. SQLite now; swap to Postgres later for concurrent multi-device.

## Docs to consult

- Fastify: https://fastify.dev/docs/latest/ • Swagger/OpenAPI plugin: https://github.com/fastify/fastify-swagger
- better-sqlite3: https://github.com/WiseLibs/better-sqlite3 (synchronous, fast, zero codegen)
- React: https://react.dev • DaisyUI: https://daisyui.com/docs • Tailwind: https://tailwindcss.com/docs
- Upgrade paths if wanted: Prisma ORM (migrations + the built-in **Prisma Studio** DB GUI — a
  free interim dashboard) https://www.prisma.io/docs ; Postgres https://www.postgresql.org/docs/
- Reversing context (this repo): `../BACKEND-REVIVAL.md`, `../re/libDriveEngine_api_extract.md`.
- The account/profile API itself has **no public spec** — our source of truth is the Ghidra
  extract + the live capture above. (Existing Anki-Overdrive projects cover only the BLE car
  protocol, not the cloud backend.)
