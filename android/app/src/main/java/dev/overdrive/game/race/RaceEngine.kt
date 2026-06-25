package dev.overdrive.game.race

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
 * Lap detection is approximate for now (segment-transition proxy) — it tightens in Phase 3 once the
 * track is scanned and the start/finish piece is known.
 */
class RaceEngine(private val mgr: CarManager) {

    companion object {
        const val MAX_SPEED = 1000      // mm/s at full throttle
        const val PLAYER_START = 600    // mm/s default when the race starts
        const val AI_SPEED = 560        // mm/s for AI rivals
        const val ACCEL = 1200          // mm/s^2
        const val LANE_STEP = 23f       // mm per lane nudge
        const val LANE_LIMIT = 68f      // mm max offset from centerline
        const val SEGMENTS_PER_LAP = 0  // 0 = unknown until track scan (Phase 3)
    }

    var state by mutableStateOf(RaceState())
        private set

    private val main = Handler(Looper.getMainLooper())
    private val tele = LinkedHashMap<String, CarTelemetry>()
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
        connected.forEach { c ->
            tele[c.address] = CarTelemetry(c.address, c.name, isPlayer = c.address == playerAddr)
        }
        startedAt = 0L
        publish(mode = mode, running = false)
    }

    fun start() {
        if (tele.isEmpty()) arm(state.mode)
        startedAt = SystemClock.elapsedRealtime()
        tele.keys.forEach { addr ->
            mgr.setLaneOffset(addr, 0f)
            mgr.drive(addr, if (addr == playerAddr) PLAYER_START else AI_SPEED, ACCEL)
        }
        publish(running = true)
        main.removeCallbacks(tick)
        main.post(tick)
    }

    fun stop() {
        tele.keys.forEach { mgr.drive(it, 0) }
        main.removeCallbacks(tick)
        publish(running = false)
    }

    /** HUD throttle: 0f..1f -> mm/s for the player's car. */
    fun setThrottle(fraction: Float) {
        val addr = playerAddr ?: return
        mgr.drive(addr, (fraction.coerceIn(0f, 1f) * MAX_SPEED).toInt(), ACCEL)
    }

    /** HUD lane buttons: shift the player's target offset by [deltaSteps] lane steps. */
    fun nudgeLane(deltaSteps: Int) {
        val addr = playerAddr ?: return
        val cur = tele[addr]?.targetOffsetMm ?: 0f
        val next = (cur + deltaSteps * LANE_STEP).coerceIn(-LANE_LIMIT, LANE_LIMIT)
        tele[addr]?.let { tele[addr] = it.copy(targetOffsetMm = next) }
        mgr.changeLane(addr, next)
        publish()
    }

    /** HUD weapon button — wired to the item/weapon system in Phase 4. */
    fun fireWeapon() { /* no-op until items exist */ }

    private val tick = object : Runnable {
        override fun run() {
            if (!state.running) return
            publish()                       // refresh elapsed time
            main.postDelayed(this, 200)
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
