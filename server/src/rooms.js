// OverdriveX multiplayer (Phase 12) — in-memory race-room registry + lobby state machine.
//
// Model (see the MP build plan in ARTIFACTS.md): HOST-AUTHORITATIVE. One phone is the basestation —
// it owns every car over BLE and runs the race simulation (RaceEngine). This broker owns the lobby
// ROSTER (join/slot/ready) and RELAYS room messages; it does not simulate the race. The host stays
// authoritative for game decisions (mode/track/start) and for the live race state it broadcasts.
//
// Rooms are ephemeral and live only here in memory; only profiles persist (db.js). Transport-agnostic:
// a "conn" is any object with a unique `.id` and a `.send(string)` method, so this is unit-testable
// without a real socket. ws.js wires @fastify/websocket to it.
//
// Field names track the 3.4.0 `PlayerMessage` / `GameLobbyDef` catalog so the spec stays traceable.

const MAX_PLAYERS = 4;
const MIN_TO_START = 2;
const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // no ambiguous 0/O/1/I

// faithful to 3.4.0 ConnectionType — kept so a player can later be flipped to own-car BLE (federated)
export const ConnectionType = { None: 'NoConnection', Wifi: 'Wifi', VirtualWifi: 'VirtualWifi', BLE: 'BLE' };
export const LobbyState = { Lobby: 'Lobby', Countdown: 'Countdown', Running: 'Running', Results: 'Results' };
export const NO_PLAYER_ID = 254;

function genCode(existing) {
  let code;
  do {
    code = Array.from({ length: 4 }, () => CODE_ALPHABET[Math.floor(Math.random() * CODE_ALPHABET.length)]).join('');
  } while (existing.has(code));
  return code;
}

function emptyPlayer(slotId) {
  return {
    gamePlayerId: NO_PLAYER_ID,
    slotId,
    connId: null,
    displayName: null,
    isHost: false,
    vehicleId: null,                       // car model id this player drives (host BLE-owns the car)
    vehicleConnectionUUID: null,
    connectionType: ConnectionType.None,
    connectionState: 'Disconnected',       // Disconnected | Connecting | Connected
    batteryState: 'Normal',                // Normal | Low | Full | OnCharger
    clientLobbyState: 'Initial',           // Initial | InLobby | LobbyReady | ...
    teamId: -1,
    ready: false,
    result: null,
    emptySlot: true,
    directedTaskEventIds: [],
  };
}

function newRoom(code, opts) {
  return {
    code,                                  // also serves as the gameId
    state: LobbyState.Lobby,
    mode: Number.isInteger(opts.mode) ? opts.mode : 6,  // 6=Race · 1=Battle · 43=KOTH
    roadMapFileName: opts.roadMapFileName ?? null,
    valueToReach: Number.isInteger(opts.valueToReach) ? opts.valueToReach : 3, // laps / score target
    teamSelectionEnabled: !!opts.teamSelectionEnabled,
    slots: Array.from({ length: MAX_PLAYERS }, (_, i) => emptyPlayer(i)),
    hostPlayerId: null,
    nextPlayerId: 0,
    createdAt: Date.now(),
  };
}

export class RoomManager {
  constructor(log = console) {
    this.log = log;
    this.rooms = new Map();   // code -> room
    this.conns = new Map();   // connId -> { conn, code, playerId }
  }

  // ---- transport lifecycle ----
  register(conn) { this.conns.set(conn.id, { conn, code: null, playerId: null }); }
  unregister(conn) { this.leaveRoom(conn); this.conns.delete(conn.id); }

