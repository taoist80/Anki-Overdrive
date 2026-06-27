package dev.overdrive.game.race

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Phone-side driving planner — a faithful-structure port of 2.6's `BaseStation::LocalPlanner`
 * (algorithm reverse-engineered in PLANNER-ALGORITHM.md). For one car it runs a short-horizon
 * **weighted-A\*** over discretized `(step, lane, speedBin)` states and returns the best next
 * (speed, lane). It reproduces the three behavioral pieces of the real planner:
 *  1. **Action filtering** by the curve/accel max-speed (we feed it the look-ahead curve-safe cap).
 *  2. **Collision-avoidance cost** vs the other cars' *predicted* trajectories (the 2.6 g-cost is almost
 *     entirely this — `ComputeStateTransitionPenalty`), using the `localPlannerParams.cfg` clearances.
 *  3. A per-mode **goal/heuristic** ([PlanMode]) — race fast & dodge freely, or hold a target lane.
 *
 * It deliberately omits 2.6's PLID-precompute / ActionResultsTable / InfoTable caches — those are
 * real-time C++ optimizations for many cars, not behavior (PLANNER-ALGORITHM.md). Replans each control
 * tick (~0.25 s, matching `vehicleLocalPlanRecomputePeriod`).
 */
class LocalPlanner(private val network: RoadNetwork, private val estimator: VehicleStateEstimator) {

    /** 2.6 constants (config m/s,m → our mm/s,mm). See PLANNER-ALGORITHM.md / the .cfg sources. */
    object P {
        const val PLID_STEP_MM = 150f            // plidParams.spacing 0.15 m — one search step
        const val SPEED_BIN_MMPS = 100f          // discretizedSpeedBinSize 0.1 m/s
        const val ACCEL_MMPS2 = 1000f            // vehicleAcceleration/Deceleration 1.0 m/s²
        const val LANE_SPACING_MM = 9f           // globalRoadPieceParams.distanceBetweenLanes 0.009 m
        const val MAX_LANE_IDX = 7               // ±7 lanes ≈ ±63 mm, within RaceEngine.LANE_LIMIT
        const val MAX_LANE_SHIFT_PER_STEP = 3    // lanes a car may shift per 0.15 m step

        // Collision clearances (localPlannerParams.cfg) + Anki car geometry.
        const val CAR_LEN_MM = 90f
        const val CAR_WIDTH_MM = 45f
        const val FWD_CLEAR_MM = 90f + CAR_LEN_MM   // forwardClearanceRequired 0.09 + car length
        const val BACK_CLEAR_MM = 50f + CAR_LEN_MM  // backClearanceRequired 0.05 + car length
        const val LAT_CLEAR_MM = CAR_WIDTH_MM + 7f  // car width + sidePadding 0.007
        const val MAX_PROX_PENALTY = 1000f          // maxProximityPenalty
        const val PROX_FLOOR = 10f                  // maxProximityPenaltyFloor

        const val LANE_CHANGE_COST = 0.04f       // small per-lane cost (discourage needless weaving)
        const val EPSILON = 2.0f                 // weighted-A* inflation (mid of epsilonSchedule; anytime)
        const val HORIZON_PREDICT_S = 1.2f       // cap on how far ahead we trust other-car prediction
        const val MAX_EXPANSIONS = 240           // search budget (vehicleLocalPlanTimeAllocated analogue)
    }

    data class Result(val speedMmps: Int, val laneOffsetMm: Float)

    /** A snapshot of another car used for collision prediction. */
    private class Obstacle(val ringIndex: Int, val distAlongMm: Float, val speedMmps: Int, val laneOffsetMm: Float)

    private class Node(
        val step: Int, val laneIdx: Int, val speedBin: Int,
        val timeS: Float, val g: Float, val f: Float,
        val firstSpeedBin: Int, val firstLaneIdx: Int,
    )

