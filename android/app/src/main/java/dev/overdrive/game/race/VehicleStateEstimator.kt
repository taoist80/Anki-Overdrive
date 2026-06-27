package dev.overdrive.game.race

import kotlin.math.roundToInt

/**
 * Per-car continuous state estimation (Phase 0), modeled on 2.6's vehicle local-plan state
 * (`vehicleLocalPlanRecomputePeriod 0.25`). The firmware only tells us *which* piece a car is on
 * (0x27, ~every 250 ms) plus its lateral offset and speed — never how far along the piece it is. So we
 * **dead-reckon** the longitudinal distance (`distAlongMm += speed·dt`) between updates and **correct**
 * the ring index on each piece change / transition, giving the planner a continuous estimate of where
 * each car is and what's ahead of it.
 */
class VehicleStateEstimator(private val network: RoadNetwork) {

    /** One car's estimated state. `ringIndex` is its slot in [RoadNetwork]; -1 until anchored. */
    data class State(
        var ringIndex: Int = -1,
        var distAlongMm: Float = 0f,
        var speedMmps: Int = 0,
        var laneOffsetMm: Float = 0f,
        var anchored: Boolean = false,
    )

    private val states = HashMap<String, State>()

    fun state(addr: String): State = states.getOrPut(addr) { State() }
    fun reset(addr: String) { states[addr] = State() }
    fun clear() = states.clear()

    /** Lane index nearest the current offset (0 = centerline), using the geometry's lane spacing. */
    fun laneIndex(addr: String): Int =
        (state(addr).laneOffsetMm / RoadPieceGeometry.laneSpacingMm).roundToInt()

    /**
     * Correct on a confirmed piece change (0x27 reported a new road-piece id). Resyncs the ring index to
     * the nearest forward slot whose piece id matches, and resets the dead-reckoned distance to the start
     * of the new piece. Robust to a missed transition (jumps forward to the matching id) and to the ring
     * not yet being built (just advances by one).
     */
    fun onPieceChange(addr: String, pieceId: Int) {
        val s = state(addr)
        s.distAlongMm = 0f
        if (!network.ready) { s.ringIndex = -1; s.anchored = false; return }
        if (!s.anchored) {
            // First sighting: anchor onto the first ring slot carrying this piece id.
            s.ringIndex = (0 until network.size).firstOrNull { network.pieceIdAt(it) == pieceId } ?: 0
            s.anchored = true
            return
        }
        // Normal case: the next slot already matches. Otherwise search forward (a piece may have been
        // missed between updates) for the nearest matching id; if none, accept a single-step advance.
        val next = s.ringIndex + 1
        if (network.pieceIdAt(next) == pieceId) { s.ringIndex = next; return }
        val fwd = (1..network.size).firstOrNull { network.pieceIdAt(s.ringIndex + it) == pieceId }
        s.ringIndex = if (fwd != null) s.ringIndex + fwd else next
    }

    /** Fold in the latest 0x27 telemetry (speed + lateral offset). */
    fun onPosition(addr: String, speedMmps: Int, offsetMm: Float) {
        val s = state(addr)
        s.speedMmps = speedMmps
        s.laneOffsetMm = offsetMm
    }

    /**
     * Dead-reckon one control tick: advance the longitudinal estimate by `speed·dt`, clamped to the
     * current piece length (we don't know we've crossed into the next piece until 0x27/0x29 confirms it).
     */
    fun tick(addr: String, dtMs: Long) {
        val s = states[addr] ?: return
        if (s.ringIndex < 0) return
        s.distAlongMm += s.speedMmps * (dtMs / 1000f)
        val len = RoadPieceGeometry.lengthMm(network.pieceIdAt(s.ringIndex)).toFloat()
        if (s.distAlongMm > len) s.distAlongMm = len
    }

    /**
     * Look-ahead curve-safe speed (mm/s) for this car (see [RoadNetwork.curveSafeSpeed]). Returns [noCap]
     * if the car isn't anchored on the ring yet (caller falls back to the current-piece cap).
     */
    fun curveSafeSpeed(addr: String, decel: Float, noCap: Int, capOf: (pieceId: Int) -> Int): Int {
        val s = states[addr] ?: return noCap
        if (s.ringIndex < 0 || !network.ready) return noCap
        return network.curveSafeSpeed(s.ringIndex, s.distAlongMm, decel, noCap, capOf)
    }
}
