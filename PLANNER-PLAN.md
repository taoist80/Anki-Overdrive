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
- **Phase 0 — Track model + state estimation. ✅ LANDED (branch phase9-hud).** Decoded the 2.6 piece
  geometry files (line1=arc length m, line2=signed radius m / 0=straight; lanes ±0.0855 m @0.009 m) and
  bundled `assets/gamedata/roadPieceGeometry.json` (137 pieces, 69 curves; regen via the commands below).
  `RoadPieceGeometry.kt` (table loader, replaces the old curve-id heuristic + deletes `RoadPieces.kt`),
  `RoadNetwork.kt` (ordered ring built during the scan from the piece sequence; max-gap finish-pair to
  dodge the finish re-trigger quirk; `lookAhead` + `curveSafeSpeed` backward-pass), `VehicleStateEstimator.kt`
  (per-car ringIndex + dead-reckoned distAlongPiece; resync on 0x27 piece change). Builds clean.
- **Phase 1 — Curve speed limiting (the stability fix). ✅ LANDED.** Per-piece cap = 2.6 parity
  `v=sqrt(0.87·g·|R|)` × `CURVE_SPEED_SCALE` (RaceEngine), applied with **look-ahead**: `curveSafeCap` →
  `estimator.curveSafeSpeed` clamps every car (incl. the player's throttle) so it brakes *before* a curve
  (`LOOKAHEAD_DECEL`). Falls back to the current-piece cap if the ring isn't mapped. **Calibration decision:**
  μ=0.87's absolute caps (sharpest ≈1034 mm/s) sit above where our cars empirically fly off (~450–600), so
  `CURVE_SPEED_SCALE=0.45` lands the sharpest mapped curve ≈ the old known-safe 450 mm/s; gentler curves run
  faster. `CURVE_SPEED_SCALE` + `LOOKAHEAD_DECEL` are the **primary on-track tuning knobs** (Phase 4).
  *Still needs on-track validation runs (cars).*
- **Phase 2 — Local planner (racing + lane modes). ✅ LANDED (faithful-structure).** RE'd the real 2.6
  planner (ARA* over (PLID,lane,speedIdx); see [PLANNER-ALGORITHM.md]) then ported `PlanMode.kt` +
  `LocalPlanner.kt`: weighted-A* over (step,lane,speedBin), g = travel-time + predicted-collision penalty
  (the 2.6 `ComputeStateTransitionPenalty` clearances) + lane/speed-mismatch, h = remaining-time, curve
  filtering per step, closed-set dedup, 240-expansion budget. `planAiCars()` drives each AI's speed+lane;
  player stays on throttle. **Scan-mapping fix:** stage only after a full finish-to-finish lap so the ring
  reliably maps (was: only mapped if a car started just before the finish → planner silently fell back).
  *Needs on-track validation (lane-passing, weaving).*
- **Phase 3 — Battle plan-modes + behavior FSM.** ◀ IN PROGRESS. Port the battle goal-regions (get-behind,
  side-by-side, ahead, ram, evade — `BattleMode::h` targets a `GoalRegionTable` region relative to the
  target vehicle) + the per-commander trait→mode FSM. *Deliverable:* AI commanders maneuver in combat.
- **Phase 4 — Tuning/parity.** Port the 2.6 params verbatim (clearances, penalties, friction, epsilon
  schedule); on-track tuning per difficulty.
- **Phase 5 (post-driving) — Track-Scanning Parity.** Replace our roadPieceId-sequence ring with 2.6's
  `TrackDetectGamePhase` approach (RE'd; see [[anki-26-track-scanning]]): use the 0x27 `locationId` (the
  optical sub-piece code we currently ignore) as per-car `CodeEntry`s; assemble the track by longest-common-
  substring overlap matching across **all** cars until loop closure (`DoesSegmentHaveLoopClosure`); track-
  type prediction from a 6-piece signature; validate (gap/missing-piece errors) → **player confirm** step →
  planner init; **cache the last scanned track** to skip re-scanning the same layout. Discovery speed
  0.25→0.4 m/s (2.6 `kBS_DISCOVERY_*`). *User-requested; do after the driving logic is complete.*

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