  handle(conn, raw) {
    let msg;
    try { msg = typeof raw === 'string' ? JSON.parse(raw) : raw; }
    catch { return this.send(conn, { t: 'roomError', error: 'bad json' }); }
    try {
      switch (msg?.t) {
        case 'createRoom':                return this.createRoom(conn, msg);
        case 'listRooms':                 return this.listRooms(conn);
        case 'joinRoom':                  return this.joinRoom(conn, msg);
        case 'selectVehicle':             return this.selectVehicle(conn, msg);
        case 'setMode':                   return this.setMode(conn, msg);
        case 'setTrack':                  return this.setTrack(conn, msg);
        case 'setReady':                  return this.setReady(conn, msg);
        case 'startGame':                 return this.startGame(conn);
        case 'raceStarted':               return this.raceStarted(conn);
        case 'control':                   return this.control(conn, msg);
        case 'raceState':                 return this.raceState(conn, msg);
        case 'backToLobby':               return this.backToLobby(conn);
        case 'raceResults':               return this.raceResults(conn, msg);
        case 'emote':                     return this.emote(conn, msg);
        case 'ping':                      return this.send(conn, { t: 'pong', seq: msg.seq, t0: msg.t0, t1: Date.now() });
        case 'leaveRoom':                 return this.leaveRoom(conn);
        default:                          return this.send(conn, { t: 'roomError', error: `unknown message: ${msg?.t}` });
      }
    } catch (e) {
      this.log.error?.(e);
      this.send(conn, { t: 'roomError', error: String(e?.message || e) });
    }
  }

  // ---- lobby management (server is roster authority) ----
  createRoom(conn, msg) {
    this.leaveRoom(conn);
    const code = genCode(this.rooms);
    const room = newRoom(code, msg || {});
    this.rooms.set(code, room);

    const p = room.slots[0];
    p.gamePlayerId = room.nextPlayerId++;
    p.connId = conn.id;
    p.displayName = (msg?.displayName || 'Host').slice(0, 24);
    p.isHost = true;
    p.emptySlot = false;
    p.connectionType = ConnectionType.BLE;   // host owns the cars
    p.connectionState = 'Connected';
    p.clientLobbyState = 'InLobby';
    room.hostPlayerId = p.gamePlayerId;

    const meta = this.conns.get(conn.id);
    if (meta) { meta.code = code; meta.playerId = p.gamePlayerId; }
    this.send(conn, { t: 'roomCreated', code, you: this.publicPlayer(p), lobby: this.lobbySnapshot(room) });
    this.log.info?.(`room ${code} created by "${p.displayName}"`);
  }

  joinRoom(conn, msg) {
    const room = this.rooms.get((msg?.code || '').toUpperCase());
    if (!room) return this.send(conn, { t: 'roomError', error: 'room not found', code: 'NOT_FOUND' });
    if (room.state !== LobbyState.Lobby) return this.send(conn, { t: 'roomError', error: 'game already started', code: 'IN_PROGRESS' });

    this.leaveRoom(conn);
    const slot = room.slots.find((s) => s.emptySlot);
    if (!slot) return this.send(conn, { t: 'roomError', error: 'room full', code: 'FULL' });

    slot.gamePlayerId = room.nextPlayerId++;
    slot.connId = conn.id;
    slot.displayName = (msg?.displayName || `Player ${slot.slotId + 1}`).slice(0, 24);
    slot.isHost = false;
    slot.emptySlot = false;
    slot.connectionType = ConnectionType.Wifi;  // remote controller
    slot.connectionState = 'Connected';
    slot.clientLobbyState = 'InLobby';

    const meta = this.conns.get(conn.id);
    if (meta) { meta.code = room.code; meta.playerId = slot.gamePlayerId; }
    this.send(conn, { t: 'roomJoined', code: room.code, you: this.publicPlayer(slot), lobby: this.lobbySnapshot(room) });
    this.broadcast(room, { t: 'gameLobbyUpdate', lobby: this.lobbySnapshot(room) }, conn.id);
    this.log.info?.(`"${slot.displayName}" joined room ${room.code}`);
  }

  listRooms(conn) {
    const rooms = [...this.rooms.values()].filter((r) => r.state === LobbyState.Lobby).map((r) => this.summary(r));
    this.send(conn, { t: 'roomList', rooms });
  }

  selectVehicle(conn, msg) {
    const { room, player } = this.ctx(conn); if (!room) return;
    player.vehicleId = msg.vehicleId ?? null;
    player.vehicleConnectionUUID = msg.vehicleConnectionUUID ?? null;
    if (msg.connectionType) player.connectionType = msg.connectionType;
    if (msg.batteryState) player.batteryState = msg.batteryState;
    player.connectionState = player.vehicleId != null ? 'Connected' : player.connectionState;
    this.broadcast(room, { t: 'responseVehicleSelect', playerId: player.gamePlayerId, vehicleId: player.vehicleId });
    this.broadcast(room, { t: 'gameLobbyUpdate', lobby: this.lobbySnapshot(room) });
  }

