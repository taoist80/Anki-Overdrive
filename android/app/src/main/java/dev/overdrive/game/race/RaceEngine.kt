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
    val parsingFlags: Int = 0,           // 0x27 parse flags; bit 0x40 = driving wrong way
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
    val playerConnected: Boolean = true, // false when the player's car has dropped its BLE link (reconnecting)
    val discoveredTrack: List<Int> = emptyList(),  // ordered piece ids mapped so far (drives the scan-screen track view)
    val trackMapped: Boolean = false,    // the RoadNetwork ring closed into a clean loop (vs. just a lap driven, no closure)
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
 * drop). Curve safety (Phase 1): a per-piece speed cap from real 2.6 geometry ([RoadPieceGeometry]) is
 * applied with **look-ahead** — using the scanned track ([RoadNetwork]) and each car's estimated state
 * ([VehicleStateEstimator]), cars brake *before* a curve to the speed it can be taken at, instead of
 * reacting once already on it. Laps: counted per car when it returns to the piece it started the race on.
 */
class RaceEngine(private val mgr: CarManager) {

    companion object {
        private const val TAG = "OverdriveX"
        const val MAX_SPEED = 900       // mm/s at full throttle (curves auto-cap below)
        const val SPEED_CEIL = 1000     // hard straight-line ceiling incl. SPEED upgrade (hardware-safe)
        // Phase 1 curve safety: per-piece caps come from [RoadPieceGeometry] (2.6 parity
        // v=sqrt(0.87·g·R)). CURVE_SPEED_SCALE calibrates that theoretical lateral-grip ceiling to our
        // hardware so the sharpest mapped curve lands near the old known-safe 450 mm/s; gentler curves run
        // faster. LOOKAHEAD_DECEL is the braking rate the look-ahead assumes so cars slow *before* a curve
        // (kept ≤ ACCEL so the car can always meet the profile). Both are the primary Phase-4 tuning knobs.
        const val CURVE_SPEED_SCALE = 0.45f   // ×2.6 parity curve cap (sharpest ≈ 465 mm/s)
        const val LOOKAHEAD_DECEL = 600f      // mm/s² assumed for pre-curve braking
        const val HOLD_DT_S = 0.08f     // weapon charge tick (matches the HUD's ~80ms hold loop)
        const val PLAYER_START = 500    // mm/s default when the race starts
        const val AI_BASE = 600         // mm/s base for AI rivals, ×tier profile. (Lower than 4.0.4's ~1000 bots
                                        // because we curve-cap manually/reactively, not via the firmware planner.)
        const val AI_SPREAD = 40        // mm/s step between AI rivals
        const val ACCEL = 700           // mm/s^2 — matches 4.0.4 (gentle accel + firmware curve limits = stable)
        const val UTURN_COOLDOWN_MS = 3000L  // min gap between wrong-way u-turns (a u-turn takes ~1-2s to execute)
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
    private val lastUTurnAt = HashMap<String, Long>()  // debounce wrong-way u-turns per car
    private var segsPerLap = 0                // segments in one physical lap, measured during the scan
    private val roadNetwork = RoadNetwork()                       // ordered ring of pieces, built during the scan
    private val estimator = VehicleStateEstimator(roadNetwork)    // per-car continuous state for look-ahead
    private val planner = LocalPlanner(roadNetwork, estimator)    // Phase 2: cost-based A* that drives the AI cars
    private val planModes = HashMap<String, PlanMode>()           // per-AI-car plan mode (racing / lane / battle)
    private val battleAi = HashSet<String>()                      // armed AIs whose mode the combat FSM picks each tick
    private val aiTopSpeed = HashMap<String, Int>()               // per-AI-car top speed the planner may pick (mm/s)
    private val scanSeq = HashMap<String, ArrayList<Int>>()       // each car's ordered piece ids during the scan
    private val scanFp = HashMap<String, ArrayList<Long>>()       // each car's ordered piece fingerprints (pieceId<<8|locationId) for loop closure
    private val scanLapStartIdx = HashMap<String, Int>()          // scanSeq index of a car's first GATED finish crossing (ring lap start)
    private var discoveredTrack: List<Int> = emptyList()          // ordered piece ids mapped so far (scan-screen track view)
    private var lapTarget = 3
    private var finished = false
    private var winnerAddr: String? = null   // the car that actually reached the lap target first (authoritative)
    private var opponentProfile: DriverProfile? = null          // campaign opponent commander's driver stats
    private val aiProfiles = HashMap<String, DriverProfile>()    // per-AI-car profile (assigned in arm)
    /** The Tournament mission this race belongs to ("" = Open Play); carried to the results screen. */
    var campaignMissionId: String = ""
    /** The opponent commander's display name (for the victory "DEFEATED: …"), null in Open Play. */
    val opponentName: String? get() = opponentProfile?.displayName
    /**
     * Did the player finish first? The winner is whoever actually reached the lap target first (captured
     * in [finishRace]) — NOT recomputed from live standings, which can tie post-finish (a trailing car's
     * lap may still register after the race ends) and wrongly credit the player.
     */
    val playerWon: Boolean get() = winnerAddr?.let { it == playerAddr } ?: (state.standings.firstOrNull()?.isPlayer == true)

    /** Set the laps-to-finish for the next race (from Match Setup). 0 = endless. */
    fun setLapTarget(n: Int) { lapTarget = n.coerceAtLeast(0) }

    /** Set the Tournament opponent commander's driver profile (null = Open Play / generic rivals). */
    fun setCampaignOpponent(profile: DriverProfile?) { opponentProfile = profile }
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
        // Assign the campaign opponent commander to the first AI car (extra cars stay generic rivals),
        // and surface its name on that car so the HUD/standings read "Crashbot", "Roofus", etc.
        aiProfiles.clear()
        tele.keys.filter { it != playerAddr }.forEachIndexed { i, addr ->
            val p = if (i == 0) (opponentProfile ?: DriverProfile.DEFAULT) else DriverProfile.DEFAULT
            aiProfiles[addr] = p
            p.displayName?.let { nm -> tele[addr]?.let { tele[addr] = it.copy(name = nm) } }
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
        roadNetwork.clear(); estimator.clear(); scanSeq.clear(); scanFp.clear(); scanLapStartIdx.clear()   // rebuild the ring fresh
        discoveredTrack = emptyList()
        scanStartedAt = SystemClock.elapsedRealtime()
        tele.keys.forEach { addr ->
            targetSpeed[addr] = SCAN_SPEED
            lastPiece[addr] = -1; lapStartSeg[addr] = 0
            scanSeq[addr] = ArrayList(); scanFp[addr] = ArrayList()
            tele[addr]?.let { tele[addr] = it.copy(laps = 0, transitions = 0) }
            mgr.setLaneOffset(addr, 0f)
        }
        Log.i(TAG, "Track scan: mapping with ${tele.size} cars @ $SCAN_SPEED mm/s")
        publish(running = false)
        main.removeCallbacks(control); main.post(control)
    }

    /**
     * Pre-load a previously-confirmed track ring instead of mapping one (Phase 5c / 4.0.4 "USE TRACK").
     * Cars keep driving the scan lap to localize, but the planner is ready at once and — because the
     * staging gate is satisfied as soon as `roadNetwork.ready` — each car stages on its first finish
     * crossing rather than its second. Returns false if the ring is implausible (falls back to mapping).
     */
    fun useCachedTrack(ring: List<Int>): Boolean {
        if (!scanning) return false
        if (!roadNetwork.setRingFromLap(ring)) return false
        // A cached track skips the mapping lap that normally measures segs/lap, so seed it from the ring
        // size — otherwise the lap-count gate falls back to 4 and a finish re-trigger could add a phantom lap.
        segsPerLap = roadNetwork.size
        discoveredTrack = roadNetwork.pieceIds()
        Log.i(TAG, "Race.scan: using cached track — ${roadNetwork.size} pieces [${discoveredTrack.joinToString(",")}]")
        publish(running = false)
        return true
    }

    private fun finishScan(timedOut: Boolean) {
        scanning = false; scanComplete = true
        tele.keys.forEach { mgr.drive(it, 0) }
        main.removeCallbacks(control)
        Log.i(TAG, "Track scan complete${if (timedOut) " (timed out — cars may not be at the line)" else " — all cars staged at start"}")
        publish(running = false)
    }

    /** First car to reach the lap target ends the race; stops everyone and flags [finished] for Results. */
    private fun finishRace(addr: String) {
        finished = true
        winnerAddr = addr   // authoritative winner — captured before any trailing car's lap can register
        Log.i(TAG, "Race.finish: ${tele[addr]?.name ?: addr} reached $lapTarget laps")
        stop()   // stops cars + control loop; publish(running=false) carries finished=true
    }

    fun start() {
        arm(state.mode)   // re-snapshot: include any car that finished connecting after Match Setup
        scanning = false; finished = false; winnerAddr = null; mgr.stopScan()
        estimator.clear()   // fresh dead-reckoning from the staged start; the scan-built ring is kept
        Log.i(TAG, "Race.start: roadNetwork ${if (roadNetwork.ready) "READY (${roadNetwork.size} pieces) — look-ahead curve safety on" else "not mapped — per-piece curve cap only"}")
        startedAt = SystemClock.elapsedRealtime()
        planModes.clear(); battleAi.clear(); aiTopSpeed.clear()
        // RACE and TIME TRIAL are weapon-free; all other modes (battle/battle-race/one-shot/koth/takeover) arm weapons.
        val weaponsEnabled = state.mode.lowercase().let { it != "race" && !it.startsWith("time") }
        var aiIdx = 0
        tele.keys.forEach { addr ->
            targetSpeed[addr] = if (addr == playerAddr) PLAYER_START else {
                // AI top speed scaled by the car's commander profile (tier/level), plus a small spread. The
                // planner picks each AI's actual speed/lane per tick up to this ceiling; assign its plan mode
                // (racing / lane / battle) from the commander profile — battle only when weapons are armed.
                val profile = aiProfiles[addr] ?: DriverProfile.DEFAULT
                val top = (AI_BASE * profile.speedScale).toInt() + (aiIdx++ % 4) * AI_SPREAD
                aiTopSpeed[addr] = top
                planModes[addr] = PlanMode.forProfile(profile, weaponsEnabled)
                if (PlanMode.isCombatant(profile, weaponsEnabled)) battleAi.add(addr)   // FSM drives its mode each tick
                top
            }
            lastPiece[addr] = -1; lapStartSeg[addr] = 0   // count race laps fresh from the staged start line
            mgr.setLaneOffset(addr, 0f)
        }
        // Arm the virtual combat model: equip the player's saved loadout, AI rivals a default.
        val roster = tele.values.map { Triple(it.address, it.address == playerAddr, GameData.byModelId(it.modelId)?.name) }
        val playerCarId = tele[playerAddr]?.modelId ?: -1
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
        aiProfiles.forEach { (addr, p) -> combat.setProfile(addr, p) }   // AI rivals drive per their commander stats
        opponentProfile?.let { Log.i(TAG, "Race.start: opponent=${it.displayName} [${it.trait}] speed×${"%.2f".format(it.speedScale)} fireCD=${it.fireCooldownMs}ms weapons=${it.weaponsOn}") }
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

    /** Curve-aware + combat-aware target: look-ahead curve cap, then scale by combat (boost/tractor/disabled). */
    private fun effectiveSpeed(addr: String, pieceId: Int): Int {
        if (combat.isDisabled(addr)) return 0
        val raw = targetSpeed[addr] ?: return 0
        // Player's SPEED upgrade boosts straight-line speed only; curves stay capped (hardware safety).
        val base = if (addr == playerAddr) (raw * playerSpeedMult).toInt() else raw
        return (minOf(base, curveSafeCap(addr, pieceId)) * combat.speedFactor(addr)).toInt()
    }

    /**
     * Curve-safe ceiling (mm/s). When the track ring is mapped, this is the look-ahead clamp over the
     * upcoming pieces (brake before a curve, [VehicleStateEstimator.curveSafeSpeed]); otherwise it falls
     * back to the current piece's own cap. Replaces the old reactive flat curve cap.
     */
    private fun curveSafeCap(addr: String, pieceId: Int): Int =
        if (roadNetwork.ready) estimator.curveSafeSpeed(addr, LOOKAHEAD_DECEL, SPEED_CEIL, ::pieceCap)
        else pieceCap(pieceId)

    /** Effective cap for a single piece: the 2.6 parity curve cap scaled to our hardware, else [SPEED_CEIL]. */
    private fun pieceCap(pieceId: Int): Int {
        val raw = RoadPieceGeometry.rawCurveCapMmps(pieceId)
        return if (raw <= 0) SPEED_CEIL else (raw * CURVE_SPEED_SCALE).toInt().coerceAtMost(SPEED_CEIL)
    }

    /**
     * Phase 2: run the [LocalPlanner] for each AI car and apply its chosen speed + lane. The planned speed
     * becomes the car's target (the control loop still clamps it curve-safe via [effectiveSpeed] and scales
     * it by combat effects); the lane is issued directly. Silently keeps the car's current target until it
     * anchors on the mapped ring (plan returns null), so it never regresses the proven plumbing.
     */
    /**
     * Run the combat FSM ([CommanderBrain]) for one armed AI: compute its situation vs the player (signed
     * along-track gap, own health, whether the target is a disabled "sitting duck") and update its plan mode.
     */
    private fun selectCombatMode(addr: String) {
        val tgt = playerAddr ?: return
        val ts = estimator.state(tgt); if (ts.ringIndex < 0) return
        val s = estimator.state(addr); if (s.ringIndex < 0) return
        val lap = roadNetwork.lapLengthMm; if (lap <= 0f) return
        val fwd = roadNetwork.forwardGapMm(s.ringIndex, s.distAlongMm, ts.ringIndex, ts.distAlongMm)
        val signed = if (fwd <= lap / 2f) fwd else fwd - lap   // + = player ahead of me, − = player behind me
        val selfHealth = combat.car(addr)?.health ?: Combat.MAX_HEALTH
        val targetDisabled = combat.car(tgt)?.disabled == true
        planModes[addr] = CommanderBrain.selectMode(aiProfiles[addr] ?: DriverProfile.DEFAULT, selfHealth, targetDisabled, signed)
    }

    private fun planAiCars() {
        val cars = tele.values.toList()
        tele.keys.forEach { addr ->
            if (addr == playerAddr) return@forEach
            val t = tele[addr] ?: return@forEach
            if (t.offTrack || combat.isDisabled(addr)) return@forEach
            // Combat FSM (Phase 3 inc2): re-pick an armed AI's battle goal each tick from the live situation
            // vs the player (close-in / tail+fire / flank / finish-off / disengage).
            if (addr in battleAi) selectCombatMode(addr)
            val mode = planModes[addr] ?: return@forEach
            val top = aiTopSpeed[addr] ?: return@forEach
            // Battle commanders hunt the player (get into firing position behind them).
            val target = if (mode is PlanMode.Battle) playerAddr else null
            val res = planner.plan(addr, mode, top, cars, target) ?: return@forEach
            targetSpeed[addr] = res.speedMmps
            // Issue the planned lane change when it meaningfully differs from the current target — but not
            // while a weapon has locked this car's steering, nor mid-curve (lane changes are unreliable there).
            if (!combat.steerBlocked(addr) && !t.onCurve &&
                kotlin.math.abs(t.targetOffsetMm - res.laneOffsetMm) >= LANE_STEP) {
                tele[addr] = t.copy(targetOffsetMm = res.laneOffsetMm)
                mgr.changeLane(addr, res.laneOffsetMm, hSpeed = LANE_H_SPEED, hAccel = ACCEL)
            }
        }
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
                // 2.6 "Vehicle Disconnected" pause: if the player's car has dropped its BLE link, hold EVERY
                // car (so the AI can't finish or fight while you reconnect) until it's back. State carries
                // playerConnected=false so the HUD shows the pause modal; the race resumes on reconnect.
                val playerLinked = playerAddr?.let { pa -> mgr.connectedCars().any { it.address == pa } } ?: true
                if (!playerLinked) {
                    tele.keys.forEach { mgr.drive(it, 0, STAGE_BRAKE) }
                    publish(); main.postDelayed(this, CONTROL_MS); return
                }
                combat.tick(CONTROL_MS, tele.values)   // regen energy, expire effects, respawn, AI fire
                planAiCars()                           // Phase 2: the planner picks each AI's speed + lane
            }
            tele.keys.forEach { addr ->
                estimator.tick(addr, CONTROL_MS)   // dead-reckon distance-along-piece between 0x27 updates
                val t = tele[addr]
                if (t?.offTrack == true || combat.isDisabled(addr) || (scanning && addr in staged)) {
                    mgr.drive(addr, 0, STAGE_BRAKE)   // off-track, disabled, or staged — hard-brake to a crisp stop
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
                    val plan = if (c.address == playerAddr) "" else " plan=${planModes[c.address]?.let { it::class.simpleName } ?: "-"} laneTgt=${c.targetOffsetMm.toInt()}"
                    Log.i(TAG, "$phase ${c.name} piece=${c.roadPieceId} seg=${c.transitions} lap=${c.laps} " +
                        "spd=${c.speedMmPerSec} off=${c.offTrack} curve=${c.onCurve} flags=0x${Integer.toHexString(c.parsingFlags)} tgt=${targetSpeed[c.address]}$plan")
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

        estimator.onPosition(addr, p.speedMmPerSec, p.offsetMm)   // fold in the latest speed + lateral offset

        if (newPiece >= 0 && newPiece != prev) {
            lastPiece[addr] = newPiece
            // Capture each car's piece sequence + fingerprint during the scan and assemble the ring by
            // LOOP CLOSURE (2.6 TrackMapper style — finish-piece-independent), with the finish-to-finish
            // build as a fallback. The lead car's live sequence feeds the scan-screen track view.
            if (scanning) scanSeq[addr]?.let { seq ->
                seq.add(newPiece)
                scanFp[addr]?.add((newPiece.toLong() shl 8) or (p.locationId.toLong() and 0xff))
                // Tracks WITH a finish piece build the ring from a GATED finish-to-finish lap (in the lap
                // block below) — reliable against the finish re-trigger that was closing the ring early.
                // Finishless tracks fall back to loop closure on the start fingerprint.
                if (!roadNetwork.ready && FINISH_PIECE_ID !in seq &&
                    roadNetwork.buildByLoopClosure(seq, scanFp[addr] ?: emptyList())) {
                    Log.i(TAG, "Race.scan: road network mapped (loop) — ${roadNetwork.size} pieces [${roadNetwork.pieceIds().joinToString(",")}] from ${t.name}")
                }
                if (!roadNetwork.ready && seq.size > discoveredTrack.size) discoveredTrack = seq.toList()   // live view
            }
            if (roadNetwork.ready && discoveredTrack.size != roadNetwork.size) discoveredTrack = roadNetwork.pieceIds()
            estimator.onPieceChange(addr, newPiece)
            // Lap = crossing the finish piece (id 33), DEBOUNCED: the finish piece id re-registers more
            // than once per physical lap on real tracks (the over-count bug seen in the logs — 4-5
            // segment "laps" amid ~8-segment real laps). Require ~3/4 of a lap's worth of segments
            // (measured during the scan) between counts so a re-trigger can't add a phantom lap.
            if (newPiece == FINISH_PIECE_ID && prev != -1 && !finished) {   // no laps after the race has ended
                val gate = if (segsPerLap > 0) maxOf(4, segsPerLap * 3 / 4) else 4
                val gap = t.transitions - (lapStartSeg[addr] ?: 0)
                if (gap >= gate) {
                    laps += 1
                    lapStartSeg[addr] = t.transitions
                    if (scanning && segsPerLap == 0) segsPerLap = t.transitions  // first scan lap = segs/lap
                    Log.i(TAG, "${if (scanning) "SCAN" else "RACE"}.lap ${t.name} -> $laps @seg ${t.transitions} (segsPerLap=$segsPerLap)")
                    // Build the ring from one GATED finish-to-finish lap (the gate rejects the finish
                    // re-trigger that was closing the ring early): anchor at the first gated finish, then
                    // build the slice [1st finish .. just before 2nd] at the next gated crossing.
                    if (scanning && !roadNetwork.ready) scanSeq[addr]?.let { seq ->
                        val startIdx = scanLapStartIdx[addr]
                        if (startIdx == null) scanLapStartIdx[addr] = seq.size - 1
                        else if (roadNetwork.setRingFromLap(seq.subList(startIdx, seq.size - 1)))
                            Log.i(TAG, "Race.scan: road network mapped — ${roadNetwork.size} pieces/lap [${roadNetwork.pieceIds().joinToString(",")}] from ${t.name}")
                    }
                } else {
                    Log.i(TAG, "${if (scanning) "SCAN" else "RACE"}.lap ${t.name} IGNORED finish re-trigger @seg ${t.transitions} (gap $gap < $gate)")
                }
            }
            // Scan staging: park a car at the finish so every car starts the race from the same place.
            // Stage only once the ring is mapped OR this car has crossed the finish *twice* — i.e. it has
            // driven one full finish-to-finish lap, which the ring build needs. (Staging on the FIRST
            // crossing parked cars before a clean lap could be recorded, so the ring only mapped when a car
            // happened to start just before the finish — otherwise Phase 2 silently fell back to no planner.)
            val finishCrossings = if (scanning) (scanSeq[addr]?.count { it == FINISH_PIECE_ID } ?: 0) else 0
            val stageNow = scanning && addr !in staged && newPiece == FINISH_PIECE_ID &&
                (roadNetwork.ready || finishCrossings >= 2)
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

        // Wrong-way correction — ported from 4.0.4 CarManager._position_update: only u-turn when the car
        // crosses the START/FINISH piece (33/34) parsing in reverse, i.e. it's genuinely going the wrong
        // way around the track. (Firing on every reverse-flagged piece spuriously flipped correctly-driving
        // cars — the cause of the "both cars backwards, never righting" chaos.) Gated to the start piece it
        // fires at most ~once per lap and self-clears once the car comes about.
        if (state.running && !scanning && (newPiece == FINISH_PIECE_ID || newPiece == 34)) {
            val reverse = (p.parsingFlags and (Protocol.PARSEFLAGS_REVERSE_DRIVING or Protocol.PARSEFLAGS_REVERSE_PARSING)) != 0
            val now = SystemClock.elapsedRealtime()
            if (reverse && now - (lastUTurnAt[addr] ?: 0L) > UTURN_COOLDOWN_MS) {
                lastUTurnAt[addr] = now
                mgr.uTurn(addr)
                Log.i(TAG, "Race.uTurn ${t.name} crossed start piece $newPiece in REVERSE (flags=0x${Integer.toHexString(p.parsingFlags)}) -> u-turn")
            }
        }

        tele[addr] = t.copy(
            speedMmPerSec = p.speedMmPerSec,
            roadPieceId = newPiece,
            locationId = p.locationId,
            offsetMm = p.offsetMm,
            onCurve = RoadPieceGeometry.isCurve(newPiece),
            offTrack = false,
            laps = laps,
            parsingFlags = p.parsingFlags,
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
        val connected = playerAddr?.let { pa -> mgr.connectedCars().any { it.address == pa } } ?: true
        state = RaceState(mode, running, elapsed(), cars, combat.playerHud(playerAddr), scanning, scanComplete, lapTarget, finished, connected, discoveredTrack, roadNetwork.ready)
    }
}

/** Process-wide RaceEngine, lazily bound to the single CarManager. */
object RaceEngineHolder {
    val engine: RaceEngine by lazy { RaceEngine(AnkiCarManagerHolder.require()) }
}
