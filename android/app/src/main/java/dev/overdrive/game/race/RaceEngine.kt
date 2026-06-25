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
    /** Standings: most laps, then most segment transitions (proxy for track progress). */
    val standings: List<CarTelemetry>
        get() = cars.sortedWith(compareByDescending<CarTelemetry> { it.laps }.thenByDescending { it.transitions })
}

/**
 * The driving loop. Wraps the proven [CarManager]: sends setSpeed/changeLane and consumes 0x27
 * position + 0x29 transition notifications. The player's car (first connected) is driven from the
 * HUD; other connected cars run a simple constant-speed AI so a physical race has rivals.
 *
 * Robustness: setSpeed is re-sent on a control loop (~250 ms) for every armed car, because BLE
 * writes-without-response can be dropped — a single initial command sometimes never reaches a car
 * (this is why AI cars could sit still). Continuous sending mirrors the original game.
 *
 * Lap detection is approximate for now (segment-transition proxy) — it tightens in Phase 3 once the
 * track is scanned and the start/finish piece is known.
 */
class RaceEngine(private val mgr: CarManager) {

    companion object {
        private const val TAG = "OverdriveX"
        // Cars fishtail through bends above ~700 mm/s on a curvy track. Until the track model lets
        // us slow for curves per-segment (Phase 3), cap full throttle to a controllable speed and
        // keep acceleration gentle to avoid traction loss on speed changes.
        const val MAX_SPEED = 700       // mm/s at full throttle (controllable on bends)
        const val PLAYER_START = 500    // mm/s default when the race starts
        const val AI_BASE = 440         // mm/s base for AI rivals (varied per car below)
        const val AI_SPREAD = 40        // mm/s step between AI rivals so it's a real race
        const val ACCEL = 1000          // mm/s^2 (gentle — reduces fishtail on speed changes)
        const val LANE_STEP = 44f       // mm per lane nudge (a clear, visible jump)
        const val LANE_LIMIT = 68f      // mm max offset from centerline
        const val LANE_H_SPEED = 400    // mm/s horizontal during a lane change
        const val CONTROL_MS = 250L     // re-send cadence for target speeds
        const val SEGMENTS_PER_LAP = 0  // 0 = unknown until track scan (Phase 3)
    }

    var state by mutableStateOf(RaceState())
        private set

    private val main = Handler(Looper.getMainLooper())
    private val tele = LinkedHashMap<String, CarTelemetry>()
    private val targetSpeed = HashMap<String, Int>()
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
        tele.clear()
        targetSpeed.clear()
        connected.forEach { c ->
            tele[c.address] = CarTelemetry(c.address, c.name, isPlayer = c.address == playerAddr)
        }
        startedAt = 0L
        Log.i(TAG, "Race.arm: ${connected.size} cars [${connected.joinToString { it.name }}], player=$playerAddr")
        publish(mode = mode, running = false)
    }

    fun start() {
        arm(state.mode)   // re-snapshot connected cars: include any that finished connecting after Match Setup
        startedAt = SystemClock.elapsedRealtime()
        var aiIdx = 0
        tele.keys.forEach { addr ->
            targetSpeed[addr] = if (addr == playerAddr) PLAYER_START
                else (AI_BASE + (aiIdx++ % 4) * AI_SPREAD)   // 440/480/520/560 — a spread field
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

    /** HUD throttle: 0f..1f -> mm/s for the player's car. */
    fun setThrottle(fraction: Float) {
        val addr = playerAddr ?: return
        val speed = (fraction.coerceIn(0f, 1f) * MAX_SPEED).toInt()
        targetSpeed[addr] = speed
        mgr.drive(addr, speed, ACCEL)
    }

    /** HUD lane buttons: shift the player's target offset by [deltaSteps] lane steps. */
    fun nudgeLane(deltaSteps: Int) {
        val addr = playerAddr ?: return
        val cur = tele[addr]?.targetOffsetMm ?: 0f
        val next = (cur + deltaSteps * LANE_STEP).coerceIn(-LANE_LIMIT, LANE_LIMIT)
        tele[addr]?.let { tele[addr] = it.copy(targetOffsetMm = next) }
        mgr.changeLane(addr, next, hSpeed = LANE_H_SPEED, hAccel = ACCEL)
        Log.i(TAG, "Race.nudgeLane: $addr -> ${next}mm")
        publish()
    }

    /** HUD weapon button — wired to the item/weapon system in Phase 4. */
    fun fireWeapon() { /* no-op until items exist */ }

    /** Control loop: re-send each car's target speed so a dropped write can't leave a car stalled. */
    private val control = object : Runnable {
        override fun run() {
            if (!state.running) return
            targetSpeed.forEach { (addr, speed) -> if (speed > 0) mgr.drive(addr, speed, ACCEL) }
            publish()   // refresh elapsed time
            main.postDelayed(this, CONTROL_MS)
        }
    }

    private fun onPosition(addr: String, p: Protocol.Position) {
        val t = tele[addr] ?: return
        tele[addr] = t.copy(
            speedMmPerSec = p.speedMmPerSec,
            roadPieceId = p.roadPieceId,
            locationId = p.locationId,
            offsetMm = p.offsetMm,
            lastUpdateMs = SystemClock.elapsedRealtime(),
        )
        publish()
    }

    private fun onTransition(addr: String) {
        val t = tele[addr] ?: return
        val transitions = t.transitions + 1
        val laps = if (SEGMENTS_PER_LAP > 0) transitions / SEGMENTS_PER_LAP else t.laps
        tele[addr] = t.copy(transitions = transitions, laps = laps)
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
