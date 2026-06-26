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
import dev.overdrive.GameData
import dev.overdrive.Protocol
import dev.overdrive.data.model.Bay
import dev.overdrive.profile.ProfileRepository

/** Live state for one car in a race, driven by 0x27 position telemetry. */
data class CarTelemetry(
    val address: String,
    val name: String,
    val isPlayer: Boolean,
    val modelId: Int = -1,
    val speedMmPerSec: Int = 0,
    val roadPieceId: Int = -1,
    val locationId: Int = -1,
    val offsetMm: Float = 0f,
    val targetOffsetMm: Float = 0f,
    val onCurve: Boolean = false,
    val offTrack: Boolean = false,
    val transitions: Int = 0,
    val laps: Int = 0,
    val lastUpdateMs: Long = 0L,
    val health: Float = Combat.MAX_HEALTH,
    val energy: Float = Combat.MAX_ENERGY,
    val disabled: Boolean = false,
)

data class RaceState(
    val mode: String = "",
    val running: Boolean = false,
    val elapsedMs: Long = 0L,
    val cars: List<CarTelemetry> = emptyList(),
    val playerHud: Combat.PlayerHud? = null,
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
        const val FINISH_PIECE_ID = 33  // the unique start/finish piece ('B', isFinishLine) from tracks.json
        const val LANE_HEAL_MM = 12f    // re-send a lane change if the car is >this far from its target
    }

    var state by mutableStateOf(RaceState())
        private set

    private val main = Handler(Looper.getMainLooper())
    private val tele = LinkedHashMap<String, CarTelemetry>()
    private val targetSpeed = HashMap<String, Int>()
    private val lastPiece = HashMap<String, Int>()
    private var startedAt = 0L
    private var playerAddr: String? = null
    private var designatedPlayer: String? = null
    val combat = Combat()

    init {
        mgr.onPosition = ::onPosition
        mgr.onTransition = ::onTransition
        mgr.onDelocalized = ::onDelocalized
    }

    val playerAddress: String? get() = playerAddr

    /** Choose which connected car is the player's (P1). Falls back to first-connected if unset. */
    fun setPlayer(address: String?) { designatedPlayer = address }

    /** Snapshot connected cars into the race; the designated (or first) car is the player's. */
    fun arm(mode: String) {
        val connected = mgr.connectedCars()
        playerAddr = designatedPlayer?.takeIf { a -> connected.any { it.address == a } }
            ?: connected.firstOrNull()?.address
        tele.clear(); targetSpeed.clear(); lastPiece.clear()
        connected.forEach { c ->
            tele[c.address] = CarTelemetry(c.address, c.name, isPlayer = c.address == playerAddr, modelId = c.modelId)
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
        // Arm the virtual combat model: equip the player's saved loadout, AI rivals a default.
        val roster = tele.values.map { Triple(it.address, it.address == playerAddr, GameData.byModelId(it.modelId)?.name) }
        val playerCarId = tele[playerAddr]?.modelId ?: -1
        // RACE and TIME TRIAL are weapon-free; all other modes (battle/battle-race/one-shot/koth/takeover) arm weapons.
        val weaponsEnabled = state.mode.lowercase().let { it != "race" && !it.startsWith("time") }
        combat.init(roster, ProfileRepository.profile.loadoutFor(playerCarId), weaponsEnabled)
        Log.i(TAG, "Race.start: driving ${tele.size} cars, targets=$targetSpeed")
        publish(running = true)
        main.removeCallbacks(control)
        main.post(control)
    }

    fun stop() {
        targetSpeed.clear()
        tele.keys.forEach { mgr.drive(it, 0) }
        main.removeCallbacks(control)
        combat.stop()
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
        if (combat.steerBlocked(addr)) return                 // gravity/scrambler: controls locked
        val steps = if (combat.invertSteer(addr)) -deltaSteps else deltaSteps  // "hacked" inverts input
        val cur = tele[addr]?.targetOffsetMm ?: 0f
        val next = (cur + steps * LANE_STEP).coerceIn(-LANE_LIMIT, LANE_LIMIT)
        tele[addr]?.let { tele[addr] = it.copy(targetOffsetMm = next) }
        mgr.changeLane(addr, next, hSpeed = LANE_H_SPEED, hAccel = ACCEL)
        Log.i(TAG, "Race.nudgeLane: $addr -> ${next}mm")
        publish()
    }

    /** Fire the player's attack bay (HUD top trigger) — resolves a target + applies the weapon. */
    fun fireAttack() { playerAddr?.let { combat.fire(it, Bay.ATTACK, tele.values); publish() } }
    /** Fire the player's support bay (HUD bottom trigger) — shield/boost/etc. */
    fun fireSupport() { playerAddr?.let { combat.fire(it, Bay.SUPPORT, tele.values); publish() } }

    /** Curve-aware + combat-aware target: cap on curves, then scale by combat (boost/tractor/disabled). */
    private fun effectiveSpeed(addr: String, pieceId: Int): Int {
        if (combat.isDisabled(addr)) return 0
        val base = targetSpeed[addr] ?: return 0
        val capped = if (RoadPieces.isCurve(pieceId)) minOf(base, CURVE_SPEED) else base
        return (capped * combat.speedFactor(addr)).toInt()
    }

    /** Control loop: re-send each car's (curve-aware) target so a dropped write can't stall a car. */
    private val control = object : Runnable {
        override fun run() {
            if (!state.running) return
            combat.tick(CONTROL_MS, tele.values)   // regen energy, expire effects, respawn, AI fire
            tele.keys.forEach { addr ->
                val t = tele[addr]
                if (t?.offTrack == true || combat.isDisabled(addr)) {
                    mgr.drive(addr, 0)   // off the track, or disabled (weapon spin-out) — hold stopped
                } else {
                    val s = effectiveSpeed(addr, t?.roadPieceId ?: -1)
                    if (s > 0) mgr.drive(addr, s, ACCEL) else mgr.drive(addr, 0)
                    // Lane self-heal: changeLane is one-shot, so re-apply the target if a write dropped.
                    // Only on straights, and not while a weapon has locked this car's steering.
                    if (t != null && !t.onCurve && !combat.steerBlocked(addr) &&
                        kotlin.math.abs(t.offsetMm - t.targetOffsetMm) > LANE_HEAL_MM) {
                        mgr.changeLane(addr, t.targetOffsetMm, hSpeed = LANE_H_SPEED, hAccel = ACCEL)
                    }
                }
            }
            publish()
            main.postDelayed(this, CONTROL_MS)
        }
    }

    /**
     * A car reported 0x2b (left the track). The car spams this continuously while off, so we always
     * re-assert stop but only flag/log/recompose once on the off→on-track transition.
     */
    private fun onDelocalized(addr: String) {
        val t = tele[addr] ?: return
        mgr.drive(addr, 0)
        if (t.offTrack) return   // already flagged — ignore the repeats
        tele[addr] = t.copy(offTrack = true, speedMmPerSec = 0)
        Log.i(TAG, "Race.delocalized: $addr OFF TRACK")
        publish()
    }

    private fun onPosition(addr: String, p: Protocol.Position) {
        val t = tele[addr] ?: return
        val newPiece = p.roadPieceId
        val prev = lastPiece[addr] ?: -1
        var laps = t.laps

        if (newPiece >= 0 && newPiece != prev) {
            // Lap = crossing the unique finish-line piece (id 33). Piece ids repeat around a track,
            // so counting "return to first-seen id" over-counts — only the finish piece is singular.
            if (newPiece == FINISH_PIECE_ID && prev != -1) laps += 1
            lastPiece[addr] = newPiece
            if (state.running) mgr.drive(addr, effectiveSpeed(addr, newPiece), ACCEL) // react to curve immediately
        }
        // Any position update means the car is on the track again — clear off-track + resume.
        if (t.offTrack && state.running) mgr.drive(addr, effectiveSpeed(addr, newPiece), ACCEL)

        tele[addr] = t.copy(
            speedMmPerSec = p.speedMmPerSec,
            roadPieceId = newPiece,
            locationId = p.locationId,
            offsetMm = p.offsetMm,
            onCurve = RoadPieces.isCurve(newPiece),
            offTrack = false,
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
        val cars = tele.values.map { t ->
            val cc = combat.car(t.address)
            if (cc != null) t.copy(health = cc.health, energy = cc.energy, disabled = cc.disabled) else t
        }
        state = RaceState(mode, running, elapsed(), cars, combat.playerHud(playerAddr))
    }
}

/** Process-wide RaceEngine, lazily bound to the single CarManager. */
object RaceEngineHolder {
    val engine: RaceEngine by lazy { RaceEngine(AnkiCarManagerHolder.require()) }
}
