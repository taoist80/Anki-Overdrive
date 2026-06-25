package dev.overdrive.game.race

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.overdrive.AnkiCarManagerHolder
import dev.overdrive.CarManager
import dev.overdrive.Protocol

/** Live state for one car in a race, driven by 0x27 position telemetry. */
data class CarTelemetry(
    val address: String,
    val name: String,
    val isPlayer: Boolean,
    val speedMmPerSec: Int = 0,
    val roadPieceId: Int = -1,
    val locationId: Int = -1,
    val offsetMm: Float = 0f,
    val targetOffsetMm: Float = 0f,
    val onCurve: Boolean = false,
    val transitions: Int = 0,
    val laps: Int = 0,
    val lastUpdateMs: Long = 0L,
)

data class RaceState(
    val mode: String = "",
    val running: Boolean = false,
    val elapsedMs: Long = 0L,
    val cars: List<CarTelemetry> = emptyList(),
) {
    /** Standings: most laps, then most segment transitions (track-progress tiebreaker). */
    val standings: List<CarTelemetry>
        get() = cars.sortedWith(compareByDescending<CarTelemetry> { it.laps }.thenByDescending { it.transitions })
}

/**
 * The driving loop. Wraps the proven [CarManager]: sends setSpeed/changeLane and consumes 0x27
 * position + 0x29 transition notifications. The player's car (first connected) is driven from the
 * HUD; other connected cars run a simple varied-speed AI.
 *
 * Robustness: each car's target speed is re-sent on a control loop (BLE writes-without-response can
 * drop). Curve safety: speed is auto-capped to [CURVE_SPEED] whenever a car is on a curve piece
 * (classified offline in [RoadPieces]) — this lets full throttle be fast on straights without
 * fishtailing on bends. Laps: counted per car when it returns to the piece it started the race on.
 */
class RaceEngine(private val mgr: CarManager) {

    companion object {
        private const val TAG = "OverdriveX"
        const val MAX_SPEED = 900       // mm/s at full throttle (curves auto-cap below)
        const val CURVE_SPEED = 450     // mm/s ceiling while on a curve piece
        const val PLAYER_START = 500    // mm/s default when the race starts
        const val AI_BASE = 440         // mm/s base for AI rivals (varied per car)
        const val AI_SPREAD = 40        // mm/s step between AI rivals
        const val ACCEL = 1000          // mm/s^2 (gentle — reduces fishtail on speed changes)
        const val LANE_STEP = 44f       // mm per lane nudge
        const val LANE_LIMIT = 68f      // mm max offset from centerline
        const val LANE_H_SPEED = 400    // mm/s horizontal during a lane change
        const val CONTROL_MS = 250L     // re-send cadence for target speeds
    }

    var state by mutableStateOf(RaceState())
        private set

    private val main = Handler(Looper.getMainLooper())
    private val tele = LinkedHashMap<String, CarTelemetry>()
    private val targetSpeed = HashMap<String, Int>()
    private val firstPiece = HashMap<String, Int>()  // each car's lap marker (piece at race start)
    private val lastPiece = HashMap<String, Int>()
    private var startedAt = 0L
    private var playerAddr: String? = null

    init {
        mgr.onPosition = ::onPosition
        mgr.onTransition = ::onTransition
    }

    val playerAddress: String? get() = playerAddr

    /** Snapshot connected cars into the race; the first is the player's. Call before [start]. */
    fun arm(mode: String) {
        val connected = mgr.connectedCars()
        playerAddr = connected.firstOrNull()?.address
        tele.clear(); targetSpeed.clear(); firstPiece.clear(); lastPiece.clear()
        connected.forEach { c ->
            tele[c.address] = CarTelemetry(c.address, c.name, isPlayer = c.address == playerAddr)
        }
        startedAt = 0L
        Log.i(TAG, "Race.arm: ${connected.size} cars [${connected.joinToString { it.name }}], player=$playerAddr")
        publish(mode = mode, running = false)
    }