    /**
     * Plan one control step for [addr]. [maxSpeedMmps] is the curve/throttle ceiling already computed by
     * the engine (look-ahead curve-safe cap × mode). Returns the next (speed, laneOffset) to command, or
     * null if the car isn't anchored on the ring yet (caller keeps its current behavior).
     */
    fun plan(addr: String, mode: PlanMode, maxSpeedMmps: Int, cars: Collection<CarTelemetry>, targetAddr: String? = null): Result? {
        if (!network.ready) return null
        val self = estimator.state(addr)
        if (self.ringIndex < 0) return null

        val maxBin = (maxSpeedMmps / P.SPEED_BIN_MMPS).toInt().coerceAtLeast(0)
        val curBin = (self.speedMmps / P.SPEED_BIN_MMPS).roundToInt().coerceIn(0, maxBin)
        val curLane = (self.laneOffsetMm / P.LANE_SPACING_MM).roundToInt().coerceIn(-P.MAX_LANE_IDX, P.MAX_LANE_IDX)
        val startCum = network.cumulativeMm(self.ringIndex, self.distAlongMm)

        val horizonM = when (mode) {
            is PlanMode.Racing -> mode.horizonM; is PlanMode.Lane -> mode.horizonM; is PlanMode.Battle -> mode.horizonM
        }
        val horizonSteps = (horizonM * 1000f / P.PLID_STEP_MM).roundToInt().coerceIn(2, 16)

        // Other cars → obstacles (skip self, off-track, disabled).
        val obstacles = cars.mapNotNull { c ->
            if (c.address == addr || c.offTrack || c.disabled) return@mapNotNull null
            val s = estimator.state(c.address)
            if (s.ringIndex < 0) null else Obstacle(s.ringIndex, s.distAlongMm, s.speedMmps, s.laneOffsetMm)
        }

        val targetLaneIdx = resolveTargetLane(mode, curLane)
        val targetSpeedBin = when (mode) {
            is PlanMode.Lane -> min(maxBin, (mode.maxSpeedMps * 1000f / P.SPEED_BIN_MMPS).toInt())
            else -> maxBin                                          // Racing / Battle chase at full speed
        }
        val speedMismatchCost = (mode as? PlanMode.Lane)?.speedMismatchCost ?: 0f
        val laneWeight = if (mode is PlanMode.Lane) 0.06f else 0f   // Racing/Battle steer via the heuristic, not a fixed lane

        // Battle: resolve the target vehicle's estimated state; the heuristic homes to `target + goal`.
        val battleGoal = (mode as? PlanMode.Battle)?.goal
        val targetObs = if (battleGoal != null && targetAddr != null) {
            val ts = estimator.state(targetAddr)
            if (ts.ringIndex >= 0) Obstacle(ts.ringIndex, ts.distAlongMm, ts.speedMmps, ts.laneOffsetMm) else null
        } else null

        // ---- weighted-A* (with a closed/visited set — 2.6's StateTable role) ----
        val open = PriorityQueue<Node>(compareBy { it.f })
        // visited[(step,lane,speed)] = best g seen; prunes the duplicate states the 28-way branching
        // generates, so the expansion budget reaches the full horizon instead of re-exploring.
        val visited = HashMap<Int, Float>()
        fun key(step: Int, lane: Int, speed: Int) = step * 100000 + (lane + P.MAX_LANE_IDX) * 256 + speed
        open.add(Node(0, curLane, curBin, 0f, 0f, 0f, -1, -1))
        visited[key(0, curLane, curBin)] = 0f
        var best: Node? = null
        var expansions = 0
        while (open.isNotEmpty() && expansions < P.MAX_EXPANSIONS) {
            val n = open.poll()
            if (n.step >= horizonSteps) { best = n; break }
            if (best == null || n.step > best!!.step) best = n   // best-so-far = deepest reached
            if ((visited[key(n.step, n.laneIdx, n.speedBin)] ?: Float.MAX_VALUE) < n.g) continue  // dominated
            expansions++

            val nextStep = n.step + 1
            val nextCum = startCum + nextStep * P.PLID_STEP_MM
            val pieceAhead = network.pieceIdAt(network.ringIndexAtCumulative(nextCum))
            val curveBinAhead = curveCapBin(pieceAhead, maxSpeedMmps)

            val accelBins = (P.ACCEL_MMPS2 * stepTime(n.speedBin, n.speedBin) / P.SPEED_BIN_MMPS)
                .toInt().coerceAtLeast(1)
            val speedCandidates = intArrayOf(n.speedBin, n.speedBin + accelBins, n.speedBin - accelBins, curveBinAhead)
            for (rawSpeed in speedCandidates) {
                val nextSpeed = rawSpeed.coerceIn(0, min(maxBin, curveBinAhead))
                for (dLane in -P.MAX_LANE_SHIFT_PER_STEP..P.MAX_LANE_SHIFT_PER_STEP) {
                    val nextLane = (n.laneIdx + dLane).coerceIn(-P.MAX_LANE_IDX, P.MAX_LANE_IDX)
                    val st = stepTime(n.speedBin, nextSpeed)
                    val newTime = n.timeS + st
                    val laneMm = nextLane * P.LANE_SPACING_MM
                    val edge = st +                                                   // travel-time cost (race fast)
                        collisionPenalty(nextCum, laneMm, newTime, obstacles) +       // avoid contact (2.6 g)
                        abs(dLane) * P.LANE_CHANGE_COST +                             // weaving cost
                        laneWeight * abs(nextLane - targetLaneIdx) * st +            // hold target lane (Lane mode)
                        speedMismatchCost * abs(nextSpeed - targetSpeedBin) * st     // hold target speed (Lane mode)
                    val g = n.g + edge
                    val k = key(nextStep, nextLane, nextSpeed)
                    if ((visited[k] ?: Float.MAX_VALUE) <= g) continue   // already reached this state cheaper
                    visited[k] = g
                    val h = if (battleGoal != null && targetObs != null)
                        battleH(nextCum, laneMm, newTime, targetObs, battleGoal, maxBin)
                    else heuristic(nextStep, horizonSteps, maxBin)
                    val first = if (n.step == 0) nextSpeed else n.firstSpeedBin
                    val firstL = if (n.step == 0) nextLane else n.firstLaneIdx
                    open.add(Node(nextStep, nextLane, nextSpeed, newTime, g, g + P.EPSILON * h, first, firstL))
                }
            }
        }
        val chosen = best ?: return null
        if (chosen.firstSpeedBin < 0) return Result((curBin * P.SPEED_BIN_MMPS).toInt(), curLane * P.LANE_SPACING_MM)
        return Result(
            speedMmps = (chosen.firstSpeedBin * P.SPEED_BIN_MMPS).toInt(),
            laneOffsetMm = chosen.firstLaneIdx * P.LANE_SPACING_MM,
        )
    }