  setReady(conn, msg) {
    const { room, player } = this.ctx(conn); if (!room) return;
    if (room.state !== LobbyState.Lobby) return;
    player.ready = !!msg.ready;
    player.clientLobbyState = player.ready ? 'LobbyReady' : 'InLobby';
    player.directedTaskEventIds = Array.isArray(msg.directedTaskEventIds) ? msg.directedTaskEventIds : [];
    this.broadcast(room, { t: 'lobbySyncPlayerReady', playerId: player.gamePlayerId, ready: player.ready });
    this.broadcast(room, { t: 'gameLobbyUpdate', lobby: this.lobbySnapshot(room) });
    if (this.allReady(room)) this.broadcast(room, { t: 'lobbySyncAllPlayersReady' });
  }

  // ---- host-only game decisions ----
  setMode(conn, msg) {
    const { room } = this.requireHost(conn); if (!room) return;
    if (Number.isInteger(msg.mode)) room.mode = msg.mode;
    this.broadcast(room, { t: 'gameLobbyUpdate', lobby: this.lobbySnapshot(room) });
  }

  setTrack(conn, msg) {
    const { room } = this.requireHost(conn); if (!room) return;
    if (msg.roadMapFileName !== undefined) room.roadMapFileName = msg.roadMapFileName;
    if (Number.isInteger(msg.valueToReach)) room.valueToReach = msg.valueToReach;
    this.broadcast(room, { t: 'gameLobbyUpdate', lobby: this.lobbySnapshot(room) });
  }

  startGame(conn) {
    const { room } = this.requireHost(conn); if (!room) return;
    if (!this.allReady(room)) return this.send(conn, { t: 'roomError', error: 'not all players ready' });
    room.state = LobbyState.Countdown;
    this.broadcast(room, { t: 'lobbySyncAllPlayersReady' });
    this.broadcast(room, { t: 'hostReadyForGameStart' });
    this.broadcast(room, { t: 'gameLobbyStateUpdate', state: room.state });
    this.log.info?.(`room ${room.code} starting`);
  }

  raceStarted(conn) {
    const { room } = this.requireHost(conn); if (!room) return;
    room.state = LobbyState.Running;
    this.broadcast(room, { t: 'gameLobbyStateUpdate', state: room.state });
  }

  backToLobby(conn) {
    const { room } = this.requireHost(conn); if (!room) return;
    room.state = LobbyState.Lobby;
    for (const p of room.slots) { if (!p.emptySlot) { p.ready = false; p.clientLobbyState = 'InLobby'; p.result = null; } }
    this.broadcast(room, { t: 'hostBackToLobby' });
    this.broadcast(room, { t: 'gameLobbyStateUpdate', state: room.state });
    this.broadcast(room, { t: 'gameLobbyUpdate', lobby: this.lobbySnapshot(room) });
  }

  // ---- in-race relays ----
  control(conn, msg) {
    const { room, player } = this.ctx(conn); if (!room) return;
    // client → host only: the host applies this to the player's car in RaceEngine
    this.sendToPlayerId(room, room.hostPlayerId, {
      t: 'control', playerId: player.gamePlayerId,
      throttle: msg.throttle, lane: msg.lane, fireBay: msg.fireBay,
    });
  }

  raceState(conn, msg) {
    const { room, player } = this.ctx(conn); if (!room || !player.isHost) return;
    // host → all clients: authoritative per-tick state for remote HUDs
    this.broadcast(room, { t: 'raceState', tick: msg.tick, cars: msg.cars }, conn.id);
  }

  raceResults(conn, msg) {
    const { room, player } = this.ctx(conn); if (!room || !player.isHost) return;
    room.state = LobbyState.Results;
    const standings = Array.isArray(msg.standings) ? msg.standings : [];
    for (const s of standings) {
      const p = room.slots.find((x) => x.gamePlayerId === s.gamePlayerId);
      if (p) p.result = s;
    }
    this.broadcast(room, { t: 'gameLobbyStateUpdate', state: room.state });
    this.broadcast(room, { t: 'results', standings });
  }

  emote(conn, msg) {
    const { room, player } = this.ctx(conn); if (!room) return;
    this.broadcast(room, { t: 'emote', playerId: player.gamePlayerId, emotion: msg.emotion }, conn.id);
  }

