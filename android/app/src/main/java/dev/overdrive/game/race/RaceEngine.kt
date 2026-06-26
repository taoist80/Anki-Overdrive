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
import dev.overdrive.game.MetaGame
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
    val scanning: Boolean = false,       // a track-scan lap is in progress
    val scanComplete: Boolean = false,   // every car has mapped a full lap (ready to race)
    val lapTarget: Int = 3,              // laps to finish (RACE/BATTLE-RACE); 0 = endless (battle/koth)
    val finished: Boolean = false,       // the race has ended (a car reached lapTarget)
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
        const val SPEED_CEIL = 1000     // hard straight-line ceiling incl. SPEED upgrade (hardware-safe)
        const val CURVE_SPEED = 450     // mm/s ceiling while on a curve piece
        const val HOLD_DT_S = 0.08f     // weapon charge tick (matches the HUD's ~80ms hold loop)
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
        const val SCAN_SPEED = 250      // mm/s during the scan lap — brisk enough to not feel like a crawl,
                                        // while STAGE_BRAKE (hard brake the instant piece 33 is seen) keeps a
                                        // staged car from coasting past the start piece. Tune down if it overshoots.
        const val STAGE_BRAKE = 2500    // mm/s^2 hard brake when staging (vs gentle race ACCEL) — minimize overshoot
        const val SCAN_TIMEOUT_MS = 45_000L  // give up waiting for every car to complete its scan lap
        const val SCAN_MIN_TRANSITIONS = 10  // a car counts as "mapped" after this many segments (track-agnostic; finish piece may not be 33)
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
    private var playerSpeedMult = 1f         // player car's SPEED upgrade (straight-line only)
    private var scanning = false
    private var scanComplete = false
    private var scanStartedAt = 0L
    private var tickCount = 0
    private val staged = HashSet<String>()   // cars parked at the finish line during scan staging
    private val lapStartSeg = HashMap<String, Int>()  // each car's segment-count at its last lap boundary
    private var segsPerLap = 0                // segments in one physical lap, measured during the scan
    private var lapTarget = 3
    private var finished = false

    /** Set the laps-to-finish for the next race (from Match Setup). 0 = endless. */
    fun setLapTarget(n: Int) { lapTarget = n.coerceAtLeast(0) }
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

    /**
     * Drive a slow scan lap so each car's firmware maps/localizes the track before racing — this is
     * what the original game does at the start of a race, and skipping it (driving at race speed on an
     * unmapped track) is what made cars delocalize, spin and reverse. Completes when every car has
     * crossed the finish-line piece once (or [SCAN_TIMEOUT_MS]). No combat during scanning.
     */
    fun startScan() {
        arm(state.mode)            // snapshot the connected cars (player = designated)
        mgr.stopScan()             // free the BLE radio for driving
        scanning = true; scanComplete = false; finished = false; staged.clear()
        segsPerLap = 0; lapStartSeg.clear()
        scanStartedAt = SystemClock.elapsedRealtime()
        tele.keys.forEach { addr ->
            targetSpeed[addr] = SCAN_SPEED
            lastPiece[addr] = -1; lapStartSeg[addr] = 0
            tele[addr]?.let { tele[addr] = it.copy(laps = 0, transitions = 0) }
            mgr.setLaneOffset(addr, 0f)
        }
        Log.i(TAG, "Track scan: mapping with ${tele.size} cars @ $SCAN_SPEED mm/s")
        publish(running = false)
        main.removeCallbacks(control); main.post(control)
    }

    private fun finishScan(timedOut: Boolean) {
        scanning = false; scanComplete = true
        tele.keys.forEach { mgr.drive(it, 0) }
        main.removeCallbacks(control)
        Log.i(TAG, "Track scan complete${if (timedOut) " (timed out — cars may not be at the line)" else " — all cars staged at start"}")
        publish(running = false)
    }

    /** First car to reach the lap target ends the race; stops everyone and flags [finished] for Results. */
    private fun finishRace(winnerAddr: String) {
        finished = true
        Log.i(TAG, "Race.finish: ${tele[winnerAddr]?.name ?: winnerAddr} reached $lapTarget laps")
        stop()   // stops cars + control loop; publish(running=false) carries finished=true
    }

    fun start() {
        arm(state.mode)   // re-snapshot: include any car that finished connecting after Match Setup
        scanning = false; finished = false; mgr.stopScan()
        startedAt = SystemClock.elapsedRealtime()
        var aiIdx = 0
        tele.keys.forEach { addr ->
            targetSpeed[addr] = if (addr == playerAddr) PLAYER_START else (AI_BASE + (aiIdx++ % 4) * AI_SPREAD)
            lastPiece[addr] = -1; lapStartSeg[addr] = 0   // count race laps fresh from the staged start line
            mgr.setLaneOffset(addr, 0f)
        }
        // Arm the virtual combat model: equip the player's saved loadout, AI rivals a default.
        val roster = tele.values.map { Triple(it.address, it.address == playerAddr, GameData.byModelId(it.modelId)?.name) }
        val playerCarId = tele[playerAddr]?.modelId ?: -1
        // RACE and TIME TRIAL are weapon-free; all other modes (battle/battle-race/one-shot/koth/takeover) arm weapons.
        val weaponsEnabled = state.mode.lowercase().let { it != "race" && !it.startsWith("time") }
        // Per-car garage upgrades → in-race multipliers (speed handled here, the rest by combat).
        val prof = ProfileRepository.profile
        fun lvl(track: String) = prof.upgradeLevel("$playerCarId:$track")
        playerSpeedMult = MetaGame.speedMult(lvl("speed"))
        val mods = Combat.PlayerMods(
            damageMult = MetaGame.damageMult(lvl("weapons")),
            defenseMult = MetaGame.defenseMult(lvl("defense")),
            energyMult = MetaGame.energyMult(lvl("energy")),
        )
        combat.init(roster, prof.loadoutFor(playerCarId), weaponsEnabled, mods)
        Log.i(TAG, "Race.start: driving ${tele.size} cars, targets=$targetSpeed")
        publish(running = true)
        main.removeCallbacks(control)
        main.post(control)
    }

    fun stop() {
        targetSpeed.clear()
        scanning = false
        main.removeCallbacks(control)
        combat.stop()
        publish(running = false)
        assertAllStopped()   // BLE writes drop; re-assert drive(0) several times so cars actually halt
        Log.i(TAG, "Race.stop")
    }

    /**
     * Robustly bring every car to a halt: a single drive(0) can be lost over BLE (which is why a car
     * kept circling after the race ended), and the control loop is gone by now, so nothing retries it.
     * Re-send drive(0) a handful of times over ~1s. Each send is gated on the race not having restarted
     * so a queued stop can't brake a freshly-started race.
     */
    private fun assertAllStopped() {
        val addrs = tele.keys.toList()
        for (i in 0 until 8) {
            main.postDelayed({ if (!state.running && !scanning) addrs.forEach { mgr.drive(it, 0) } }, i * 150L)
        }
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

    /** Player holds a weapon button: charge that bay (drains energy live). [bay] = "attack"/"support"/"special". */
    fun holdBay(bay: String): Float = playerAddr?.let { combat.holdTick(it, bayOf(bay), HOLD_DT_S).also { publish() } } ?: -1f
    /** Player releases a weapon button: fire that bay scaled by the charge built up while holding. */
    fun fireBay(bay: String) { playerAddr?.let { combat.release(it, bayOf(bay), tele.values); publish() } }
    private fun bayOf(bay: String) = when (bay) { "attack" -> Bay.ATTACK; "support" -> Bay.SUPPORT; else -> Bay.SPECIAL }

    /** Curve-aware + combat-aware target: cap on curves, then scale by combat (boost/tractor/disabled). */
    private fun effectiveSpeed(addr: String, pieceId: Int): Int {
        if (combat.isDisabled(addr)) return 0
        val raw = targetSpeed[addr] ?: return 0
        // Player's SPEED upgrade boosts straight-line speed only; curves stay hard-capped (hardware safety).
        val base = if (addr == playerAddr) (raw * playerSpeedMult).toInt() else raw
        val capped = if (RoadPieces.isCurve(pieceId)) minOf(base, CURVE_SPEED) else minOf(base, SPEED_CEIL)
        return (capped * combat.speedFactor(addr)).toInt()
    }

    /** Control loop: re-send each car's (curve-aware) target so a dropped write can't stall a car. */
    private val control = object : Runnable {
        override fun run() {
            if (!state.running && !scanning) return
            if (scanning) {
                // Scan = map a lap, then stage each car at the finish line (onPosition adds to `staged`)
                // so every car starts the race from the same place. Complete when every car that has
                // LOCALIZED (ever reported a road piece) is staged — a car still at piece -1 isn't on
                // the track (dead/on charger) and must not hang the scan. 45s timeout is the backstop.
                val onTrack = tele.values.filter { it.roadPieceId >= 0 }
                val allStaged = onTrack.isNotEmpty() && onTrack.all { it.address in staged }
                val timedOut = SystemClock.elapsedRealtime() - scanStartedAt > SCAN_TIMEOUT_MS
                if (allStaged || timedOut) { finishScan(timedOut); return }
            } else {
                combat.tick(CONTROL_MS, tele.values)   // regen energy, expire effects, respawn, AI fire
            }
            tele.keys.forEach { addr ->
                val t = tele[addr]
                if (t?.offTrack == true || combat.isDisabled(addr) || (scanning && addr in staged)) {
                    mgr.drive(addr, 0)   // off-track, disabled, or staged at the line — hold stopped
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
            // Diagnostics: every ~3s, dump each car's live driving state (helps debug spin/off-track).
            if (tickCount++ % 12 == 0) {
                val phase = if (scanning) "SCAN" else "RACE"
                tele.values.forEach { c ->
                    Log.i(TAG, "$phase ${c.name} piece=${c.roadPieceId} seg=${c.transitions} lap=${c.laps} " +
                        "spd=${c.speedMmPerSec} off=${c.offTrack} curve=${c.onCurve} tgt=${targetSpeed[c.address]}")
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
            lastPiece[addr] = newPiece
            // Lap = crossing the finish piece (id 33), DEBOUNCED: the finish piece id re-registers more
            // than once per physical lap on real tracks (the over-count bug seen in the logs — 4-5
            // segment "laps" amid ~8-segment real laps). Require ~3/4 of a lap's worth of segments
            // (measured during the scan) between counts so a re-trigger can't add a phantom lap.
            if (newPiece == FINISH_PIECE_ID && prev != -1) {
                val gate = if (segsPerLap > 0) maxOf(4, segsPerLap * 3 / 4) else 4
                val gap = t.transitions - (lapStartSeg[addr] ?: 0)
                if (gap >= gate) {
                    laps += 1
                    lapStartSeg[addr] = t.transitions
                    if (scanning && segsPerLap == 0) segsPerLap = t.transitions  // first scan lap = segs/lap
                    Log.i(TAG, "${if (scanning) "SCAN" else "RACE"}.lap ${t.name} -> $laps @seg ${t.transitions} (segsPerLap=$segsPerLap)")
                } else {
                    Log.i(TAG, "${if (scanning) "SCAN" else "RACE"}.lap ${t.name} IGNORED finish re-trigger @seg ${t.transitions} (gap $gap < $gate)")
                }
            }
            // Scan staging: after a car completes one mapped lap, park it at the finish line so every
            // car starts the race from the same place (fair start), wherever it began the scan.
            val stageNow = scanning && addr !in staged && newPiece == FINISH_PIECE_ID && laps >= 1
            if (stageNow) {
                staged.add(addr); mgr.drive(addr, 0, STAGE_BRAKE)   // hard brake so it rests on the start piece
                Log.i(TAG, "Race.scan: ${t.name} staged at start line")
            } else if (state.running || (scanning && addr !in staged)) {
                mgr.drive(addr, effectiveSpeed(addr, newPiece), ACCEL) // react to curve immediately
            }
            // Race finish: the first car (the winner) to reach the lap target ends the race for everyone.
            if (state.running && !finished && lapTarget > 0 && laps >= lapTarget) finishRace(addr)
        }
        // Any position update means the car is on the track again — clear off-track + resume.
        if (t.offTrack && (state.running || (scanning && addr !in staged)))
            mgr.drive(addr, effectiveSpeed(addr, newPiece), ACCEL)

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
        state = RaceState(mode, running, elapsed(), cars, combat.playerHud(playerAddr), scanning, scanComplete, lapTarget, finished)
    }
}

/** Process-wide RaceEngine, lazily bound to the single CarManager. */
object RaceEngineHolder {
    val engine: RaceEngine by lazy { RaceEngine(AnkiCarManagerHolder.require()) }
}
