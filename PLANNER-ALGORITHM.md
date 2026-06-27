# 2.6 LocalPlanner — reverse-engineered algorithm (from libDriveEngine.so)

Decompiled with Ghidra (headless) from `lib/arm64-v8a/libDriveEngine.so` (2.6.10). The phone-side
planner is **Anytime Repairing A\* (ARA\*)** over a discretized vehicle state. Functions studied:
`Planner::ImprovePlan`, `Planner::ExpandState`, `State::GetSuccessors`, `State::ComputeStateTransitionPenalty`,
`RacingMode::h/Init`, `LaneMode::h/Init`, `BattleMode::h/Init`, `RoadNetwork::InitTrackMaxSpeedFromPieces`,
`InfoTable::MaxSpeedIdxUnderLimit/Speed2SpeedIdx/ClosestSpeedIdxUnderLimit`, `PlannerActionFilter::SetMaxSpeed`.

## State space
A node = **(PLID, lane, speedIdx)** + a time index.
- **PLID** = piece-lane-id: each lane of each drivable section sampled every `spacing = 0.15 m`
  (`aic_parameters.plidParams`: `offset 0.01`, `endOfLaneBuffer 0.03`). A position+lane discretization.
- **speedIdx** = speed bin, `discretizedSpeedBinSize 0.1 m/s`, `[0 … discretizedSpeedMax 3.0]`
  (`baseStationParams.cfg`); `num_speed_indices_cars 255`.
- **lane** = track lane index. State ID packs `(PLID << 19) | (lane << 5) | speedIdx` (`GetSuccessors`).
- **time** via `State::Time2TimeIndex` — states carry an arrival time (for moving-obstacle prediction).

## Search — ARA\* (`Planner::ImprovePlan`)
- Weighted A\* with an inflation schedule `epsilonSchedule = [100,50,10,5,4,3,2.5,2,1.9,1.8,1.7,1.5,1.2,1.0]`
  (`aic_parameters.json`): expand best `f = g + ε·h`, shrink ε each pass → fast-then-optimal anytime plans.
- Budget: replan every `vehicleLocalPlanRecomputePeriod 0.25 s`, `vehicleLocalPlanTimeAllocated 0.22 s`,
  `min_expansions_for_termination 1200`; aborts on "way too many ara iterations".
- If `PlannerActionFilter::Any()` is false (no speed under the curve/accel limit) it doesn't run.

## Successors (`State::GetSuccessors` + `ActionResultsTable`)
From each (PLID, speedIdx) a precomputed, cached set of actions → successor states
(`ComputeAndStoreAllActionResults(..., 100)`). Actions: speed-bin changes (accel/decel within
`vehicleAcceleration/Deceleration 1.0 m/s²`), lane changes to `laneOffsetsToConsider [1,2,3,5,7,11,15,99]`,
and specials (180, e-brake, boost). Filtered by `PlannerActionFilter`:
- `SetMaxSpeed(v)` → `MaxSpeedIdxUnderLimit(v, chassisStyle)` = highest speed bin **< v** whose chassis
  flags match. The curve limit thus *removes* too-fast actions rather than penalizing them.
- per-piece max speed lives at piece+0x24; `InitTrackMaxSpeedFromPieces` just takes the track-wide max
  (for table sizing). (Per-piece value = the curve limit; our Phase 1 `v=sqrt(0.87·g·R)` reproduces it.)

## Transition cost g (`State::ComputeStateTransitionPenalty`)
Integrated over the action's intermediate states (`IntermediateLocationTimeState`, stride 12B):
```
g = max_i ComputePositionObstaclePenalty(pos_i, time_i)          // closest-approach collision penalty
  + Σ_i (segLen_i · ComputeEvasionPenalty(plid_i, time_i))        // path-integral of evasion cost
  // clamped to SystemParameters max (≈ maxProximityPenalty 1000)
```
→ cost is **almost entirely moving-collision avoidance** vs other cars' predicted trajectories
(`MovingObstacleTracker`). Clearances/paddings from `localPlannerParams.cfg`
(`forwardClearanceRequired 0.09`, `backClearanceRequired 0.05`, `sideClearanceRequired 0.008`, dropoff
distances/penalties, `frontPadding 0.02`/`backPadding 0.025`/`sidePadding 0.007`) + action costs
(`planner_one_eighty/ebrake/boost_*`). Curve safety is NOT in g — it's in the action filter.

## Heuristics h (per mode) — the "personality"
- **RacingMode** (`h` → `ShortestLaneHeuristic`): cost-to-go = time to the goal `horizon_dist 1.5 m` ahead
  via the **shortest/inside lane** → drives the racing line as fast as the filter allows.
- **LaneMode** (`h = max(timeToGoal, laneMisalignmentCost)`): reach + hold `target_lane`
  (longest / absolute N / relative_center / relative_inside+offset) at `max_speed` (0.34/0.6/0.8 m/s),
  with `speed_mismatch_cost`/`half_lane_width` (`aic_commander_planner.json`).
- **BattleMode** (Phase 3): reach a `goal_region` relative to the target vehicle (behind −0.5/−0.25 m, etc.).

## Takeaway for our port
The behavior = **A\* over (track-position, lane, speed-bin)** that (a) filters actions by the curve/accel
limit, (b) costs successors almost entirely by predicted-collision avoidance vs the other cars, (c) is
steered by a per-mode goal+heuristic (race the inside line / hold a lane / battle position). The PLID
precompute + ActionResultsTable + InfoTable are real-time **optimizations** for running many cars in C++,
not behavioral requirements. Our port reproduces (a)–(c) over the [RoadNetwork] ring + [VehicleStateEstimator]
state with the **real constants**, without the precompute machinery.