    /** Admissible cost-to-go: remaining distance at best speed (lower bound on travel time to the goal). */
    private fun heuristic(step: Int, horizon: Int, maxBin: Int): Float {
        val remaining = (horizon - step).coerceAtLeast(0)
        val bestSpeed = maxBin.coerceAtLeast(1) * P.SPEED_BIN_MMPS
        return remaining * P.PLID_STEP_MM / bestSpeed
    }

    /**
     * Battle cost-to-go (2.6 `BattleMode::h`): time to reach the goal region defined relative to the
     * target's *predicted* position (`target + goal`). Zero inside the region (hold there), else the
     * longitudinal+lateral distance to its edge at best speed. The collision cost separately keeps the AI
     * from ramming the target, so a "behind" goal naturally produces a clean tail into firing position.
     */
    private fun battleH(nodeCum: Float, nodeLaneMm: Float, timeS: Float, target: Obstacle, goal: PlanMode.GoalRegion, maxBin: Int): Float {
        val lap = network.lapLengthMm
        val t = timeS.coerceAtMost(P.HORIZON_PREDICT_S)
        val goalCum = network.cumulativeMm(target.ringIndex, target.distAlongMm) + target.speedMmps * t + goal.distOffsetMm
        val goalLaneMm = target.laneOffsetMm + goal.laneOffset * P.LANE_SPACING_MM
        var long = (goalCum - nodeCum) % lap
        if (long > lap / 2f) long -= lap
        if (long < -lap / 2f) long += lap
        val longErr = (abs(long) - goal.halfLengthMm).coerceAtLeast(0f)
        val latErr = (abs(nodeLaneMm - goalLaneMm) - goal.halfLaneWidth * P.LANE_SPACING_MM).coerceAtLeast(0f)
        return (longErr + latErr) / (maxBin.coerceAtLeast(1) * P.SPEED_BIN_MMPS)
    }

