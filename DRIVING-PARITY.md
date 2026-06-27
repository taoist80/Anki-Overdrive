# Driving-logic parity — our RaceEngine vs 4.0.4 (and 2.6)

Reverse-engineered from the **4.0.4 Godot project** (GDRE-recovered GDScript — the proven driving logic
lives in `Globals/CarManager.gd`, `Globals/Car.gd`, `Bots/Race.gd`). 2.6 is IL2CPP (logic not readable;
only its basestation config JSON is). The 4.0.4 BLE wire format lives in a native `VehicleDelegateManager`
superclass, but the *control logic* — when/how it sets speed, handles curves, wrong-way, delocalization —
is all in GDScript and is the reference below.

## Divergences found (cause → fix)

| # | 4.0.4 (proven) | Ours (was) | Effect | Fix landed |
|---|---|---|---|---|
| 1 | `setSpeed(s, a, **true**)` everywhere in-race — the 3rd byte is `respectRoadPieceSpeedLimit`; firmware auto-slows for the scanned track's per-piece limits (curves/jumps) | `setSpeed` 3rd byte hardcoded **0** | nothing capped curves → fishtail / fly off / **disconnect (link lost)** | `Protocol.setSpeed(..., respectLimits=true)` default; 3rd byte = 1 |
| 2 | `turn180()` **only** when crossing the **start/finish piece (33/34) in reverse**, or when the map's expected reverse ≠ actual | we issued a u-turn on **every** position with the 0x40 flag set | spurious flips of correctly-driving cars → **"both cars backwards, never righting"** | u-turn gated to piece 33/34 + reverse bit, debounced 3s |
| 3 | bots `setSpeed(**1000**, **700**, true)`; in-race accel **700** | AI 360–520, accel 1800 | too slow + too-aggressive accel | `AI_BASE=800` (×tier), `ACCEL=700` |
| 4 | on **delocalization** the car keeps **driving** (`setSpeed(700,…)`) to re-find the track | we **stop** the car (`drive 0`) | a nudged-off car can't recover | *(noted — left as stop for now; revisit)* |
| 5 | uploads the full **road network + per-piece `speed_limit`** (`SendRoadNetwork`) so the firmware knows curve limits | not sent | #1's firmware curve-handling is weaker without it | *(gap — see below)* |
| 6 | reconnect: BLE is native; cars re-advertise only after a **charger** reseat (matches `GET TO DA CHARGA!`) | `autoConnect=true` (works, ~19s); modal now says reseat on charger | — | done earlier |

## Landed
- **`respectRoadPieceSpeedLimit=true`** on every `setSpeed`.
- **Wrong-way U-turn** rewritten to the 4.0.4 start-piece-reverse trigger (kills the spurious-flip chaos).
- **Speeds/accel matched** to the proven values (AI base 800 ×tier, accel 700). Manual curve cap kept as a backstop.
- **Localization override OFF (headline):** `sdkMode` no longer sets `OVERRIDE_LOCALIZATION`. With it on, the
  car waited for host position overrides we never send — disabling its own localization + per-piece curve
  limiting (the root of fly-offs / wrong-way / no-recovery). Now the firmware localizes the scanned track
  and limits curves itself, which is `RoadNetwork::InitTrackMaxSpeedFromPieces` realized on the car — so
  "step 2 (per-piece speed limits)" is delivered without a separate road-network upload. (2.6 overrides only
  because it runs the full planner; we don't, so we let the firmware drive.)
- **2.6 AI driver logic ported** ([DriverProfile.kt]): each commander's `vehicle_setup`+tier maps to 2.6's
  `ai_trait_configuration` — **aggressive** trait (lazy/low/hostile/tactical/ultra) → real item-use cadence
  (9s…1.25s) + follow-distance, **speedy** trait (autopilot/novice/relaxed/normal/hyper) → race speed.
  Combat's AI fire now uses the trait cadence. All 27 commanders drive/fight distinctly (15 archetypes).

## Known remaining gaps (next, if still unstable)
- **Road-network upload** (#5): we localize via SDK *override* (`sdkMode` option 0x01) and never send the
  scanned network + speed limits. If `respectLimits` proves weak under override localization, port
  `SendRoadNetwork` (addRoadPiece id + speed_limit per piece) — or stop overriding localization and let the
  firmware drive the scanned track itself (closer to 4.0.4).
- **Delocalization recovery** (#4): consider driving (not stopping) to re-localize.
- **Offset/lane correction**: 4.0.4 corrects the reported offset against the lane definition
  (`CarManager._position_update` ~L477–494) — we don't.

## Binary RE of the 2.6 build (libDriveEngine.so + libil2cpp.so)

The 2.6 APK ships the real engine as native libs (not stripped) plus the Unity C# layer:
- **`libDriveEngine.so`** — Anki's C++ basestation engine, **35,690 symbols, NOT stripped**. The phone runs
  a full **cost-based local planner** (`BaseStation::LocalPlanner::Planner`) over a `RoadNetwork`:
  - **Curve safety = per-piece speed limits**: `RoadNetwork::InitTrackMaxSpeedFromPieces()`,
    `LocalPlanner::InfoTable::MaxSpeedIdxUnderLimit(float, VehicleChassisStyle)`,
    `PlannerActionFilter::SetMaxSpeed()`. → confirms `respectRoadPieceSpeedLimit` is the right lever, and
    that real curve handling needs the per-piece limits loaded.
  - **Wrong-way**: `DirtyTireDetector::Detected_ReverseDriving()` (native detection),
    `SdkDrivingScript::Turn180(uint, bool)` + `SdkTurn180Command` → confirms our 0x32 U-turn is correct
    (the bool is `isJumpPiece` → use UTURN vs UTURN_JUMP; immediate trigger).
  - Config that drives it (readable, extracted): `aic_parameters.json` (`doCurveSpeedLimiting:true`,
    `curveLimitFrictionConstant:0.87`, `minLanesFor180:15`), `aic_commander_planner.json` (AI plan-modes:
    racing lanes at 0.34/0.6/0.8 m/s + battle positioning), `baseStationParams.cfg`
    (`doAutoDrivingDirectionCorrection:true`, accel 1.0 m/s², discovery speed 0.4–0.7 m/s, plan recompute 0.25s).
- **`libil2cpp.so`** — dumped with Il2CppDumper (`dump.cs`, 228k lines). The C# layer is orchestration:
  `class DriveEngine`/`DriveEngineContext` (wrapper over the native engine), `Commander`/`CommanderType`,
  `AiVehicleSpeedUpdate`, `ConnectVehicleForSlot`/`DisconnectVehicleForSlot`, `SendTurn180Command(carId, isJumpPiece)`.

**Architectural takeaway:** 2.6 (and 4.0.4) run a **full local planner on the phone** that computes safe
speed/lane per the road network and streams fine-grained commands. Our rebuild does **direct speed control
+ a manual curve cap** — much simpler. We can't match the planner without porting `libDriveEngine`, so the
pragmatic hardening is: `respectRoadPieceSpeedLimit=true` (done) + (next, if needed) **upload the road
network with per-piece speed limits** so the firmware/planner can cap curves itself, and lean on the car's
own scanned-track limits. (Artifacts: `scratchpad/il2cpp/` — regenerate via Il2CppDumper + `nm -D | c++filt`.)

## On-track signals to confirm
- The race diagnostic now logs `flags=0x..`; confirm which bit = reverse at piece 33/34 (0x20 vs 0x40).
- Watch for `Race.uTurn … crossed start piece … in REVERSE` when a car is wrong-way.
