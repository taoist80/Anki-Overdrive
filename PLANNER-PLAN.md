# Local Planner — implementation plan

**Goal:** a phone-side driving planner modeled on 2.6's `BaseStation::LocalPlanner` (RE'd in
[DRIVING-PARITY.md](DRIVING-PARITY.md)), so cars are driven **curve-safe with real racing + battle
behavior** — replacing today's reactive manual curve-cap + simple varied-speed AI. We keep
override-localization ON (we remain the basestation; the car drives raw at our commanded speed), and the
planner computes those speed/lane commands.

## Why (recap)
Our control model sends raw speed and reacts to curves ~every 250 ms (a localization ping) — too late, so
cars fly off. 2.6 instead **estimates each car's continuous state** and runs a **cost-based short-horizon
planner** that picks a curve-safe speed + lane every 0.25 s. To match it we need the same three pieces:
a **track model**, **state estimation**, and the **planner**.

## Source material (all in hand)
- `libDriveEngine.so` symbols: `LocalPlanner::Planner`, `RoadNetwork::InitTrackMaxSpeedFromPieces`,
  `InfoTable::MaxSpeedIdxUnderLimit`, `PlannerActionFilter::SetMaxSpeed`, `SdkDrivingScript::Turn180`.
- Config (extracted, `scratchpad/ddl26/cfg2/.../basestation/config`):
  - `core/localPlannerParams.cfg` — clearance/proximity/dropoff penalties, plan period/horizon.
  - `aiConfigData/aic_parameters.json` — `curveLimitFrictionConstant 0.87`, `doCurveSpeedLimiting`,
    `epsilonSchedule`, `laneOffsetsToConsider`, `minLanesFor180`, plan-mode costs.
  - `aiConfigData/aic_commander_planner.json` — plan modes (racing / lane @0.34–0.8 m/s / battle goals).
  - `aiConfigData/aic_commander_{fsm,high_level_ai}.json` — per-difficulty FSM + traits (already ported
    to [DriverProfile.kt] at the rule level; the planner will consume them properly).
  - `modularRoadPieceDefinitionFiles/racing/*.txt` — per-piece geometry (length, ~20 lane offsets,
    curvature). Port to a compact bundled table keyed by road-piece id.

## Architecture — new `game/race/planner/` package
- **`RoadPieceGeometry`** — per-piece-id record {length_m, isCurve, curvatureRadius_m, laneOffsets[],
  maxSpeed_mmps}. Built from a bundled `roadPieceGeometry.json` (ported from the 2.6 defs).
- **`RoadNetwork`** — the scanned track as an ordered ring of pieces (built during the scan from the
  piece sequence we already see), each linked to its geometry + computed `maxSpeed`
  (`InitTrackMaxSpeedFromPieces`: `v = sqrt(0.87 · g · radius)`).
- **`VehicleStateEstimator`** — per car: `{pieceIndex, distAlongPiece_m, lane, speed_mmps}`. Advance by
  `speed·dt` each control tick; **correct** on each 0x27 position / 0x29 transition. (2.6:
  `vehicleLocalPlanRecomputePeriod 0.25`, `discretizedSpeedBinSize 0.1`.)
- **`LocalPlanner`** — for one car: search a short horizon of (lane, speed) actions over the upcoming
  pieces; score with the 2.6 cost terms (curve limit, clearance/proximity vs other cars' estimated
  states, dropoff, speed-mismatch, plan-mode goal); emit the best next speed + lane.
- **`PlanMode`** — `Racing` / `Lane(targetLane, maxSpeed)` / `Battle(goalRegion)` (relative-to-target).
- Integrate into `RaceEngine.control`: replace `effectiveSpeed` + the simple AI fire-position logic with
  `planner.plan(car) → drive(speed) + changeLane(lane)`.

## Phases (each shippable; early ones fix stability before the hard search)
- **Phase 0 — Track model + state estimation.** Port the piece-geometry table; build `RoadNetwork` from
  the scan; add `VehicleStateEstimator` (dead-reckoning + localization correction). *Deliverable:*
  continuous car state + look-ahead. No behavior change yet.