  // ---- leave / disconnect ----
  leaveRoom(conn) {
    const meta = this.conns.get(conn.id);
    if (!meta || !meta.code) return;
    const room = this.rooms.get(meta.code);
    meta.code = null; meta.playerId = null;
    if (!room) return;

    const player = room.slots.find((s) => s.connId === conn.id);
    if (!player) return;
    const leftId = player.gamePlayerId;

    if (player.isHost) {
      // host migration is out of scope (v1) → tearing down the room
      const memberConnIds = room.slots
        .filter((s) => !s.emptySlot && s.connId && s.connId !== conn.id)
        .map((s) => s.connId);
      this.broadcast(room, { t: 'roomClosed', reason: 'host left' }, conn.id);
      for (const cid of memberConnIds) { const m = this.conns.get(cid); if (m) { m.code = null; m.playerId = null; } }
      this.rooms.delete(room.code);
      this.log.info?.(`room ${room.code} closed (host left)`);
      return;
    }

    Object.assign(player, emptyPlayer(player.slotId));
    if (room.slots.every((s) => s.emptySlot)) { this.rooms.delete(room.code); return; }
    if (room.state !== LobbyState.Lobby) this.broadcast(room, { t: 'playerDisconnected', playerId: leftId });
    this.broadcast(room, { t: 'gameLobbyUpdate', lobby: this.lobbySnapshot(room) });
  }

  // ---- helpers ----
  ctx(conn) {
    const meta = this.conns.get(conn.id);
    if (!meta || !meta.code) { this.send(conn, { t: 'roomError', error: 'not in a room' }); return {}; }
    const room = this.rooms.get(meta.code);
    if (!room) { this.send(conn, { t: 'roomError', error: 'room gone' }); return {}; }
    const player = room.slots.find((s) => s.connId === conn.id);
    if (!player) { this.send(conn, { t: 'roomError', error: 'player not found' }); return {}; }
    return { room, player };
  }

  requireHost(conn) {
    const { room, player } = this.ctx(conn);
    if (!room) return {};
    if (!player.isHost) { this.send(conn, { t: 'roomError', error: 'host only' }); return {}; }
    return { room, player };
  }

  allReady(room) {
    const players = room.slots.filter((s) => !s.emptySlot);
    return players.length >= MIN_TO_START && players.every((p) => p.ready);
  }

  publicPlayer(p) {
    return {
      gamePlayerId: p.gamePlayerId, slotId: p.slotId, displayName: p.displayName, isHost: p.isHost,
      vehicleId: p.vehicleId, vehicleConnectionUUID: p.vehicleConnectionUUID, connectionType: p.connectionType,
      connectionState: p.connectionState, batteryState: p.batteryState, clientLobbyState: p.clientLobbyState,
      teamId: p.teamId, ready: p.ready, result: p.result, emptySlot: p.emptySlot,
    };
  }

  lobbySnapshot(room) {
    return {
      code: room.code, gameId: room.code, state: room.state, mode: room.mode,
      roadMapFileName: room.roadMapFileName, valueToReach: room.valueToReach,
      teamSelectionEnabled: room.teamSelectionEnabled, hostPlayerId: room.hostPlayerId,
      maxPlayers: MAX_PLAYERS, players: room.slots.map((p) => this.publicPlayer(p)),
    };
  }

  summary(r) {
    const players = r.slots.filter((s) => !s.emptySlot);
    const host = players.find((p) => p.isHost);
    return {
      code: r.code, hostName: host?.displayName ?? '—', mode: r.mode,
      playerCount: players.length, maxPlayers: MAX_PLAYERS, state: r.state,
    };
  }

  list() { return [...this.rooms.values()].map((r) => this.summary(r)); }

  broadcast(room, obj, exceptConnId = null) {
    for (const s of room.slots) {
      if (s.emptySlot || !s.connId || s.connId === exceptConnId) continue;
      const m = this.conns.get(s.connId);
      if (m?.conn) this.send(m.conn, obj);
    }
  }

  sendToPlayerId(room, playerId, obj) {
    const s = room.slots.find((x) => x.gamePlayerId === playerId);
    if (s?.connId) { const m = this.conns.get(s.connId); if (m?.conn) this.send(m.conn, obj); }
  }

  send(conn, obj) {
    try { conn.send(JSON.stringify(obj)); } catch (e) { this.log.error?.(e); }
  }
}