    /** Time (s) to traverse one PLID step transitioning from [fromBin] to [toBin] at avg speed. */
    private fun stepTime(fromBin: Int, toBin: Int): Float {
        val avg = ((fromBin + toBin) * 0.5f * P.SPEED_BIN_MMPS).coerceAtLeast(P.SPEED_BIN_MMPS)
        return P.PLID_STEP_MM / avg
    }

    /** Curve-safe speed bin for a piece, under the engine's overall ceiling. */
    private fun curveCapBin(pieceId: Int, maxSpeedMmps: Int): Int {
        val raw = RoadPieceGeometry.rawCurveCapMmps(pieceId)
        val cap = if (raw <= 0) maxSpeedMmps else min(raw, maxSpeedMmps)
        return (cap / P.SPEED_BIN_MMPS).toInt().coerceAtLeast(0)
    }

    /**
     * Collision penalty at our predicted (cum, lane, time) vs every obstacle's predicted position
     * (assumes they hold speed + lane). Both longitudinal and lateral must overlap to count; the penalty
     * ramps to [P.MAX_PROX_PENALTY] as the gap closes (the 2.6 position-obstacle penalty).
     */
    private fun collisionPenalty(ourCum: Float, ourLaneMm: Float, timeS: Float, obstacles: List<Obstacle>): Float {
        if (obstacles.isEmpty()) return 0f
        val t = timeS.coerceAtMost(P.HORIZON_PREDICT_S)
        val lap = network.lapLengthMm
        var worst = 0f
        for (o in obstacles) {
            val theirCum = network.cumulativeMm(o.ringIndex, o.distAlongMm) + o.speedMmps * t
            var gap = (theirCum - ourCum) % lap                // signed forward gap (+ = ahead of us)
            if (gap > lap / 2f) gap -= lap
            if (gap < -lap / 2f) gap += lap
            val longClear = if (gap >= 0f) P.FWD_CLEAR_MM else P.BACK_CLEAR_MM
            val lat = abs(ourLaneMm - o.laneOffsetMm)
            if (abs(gap) >= longClear || lat >= P.LAT_CLEAR_MM) continue
            val closeness = min(1f - abs(gap) / longClear, 1f - lat / P.LAT_CLEAR_MM)
            val pen = (P.MAX_PROX_PENALTY * closeness).coerceAtLeast(P.PROX_FLOOR)
            if (pen > worst) worst = pen
        }
        return worst
    }

    private fun resolveTargetLane(mode: PlanMode, curLane: Int): Int = when (mode) {
        is PlanMode.Racing -> curLane                     // no fixed target — free to dodge
        is PlanMode.Battle -> curLane                     // lane is steered by the battle goal heuristic
        is PlanMode.Lane -> when (val tl = mode.target) {
            is PlanMode.TargetLane.RelativeCenter -> 0
            is PlanMode.TargetLane.Longest -> 0           // approx: centerline (true "longest lane" needs map data)
            is PlanMode.TargetLane.Absolute -> (tl.lane - 8).coerceIn(-P.MAX_LANE_IDX, P.MAX_LANE_IDX) // 2.6 lanes 0..15 → centered
            is PlanMode.TargetLane.RelativeInside -> tl.laneOffset.coerceIn(-P.MAX_LANE_IDX, P.MAX_LANE_IDX)
        }
    }
}