- **Phase 1 — Curve speed limiting (the stability fix).** Compute per-piece `maxSpeed` from curvature;
  clamp every car (incl. the player's throttle) to the **upcoming** pieces' limits using look-ahead, so
  cars brake *before* a curve. *Deliverable:* cars hold curves — the real fix for fly-offs. (Medium effort,
  biggest payoff.)
- **Phase 2 — Local planner (racing + lane modes).** The cost-based short-horizon search with
  clearance/proximity collision avoidance; AI cars driven by it. Start greedy/short-horizon, then add the
  epsilon-schedule search. *Deliverable:* AI drives racing lines + avoids contact. (The hard part.)
- **Phase 3 — Battle plan-modes + behavior FSM.** Port the battle goal-regions (get-behind, side-by-side,
  ahead, ram, evade) + the per-commander trait→mode FSM. *Deliverable:* AI commanders maneuver in combat.
- **Phase 4 — Tuning/parity.** Port the 2.6 params verbatim (clearances, penalties, friction, epsilon
  schedule); on-track tuning per difficulty.

## Scope / risk
- Phases 0–1 are the **foundation + the stability win** and are moderate effort — recommended first; they
  make the game playable on curves the right way and stand on their own even if 2–4 come later.
- Phase 2 (the search planner) is the large, hard piece. Phase 3 is the combat-AI depth. Multi-session.
- Player control: Phase 1 clamps the player's throttle to curve-safe speed (keeps direct throttle); making
  the player a full planner *goal* (firmware-style assist) is an optional later refinement.
- Verification: each phase needs on-track runs (cars). Keep the current build playable throughout.

## Regenerating the 2.6 spec data (scratchpad is per-session — a fresh chat re-extracts from the repo's xapk)
```sh
X="DDL Overdrive 2.6.10.xapk"; W=/tmp/od26; mkdir -p $W
unzip -o -j "$X" "DDL Overdrive 2.6 2.6.10.apk" -d $W
APK="$W/DDL Overdrive 2.6 2.6.10.apk"
# config spec (planner params, plan modes, FSM/traits, road-piece geometry):
unzip -o "$X" "Android/obb/com.digitaldreamlabs.retrodrive/main.22*.obb" -d $W
unzip -o "$W"/Android/obb/com.digitaldreamlabs.retrodrive/main.22*.obb \
  "assets/rams/overdrive/basestation/config/*" -d $W/cfg   # -> core/*.cfg, aiConfigData/aic_*.json, modularRoadPieceDefinitionFiles/
# native driving engine (symbols, NOT stripped) for Phase 2 algorithm study:
unzip -o -j "$APK" "lib/arm64-v8a/libDriveEngine.so" -d $W
nm -D $W/libDriveEngine.so | awk '{print $NF}' | c++filt > $W/driveengine_syms.txt   # grep LocalPlanner/RoadNetwork/...
# optional: Ghidra (/usr/local/Cellar/ghidra) to decompile LocalPlanner::Planner functions for the search.
# C# layer (orchestration): Il2CppDumper-net6 on lib/arm64-v8a/libil2cpp.so + assets/bin/Data/Managed/Metadata/global-metadata.dat
```
Key files: `core/localPlannerParams.cfg`, `aiConfigData/aic_parameters.json` (`curveLimitFrictionConstant 0.87`,
`epsilonSchedule`, `laneOffsetsToConsider`), `aic_commander_planner.json` (plan modes), `aic_commander_{fsm,high_level_ai}.json`
(traits), `modularRoadPieceDefinitionFiles/racing/*.txt` (per-piece geometry → port to `assets/gamedata/roadPieceGeometry.json`).

## Recommended start
**Phase 0 + 1** — track model, state estimation, and per-piece curve clamping with look-ahead. That alone
should fix the fly-offs properly (vs today's reactive cap), and it's the substrate the planner needs.