    fun start() {
        arm(state.mode)   // re-snapshot: include any car that finished connecting after Match Setup
        startedAt = SystemClock.elapsedRealtime()
        var aiIdx = 0
        tele.keys.forEach { addr ->
            targetSpeed[addr] = if (addr == playerAddr) PLAYER_START else (AI_BASE + (aiIdx++ % 4) * AI_SPREAD)
            mgr.setLaneOffset(addr, 0f)
        }
        Log.i(TAG, "Race.start: driving ${tele.size} cars, targets=$targetSpeed")
        publish(running = true)
        main.removeCallbacks(control)
        main.post(control)
    }

    fun stop() {
        targetSpeed.clear()
        tele.keys.forEach { mgr.drive(it, 0) }
        main.removeCallbacks(control)
        Log.i(TAG, "Race.stop")
        publish(running = false)
    }

    /** HUD throttle: 0f..1f -> mm/s for the player's car (curve cap still applies live). */
    fun setThrottle(fraction: Float) {
        val addr = playerAddr ?: return
        targetSpeed[addr] = (fraction.coerceIn(0f, 1f) * MAX_SPEED).toInt()
        mgr.drive(addr, effectiveSpeed(addr, tele[addr]?.roadPieceId ?: -1), ACCEL)
    }

    fun nudgeLane(deltaSteps: Int) {
        val addr = playerAddr ?: return
        val cur = tele[addr]?.targetOffsetMm ?: 0f
        val next = (cur + deltaSteps * LANE_STEP).coerceIn(-LANE_LIMIT, LANE_LIMIT)
        tele[addr]?.let { tele[addr] = it.copy(targetOffsetMm = next) }
        mgr.changeLane(addr, next, hSpeed = LANE_H_SPEED, hAccel = ACCEL)
        Log.i(TAG, "Race.nudgeLane: $addr -> ${next}mm")
        publish()
    }

    fun fireWeapon() { /* Phase 4: item/weapon system */ }

    /** Curve-aware target: cap to CURVE_SPEED while on a curve piece, else the full target. */
    private fun effectiveSpeed(addr: String, pieceId: Int): Int {
        val target = targetSpeed[addr] ?: return 0
        return if (RoadPieces.isCurve(pieceId)) minOf(target, CURVE_SPEED) else target
    }

    /** Control loop: re-send each car's (curve-aware) target so a dropped write can't stall a car. */
    private val control = object : Runnable {
        override fun run() {
            if (!state.running) return
            tele.keys.forEach { addr ->
                val s = effectiveSpeed(addr, tele[addr]?.roadPieceId ?: -1)
                if (s > 0) mgr.drive(addr, s, ACCEL)
            }
            publish()
            main.postDelayed(this, CONTROL_MS)
        }
    }

    private fun onPosition(addr: String, p: Protocol.Position) {
        val t = tele[addr] ?: return
        val newPiece = p.roadPieceId
        val prev = lastPiece[addr] ?: -1
        var laps = t.laps

        if (newPiece >= 0 && newPiece != prev) {
            when {
                !firstPiece.containsKey(addr) -> firstPiece[addr] = newPiece          // race-start marker
                newPiece == firstPiece[addr] && prev != -1 -> laps += 1               // crossed start again
            }
            lastPiece[addr] = newPiece
            if (state.running) mgr.drive(addr, effectiveSpeed(addr, newPiece), ACCEL) // react to curve immediately
        }

        tele[addr] = t.copy(
            speedMmPerSec = p.speedMmPerSec,
            roadPieceId = newPiece,
            locationId = p.locationId,
            offsetMm = p.offsetMm,
            onCurve = RoadPieces.isCurve(newPiece),
            laps = laps,
            lastUpdateMs = SystemClock.elapsedRealtime(),
        )
        publish()
    }

    private fun onTransition(addr: String) {
        val t = tele[addr] ?: return
        tele[addr] = t.copy(transitions = t.transitions + 1)
        publish()
    }

    private fun elapsed(): Long = if (startedAt == 0L) 0L else SystemClock.elapsedRealtime() - startedAt

    private fun publish(mode: String = state.mode, running: Boolean = state.running) {
        state = RaceState(mode, running, elapsed(), tele.values.toList())
    }
}

/** Process-wide RaceEngine, lazily bound to the single CarManager. */
object RaceEngineHolder {
    val engine: RaceEngine by lazy { RaceEngine(AnkiCarManagerHolder.require()) }
}
