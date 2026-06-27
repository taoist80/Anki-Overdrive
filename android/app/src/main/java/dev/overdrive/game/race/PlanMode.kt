package dev.overdrive.game.race

/**
 * A planner goal/mode — the "personality" that steers the [LocalPlanner]'s heuristic, ported from 2.6's
 * `aic_commander_planner.json` + the decompiled `RacingMode::h` / `LaneMode::h` (see PLANNER-ALGORITHM.md).
 * The search cost (collision avoidance) is mode-independent; the mode only changes the cost-to-go `h`:
 *  - [Racing]: race the **inside/shortest lane** toward a point `horizonM` ahead as fast as the curve
 *    filter allows (2.6 `RacingMode` → ShortestLaneHeuristic).
 *  - [Lane]: reach + hold a [target] lane at [maxSpeedMps], penalizing lane misalignment and speed
 *    mismatch (2.6 `LaneMode` → `h = max(timeToGoal, laneMisalignmentCost)`).
 * (BattleMode is Phase 3.)
 */
sealed class PlanMode {
    data class Racing(val horizonM: Float = 1.5f) : PlanMode()

    data class Lane(
        val target: TargetLane,
        val halfLaneWidth: Int,
        val maxSpeedMps: Float,
        val speedMismatchCost: Float = 0f,
        val horizonM: Float = 1.5f,
    ) : PlanMode()

    /**
     * Battle (Phase 3): maneuver into a [goal] region defined **relative to a target vehicle** (2.6
     * `BattleMode` → `GoalRegionTable`). The planner aims for `target + goal` (e.g. ~0.4 m behind, in the
     * target's lane) so the AI tails into firing position; combat's fire cadence then engages. The goal
     * regions mirror `aic_commander_planner.json` (behind / side-by-side / ahead).
     */
    data class Battle(val goal: GoalRegion, val horizonM: Float = 1.5f) : PlanMode()

    /** A goal region relative to the target vehicle (distances mm, signed: + ahead / − behind; lanes). */
    data class GoalRegion(
        val distOffsetMm: Float,     // along-track offset from the target (+ ahead, − behind)
        val halfLengthMm: Float,     // longitudinal tolerance
        val laneOffset: Int,         // lanes from the target's lane (± = side-by-side)
        val halfLaneWidth: Int,      // lateral tolerance (lanes)
    )

    /** How a [Lane] mode resolves its target lane index (2.6 `target_lane.type`). */
    sealed class TargetLane {
        object Longest : TargetLane()                       // the lane with the most clearance (we approx: centerline)
        data class Absolute(val lane: Int) : TargetLane()   // a fixed lane index
        object RelativeCenter : TargetLane()                // track centerline (lane 0)
        data class RelativeInside(val laneOffset: Int) : TargetLane()  // `laneOffset` lanes inside the racing line
    }

    companion object {
        // 2.6 fallback_planner_modes (aic_commander_planner.json), m/s preserved.
        val RACING = Racing(horizonM = 1.5f)
        val SLOW_LANE = Lane(TargetLane.Absolute(7), halfLaneWidth = 10, maxSpeedMps = 0.6f)
        val MEDIUM_LANE = Lane(TargetLane.RelativeCenter, halfLaneWidth = 6, maxSpeedMps = 0.8f, speedMismatchCost = 0.2f)
        // 2.6 nonconstant_planner_modes (battle goal-regions), m→mm.
        val BEHIND_NORMAL = GoalRegion(distOffsetMm = -500f, halfLengthMm = 200f, laneOffset = 0, halfLaneWidth = 2)
        val BEHIND_CLOSE = GoalRegion(distOffsetMm = -250f, halfLengthMm = 100f, laneOffset = 0, halfLaneWidth = 2)
        val SIDE_BY_SIDE_R = GoalRegion(distOffsetMm = 0f, halfLengthMm = 50f, laneOffset = 6, halfLaneWidth = 3)
        val SITTING_DUCK = GoalRegion(distOffsetMm = 0f, halfLengthMm = 100f, laneOffset = 5, halfLaneWidth = 3)  // flank a disabled target

        /**
         * Pick a plan mode for an AI car from its [DriverProfile]. Weaponized commanders **hunt** — they
         * maneuver into a firing position behind the target (battle goal-region), with the follow distance
         * set by the trait's [DriverProfile.followFactor] (hostile tails close, lazy hangs back). Pure
         * racers race the line; defensive non-combatants hold a center lane. Speed is still scaled by
         * [DriverProfile.speedScale].
         */
        fun forProfile(p: DriverProfile, weaponsThisRace: Boolean): PlanMode = when {
            weaponsThisRace && p.weaponsOn -> Battle(GoalRegion(
                distOffsetMm = -(200f + p.followFactor * 500f),   // hostile ≈ −250 mm … lazy ≈ −650 mm behind
                halfLengthMm = 200f, laneOffset = 0, halfLaneWidth = 3,
            ))
            p.defensive -> MEDIUM_LANE
            else -> RACING
        }

        /** True if this profile drives the combat FSM ([CommanderBrain]) — i.e. it's an armed combatant. */
        fun isCombatant(p: DriverProfile, weaponsThisRace: Boolean): Boolean = weaponsThisRace && p.weaponsOn
    }
}

/**
 * The per-tick combat FSM (Phase 3 inc2) — a faithful-structure port of 2.6's `think_high_level_goal`
 * (aic_commander_fsm.json). For an armed commander it picks a battle [PlanMode] each control tick from the
 * live situation vs its target: finish off a disabled "sitting duck", flee when badly hurt (defensive),
 * close in when behind & out of range, tail-and-fire when behind & in range, or flank when it's ahead.
 * The follow distance still scales with the trait ([DriverProfile.followFactor]).
 */
object CommanderBrain {
    const val FIRE_RANGE_MM = 600f   // longitudinal gap within which a tailing car can fire (approx weapon reach)
    const val HURT_HEALTH = 35f      // below this a defensive commander breaks off to recover

    /**
     * @param signedGapMm  along-track gap self→target: **+ = target ahead of me** (I'm chasing),
     *                     **− = target behind me** (I'm leading).
     */
    fun selectMode(p: DriverProfile, selfHealth: Float, targetDisabled: Boolean, signedGapMm: Float): PlanMode = when {
        targetDisabled -> PlanMode.Battle(PlanMode.SITTING_DUCK)                        // pummel the duck
        selfHealth < HURT_HEALTH && p.defensive -> PlanMode.RACING                      // hurt + cautious → disengage
        signedGapMm > FIRE_RANGE_MM -> PlanMode.Battle(behindGoal(p))                   // far behind → close in
        signedGapMm > 0f -> PlanMode.Battle(PlanMode.BEHIND_CLOSE)                      // behind, in range → tail + fire
        else -> PlanMode.Battle(PlanMode.SIDE_BY_SIDE_R)                                // I'm ahead → flank to re-engage
    }

    private fun behindGoal(p: DriverProfile) = PlanMode.GoalRegion(
        distOffsetMm = -(200f + p.followFactor * 500f), halfLengthMm = 200f, laneOffset = 0, halfLaneWidth = 3,
    )
}
