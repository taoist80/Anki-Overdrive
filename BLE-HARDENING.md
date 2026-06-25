# BLE Hardening Plan — stable multi-car (4×) connections

Goal: root-cause the mid-run BLE drops in **DDL Overdrive 2.6.10** on the **Tab S10+**
(Android 16, MediaTek/Samsung BT stack) and harden the link to hold **4 cars simultaneously**
for a full session with no unexpected disconnects.

## Evidence so far (from `openplay_capture.log`, run 11:08–11:11)
- Ran ~3 min, then a car (`…6f:e0`) dropped — **intermittent, not immediate** (points to a
  timeout/scheduling cause, not a hard incompatibility).
- System-BT-stack errors around the drop:
  - `bta_gattc_mark_bg_conn: unable to find the bg connection mask for bd_addr=…6f:e0`
    → app is using a **background/auto connection** path the stack lost track of.
  - `bluetooth-asha … OnConnectionUpdateComplete: unknown device` (throughout the run)
    → **connection-parameter-update churn** the stack mishandles.
  - After the drop: floods of `indication/notif for unknown device` + `Ignore unknown conn ID`
    → the car kept notifying after the GATT link was gone.
- "Cars won't stay on track" is a **symptom** of the drop (no link → no steering/speed commands).
- **Follow-up run (fewer cars):** completed a full Open Play (weapon/XP/loot) but **dropped once**,
  recovered only by **manual pause/resume** (forces a fresh `connectGatt`). → drops persist even at
  low car count (so it's marginal, not only a 4-car scheduling cliff), the reconnect path works but
  is **manual** (hardening must add **automatic reconnect on unexpected disconnect**), and fewer
  cars = far more stable (consistent with the fixed-30 ms-interval root cause).
- **Lab result (2026-06-25):** our app held **4 cars @ BALANCED (~30 ms), 0 drops in 2 min**
  (offline, light load). → **Bare 4-car scheduling at 30 ms is NOT the cause.** Drops are induced
  by **real-run activity** (user's call). This *revises* H2: 30 ms isn't an overload by itself, but
  leaves **no margin under load**. New leading suspects:
  - **H6 — WiFi/BT 2.4 GHz coexistence.** The game hammers dead servers (test-rams, GameAnalytics,
    Amplitude…) over WiFi, constantly retrying → radio contention starves BLE → supervision
    timeout. Our lab makes **no** network calls → no contention → no drops. **Cheapest test: run
    the real game with WiFi OFF (BT on).** If drops vanish → coexistence/network.
  - **H7 — on-track notification throughput** (4 cars × ~10–30 Hz position updates) + commands,
    absent if the lab cars weren't on a track.
  - **H8 — Unity CPU/GPU stalls** (the "16 GB memory" warning; heavy render/engine load) hitching
    the BLE servicing.

## Key enabler: the BLE layer is patchable Java/smali
The game's GATT connection management lives in Java (not native), so we have levers:
- `com.anki.bluetooth.le.LeService` — `connectGatt`, scan, notifications, GATT ops.
- **`com.anki.bluetooth.le.AnkiVehicleConnectionIntervalChanger`** — dedicated conn-interval control
  (the #1 multi-peripheral stability lever).
- `AnkiVehicleFlasher` (firmware OTA), `LePeripheral`, `LeServiceCallback`, `GattAttributes`.
- Native `libDriveEngine` does the *protocol/commands* over JNI; Android GATT is in Java.

## Phase 2 findings (decompiled smali — control points confirmed)
- `LeService` → `connectGatt(ctx, autoConnect=false, cb)` (`const/4 v9, 0x0`): **direct connect,
  not background** → **H1 ruled out** (`mark_bg_conn` was a teardown artifact).
- Connections are **serialized** via `LeService$ConnectQueue` + `ConnectionAttempt` (with retry/
  timeout) → H3 unlikely at connect time.
- **`AnkiVehicleConnectionIntervalChanger`** is held per-`LeService` (`mConnectionIntervalChanger`),
  runs on connect, and writes params to the car via `makeChangeConnectionParamsMessage()` →
  `LeService.writeCharacteristic(...)`. **Default params applied to EVERY car:**
  `connIntervalMin = max = 0x18 (24 → 30 ms)`, `slaveLatency = 0`, `timeoutMult = 0x2bc (700)`.
  No `requestConnectionPriority` anywhere (Anki tunes via the car, in-protocol).
- **→ Leading root cause (H2):** a fixed **30 ms** interval per car × 4 cars overcommits the
  controller's scheduling → missed connection events → ~7 s supervision timeout → drop.
  **Patch lever:** the default consts in the `AnkiVehicleConnectionIntervalChanger` constructor
  (`0x18 / 0x18 / 0x0 / 0x2bc`) + the static `DEFAULT_CONNECTION_INTERVAL_MIN/MAX` — widen the
  interval and/or add slave latency for multi-car. Exact value to be set from Phase 1 snoop +
  Phase 3 lab.

## Root-cause hypotheses (confirm with data before fixing)
- **H1 — autoConnect/background connections.** `connectGatt(autoConnect=true)` uses the stack's
  background-connection path (the `mark_bg_conn` error); fragile and poor with multiple devices.
  *Fix:* direct connect (`autoConnect=false`) + app-managed reconnect.
- **H2 — connection interval too aggressive for N cars.** The controller can't fit 4 connection
  events per interval → missed events → **supervision timeout (HCI 0x08)**. *Fix:* widen/space
  the interval via `AnkiVehicleConnectionIntervalChanger`; pick a value that holds 4 links.
- **H3 — concurrent GATT operations** across multiple `LeService`/gatt instances → stack races on
  this controller. *Fix:* a single global serialized GATT op queue (one op in flight at a time).
- **H4 — old car firmware** negotiates params the modern stack handles badly. *Fix:* request
  better params from the central, or update firmware via `AnkiVehicleFlasher` (last resort).
- **H5 — notification throughput** (4 cars × position updates + outbound commands) saturates
  connection events. *Fix:* pace command resends; ensure interval accommodates throughput.

## Plan

### Phase 1 — Instrument & reproduce (ground truth)
**btsnoop is NOT retrievable on this device** (2026-06-25): `/data/misc/bluetooth/logs/` is
root-only, and Samsung's `adb bugreport` does **not** package the snoop log (verified — the zip
has dumpstate + kernel logs only). Device is non-rooted, app non-debuggable. So we get HCI-level
truth a different way:

- **Our own Kotlin app as the instrument** (no root needed): `BluetoothGattCallback.
  onConnectionStateChange(gatt, status, newState)` surfaces the disconnect **status = the HCI
  reason** — `8` = `GATT_CONN_TIMEOUT` (supervision timeout / link loss, = HCI 0x08), `0x13`
  remote-terminated, `0x16` local, `0x3E` fail-to-establish. That confirms *why* it drops.
- **Reproduce + experiment in-app:** connect **1→2→4 cars**, run the game-like load (notify +
  control loop), and sweep **`requestConnectionPriority`** (HIGH ≈ 11 ms, BALANCED ≈ 30 ms,
  LOW_POWER ≈ 100+ ms) to see which interval holds 4 cars. If LOW_POWER (wider interval) holds
  where BALANCED (≈ the game's 30 ms) drops with `status=8`, that **confirms the interval root
  cause and the fix direction** — without any root/snoop.

(Optional, only if we ever need raw HCI: a device-UI **full** bug report may include btsnoop; or
sweep the exact Anki interval via the car's connection-param characteristic, replicating
`AnkiVehicleConnectionIntervalChanger.makeChangeConnectionParamsMessage`.)

### Phase 2 — Decompile & map the control points  [device-independent, in progress]
1. apktool-decompile the DDL APK (running).
2. Read `LeService`: the `connectGatt(...)` call (autoConnect flag), scan settings, GATT-op
   sequencing, notification subscribe, reconnect handling.
3. Read `AnkiVehicleConnectionIntervalChanger`: how/when it sets the interval and to what values.
4. Note every lever we can patch + safe default values.

### Phase 3 — BLE lab in our own app (fast iteration)  [device, our Kotlin app]
Use `android/` (OverdriveX) as a controlled test bench — we own every BLE parameter — to
**empirically find the params that hold 4 cars**: autoConnect false, sequential connects, global
GATT op queue, `requestConnectionPriority`/interval sweeps, paced control loop, auto-reconnect.
Faster than rebuilding the game per experiment; results feed Phase 4.

### Phase 4 — Patch & harden the game  [smali → rebuild → sign → install]
Apply the validated params to `LeService`/`AnkiVehicleConnectionIntervalChanger` smali:
`autoConnect=false`, stable multi-car interval, serialized GATT ops, robust reconnect. Rebuild,
sign, install (Phase 3 of PLAN.md), re-test.

### Phase 5 — Validate
Extended **4-car** run; confirm via btsnoop that there are **no unexpected HCI disconnects** and
gameplay is stable. Iterate params on the data.

## Notes / risks
- Tuning is empirical — the right interval depends on this controller + firmware; the btsnoop data
  drives it.
- Firmware update (H4) is the riskiest lever — reserve for last, only if data points to it.
- We control our app's BLE fully (Phase 3); the game's is patchable but needs rebuild/sign cycles.
