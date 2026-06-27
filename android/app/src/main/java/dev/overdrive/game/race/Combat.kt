package dev.overdrive.game.race

import android.os.SystemClock
import android.util.Log
import dev.overdrive.data.ItemRepository
import dev.overdrive.data.model.Bay
import dev.overdrive.data.model.GameItem
import dev.overdrive.data.model.Interaction

/**
 * Client-side virtual combat — the model the original game ran in `libDriveEngine` and re-expressed
 * over BLE as speed/lane commands (the cars carry no weapons). When a bay fires we resolve a target
 * from 0x27 telemetry (track progress via segment-transition count + lane offset), apply damage and
 * the item's status effects, and surface per-car modifiers — [speedFactor], [steerBlocked],
 * [invertSteer], [isDisabled] — that [RaceEngine] folds into the cars it's already driving.
 *
 * **Every weapon/support item behaves per its real values** ([GameItem.combat], resolved verbatim from
 * items.json). The three [Interaction] styles drive the whole fire model:
 *  - HOLD       (machine gun / shield / boost / tractor / scrambler / e-brake / reverse): sustained
 *               while held — drains `energyPerS`, builds `heat`, overheats then cools. Damage weapons
 *               apply `sustainedDps × dt`; support items keep their effect alive while held.
 *  - CHARGE     (sniper / EMP / mine): hold to wind up to `chargeLimitS` (draining `energyChargePerS`),
 *               release to fire scaled by how full the charge was; cooldown lerps min→max with charge.
 *  - SINGLESHOT (shotgun / horn / gravity trap / pulse ram): instant tap, fixed `rechargeS`.
 *
 * Health/energy/heat/cooldowns are virtual state here; "disabled" (health ≤ 0) forces a brief spin-out
 * + respawn and increments a death counter (consumed by the NO_DEATHS campaign objective).
 */
class Combat {

    companion object {
        private const val TAG = "OverdriveX"
        const val MAX_HEALTH = 100f
        const val MAX_ENERGY = 100f
        const val ENERGY_REGEN_PER_S = 7f       // energy/sec passive regen (PAUSED briefly during active use,
                                                // else it swamps the real per-item drains of 1.5–5/s)
        const val REGEN_PAUSE_MS = 900L         // no passive regen for this long after a bay drains energy
        const val RESPAWN_MS = 2600L            // spin-out duration when disabled
        const val MIN_COOLDOWN_S = 0.4          // floor so 0-recharge items can't be spammed
        const val AI_FIRE_PERIOD_MS = 2200L     // how often an AI rival tries to fire
        const val GRAVITY_FACTOR = 0.08f        // gravity_beam: near-stop (also no steering)
        const val TRACTOR_FALLBACK = 0.42f      // tractor speed mult if the item carries no ratio
        const val HOLD_WINDOW_MS = 180L         // a held support effect (shield/boost) stays live this long per tick
        const val OVERHEAT_MIN_COOL_S = 0.6f    // floor on the overheat-recovery lockout
        // Default loadout when nothing is equipped, so combat always works out of the box.
        const val DEFAULT_ATTACK = "base_machine_gun"
        const val DEFAULT_SUPPORT = "base_shield"
    }

    /** A timed status effect on a car. [speedMul] ≥ 0 overrides the effect's default speed scaling (tractor). */
    class ActiveEffect(val effectId: String, val untilMs: Long, val speedMul: Float = -1f)

    inner class CombatCar(val address: String, val isPlayer: Boolean, val vehicleName: String?) {
        var health = MAX_HEALTH
        var energy = MAX_ENERGY
        var deaths = 0
        var disabledUntil = 0L
        var boostUntil = 0L
        var boostSpeedAdd = 0f                     // the equipped boost's real speed addition
        var shieldUntil = 0L
        var shieldBlock = 1f                       // the equipped shield's real block fraction
        var selfSlowUntil = 0L                     // e-brake / reverse-drive: brief self slow while held
        var selfSlowMul = 1f
        var lastAiFireAt = 0L
        var lastUseAt = 0L                          // last time any bay drained energy (gates passive regen)
        var lastHitAt = 0L                          // last time this car took damage (AI reactive support)
        var profile: DriverProfile? = null          // AI driver stats (campaign opponent commander); null = generic
        val equipped = HashMap<Bay, String>()      // bay -> itemId
        val readyAt = HashMap<Bay, Long>()         // bay -> cooldown/overheat-until ms
        val charge = HashMap<Bay, Float>()         // bay -> 0..1 charge accrued (CHARGE weapons)
        val heat = HashMap<Bay, Float>()           // bay -> 0..1 heat accrued (HOLD weapons)
        val holdStartAt = HashMap<Bay, Long>()     // when the current hold began (for the engage log)
        val holdStartEnergy = HashMap<Bay, Float>()
        val effects = ArrayList<ActiveEffect>()
        val disabled: Boolean get() = SystemClock.elapsedRealtime() < disabledUntil
    }

    /** The player car's garage-upgrade multipliers (1f = no upgrade). AI rivals always use 1f. */
    data class PlayerMods(val damageMult: Float = 1f, val defenseMult: Float = 1f, val energyMult: Float = 1f)

    private val cars = LinkedHashMap<String, CombatCar>()
    var running = false; private set
    /** Whether this game mode allows weapons (RACE / TIME TRIAL are weapon-free). */
    var weaponsEnabled = true; private set
    private var playerMods = PlayerMods()

    /** Snapshot the race roster: equip the player's loadout, AI rivals a sensible default. */
    fun init(
        roster: List<Triple<String, Boolean, String?>>,
        playerLoadout: Map<String, String>,
        weaponsEnabled: Boolean,
        playerMods: PlayerMods = PlayerMods(),
    ) {
        cars.clear()
        this.weaponsEnabled = weaponsEnabled
        this.playerMods = playerMods
        roster.forEach { (addr, isPlayer, vehName) ->
            val c = CombatCar(addr, isPlayer, vehName)
            val defaults = ItemRepository.defaultLoadout(vehName)
            if (isPlayer) {
                c.equipped[Bay.ATTACK] = playerLoadout["attack"] ?: defaults["attack"] ?: DEFAULT_ATTACK
                c.equipped[Bay.SUPPORT] = playerLoadout["support"] ?: defaults["support"] ?: DEFAULT_SUPPORT
                playerLoadout["special"]?.let { c.equipped[Bay.SPECIAL] = it }
            } else {
                c.equipped[Bay.ATTACK] = defaults["attack"] ?: DEFAULT_ATTACK
                c.equipped[Bay.SUPPORT] = defaults["support"] ?: DEFAULT_SUPPORT
            }
            cars[addr] = c
        }
        running = true
    }

    fun stop() { running = false; cars.clear() }
    fun car(addr: String): CombatCar? = cars[addr]

    /** Assign an AI car its commander driver profile (campaign opponent) — drives its fire behaviour. */
    fun setProfile(addr: String, profile: DriverProfile) { cars[addr]?.profile = profile }

    // ---- per-car driving modifiers (queried by RaceEngine) -------------------
    fun isDisabled(addr: String): Boolean = cars[addr]?.disabled == true

    /** Multiplier on a car's target speed from its active effects + boost/brake (queried by RaceEngine). */
    fun speedFactor(addr: String): Float {
        val c = cars[addr] ?: return 1f
        if (c.disabled) return 0f
        var f = 1f
        val now = SystemClock.elapsedRealtime()
        if (now < c.boostUntil) f *= (1f + c.boostSpeedAdd)        // real boost_speed_addition
        if (now < c.selfSlowUntil) f *= c.selfSlowMul             // e-brake / reverse
        for (e in c.effects) {
            val eff = ItemRepository.effect(e.effectId) ?: continue
            if (!eff.allowSpeedChange) f *= when {
                e.speedMul >= 0f -> e.speedMul                    // real tractor_beam_ratio
                !eff.allowSteering -> GRAVITY_FACTOR              // gravity beam: near stop
                else -> TRACTOR_FALLBACK
            }
        }
        return f
    }

    /** Steering suppressed (gravity/scrambler) — RaceEngine won't re-apply lane for this car. */
    fun steerBlocked(addr: String): Boolean {
        val c = cars[addr] ?: return false
        if (c.disabled) return true
        return c.effects.any { ItemRepository.effect(it.effectId)?.allowSteering == false }
    }

    /** Lane input inverted ("hacked") — RaceEngine flips the player's lane nudges. */
    fun invertSteer(addr: String): Boolean =
        cars[addr]?.effects?.any { ItemRepository.effect(it.effectId)?.invertSteering == true } == true

    // ---- firing --------------------------------------------------------------
    private fun cmb(itemId: String?) = ItemRepository.item(itemId)?.combat

    /**
     * Instant fire of [bay] (AI rivals + any non-held use). Resolves the interaction style: HOLD applies
     * a one-second sustained slice, CHARGE fires a full-charge shot, SINGLESHOT one shot. Returns true if
     * it actually fired (ready + energy).
     */
    fun fire(addr: String, bay: Bay, telemetry: Collection<CarTelemetry>): Boolean {
        if (!weaponsEnabled) return false
        val c = cars[addr] ?: return false
        if (c.disabled || !running) return false
        val item = ItemRepository.item(c.equipped[bay] ?: return false) ?: return false
        val k = item.combat
        val now = SystemClock.elapsedRealtime()
        if (now < (c.readyAt[bay] ?: 0L)) return false
        return when (k.interaction) {
            Interaction.HOLD -> {
                val cost = (k.energyPerS.toFloat()).coerceAtLeast(4f)
                if (c.energy < cost) return false
                c.energy -= cost; c.lastUseAt = now
                applyBaySlice(c, item, telemetry, dtS = 1f)               // a 1s burst
                c.readyAt[bay] = now + (maxOf(k.rechargeS, MIN_COOLDOWN_S) * 1000).toLong()
                true
            }
            Interaction.CHARGE -> { fireCharge(c, bay, item, telemetry, charge = 1f, now); true }
            Interaction.SINGLESHOT -> {
                val cost = k.baseEnergyCost.toFloat()
                if (c.energy < cost) return false
                c.energy -= cost
                fireShot(c, item, telemetry, now, scale = 1f)
                c.readyAt[bay] = now + (maxOf(k.rechargeS, MIN_COOLDOWN_S) * 1000).toLong()
                true
            }
        }
    }

    /**
     * Player holds a bay's button (~80ms cadence). Behaviour by interaction style; returns the bay's
     * fill 0..1 (CHARGE→charge, HOLD→heat) or -1 if it can't engage (cooling / overheated / disabled /
     * weapons-free / SINGLESHOT which fires on release instead).
     */
    fun holdTick(addr: String, bay: Bay, dtS: Float): Float {
        if (!weaponsEnabled) return -1f
        val c = cars[addr] ?: return -1f
        if (c.disabled || !running) return -1f
        val item = ItemRepository.item(c.equipped[bay] ?: return -1f) ?: return -1f
        val k = item.combat
        val now = SystemClock.elapsedRealtime()
        if (now < (c.readyAt[bay] ?: 0L)) return -1f                      // cooling / overheated
        return when (k.interaction) {
            Interaction.HOLD -> {
                if (bay !in c.holdStartAt) { c.holdStartAt[bay] = now; c.holdStartEnergy[bay] = c.energy }
                if (c.energy <= 0f) return c.heat[bay] ?: 0f             // out of energy: hold can't sustain
                c.lastUseAt = now                                       // pause passive regen while engaged
                c.energy = (c.energy - k.energyPerS.toFloat() * dtS).coerceAtLeast(0f)
                applyBaySlice(c, item, telemetry = lastTelemetry, dtS = dtS)
                val rate = if (k.heatupRate > 0) k.heatupRate.toFloat() else 0.5f
                var h = (c.heat[bay] ?: 0f) + rate * dtS
                if (h >= 1f) {                                            // overheat → lockout while it cools
                    h = 1f
                    val coolRate = if (k.cooldownRate > 0) k.cooldownRate.toFloat() else 0.25f
                    c.readyAt[bay] = now + (maxOf(1f / coolRate, OVERHEAT_MIN_COOL_S) * 1000).toLong()
                }
                c.heat[bay] = h
                h
            }
            Interaction.CHARGE -> {
                if (bay !in c.holdStartAt) { c.holdStartAt[bay] = now; c.holdStartEnergy[bay] = c.energy }
                if (c.energy <= 0f) return c.charge[bay] ?: 0f
                c.lastUseAt = now                                       // pause passive regen while charging
                c.energy = (c.energy - k.energyChargePerS.toFloat() * dtS).coerceAtLeast(0f)
                val lim = if (k.chargeLimitS > 0) k.chargeLimitS.toFloat() else 1.5f
                val ch = ((c.charge[bay] ?: 0f) + dtS / lim).coerceAtMost(1f)
                c.charge[bay] = ch
                ch
            }
            Interaction.SINGLESHOT -> -1f                                 // tap weapon: fires on release
        }
    }

    /**
     * Player releases a bay's button. HOLD just stops (damage was applied live; heat cools in [tick]).
     * CHARGE fires scaled by the charge built (cooldown lerps min→max with charge). SINGLESHOT fires once.
     * Returns true if a shot was fired (HOLD returns whether it was engaged).
     */
    fun release(addr: String, bay: Bay, telemetry: Collection<CarTelemetry>): Boolean {
        if (!weaponsEnabled) return false
        val c = cars[addr] ?: return false
        if (c.disabled || !running) { c.charge.remove(bay); return false }
        val item = ItemRepository.item(c.equipped[bay] ?: return false) ?: return false
        val k = item.combat
        val now = SystemClock.elapsedRealtime()
        val holdStarted = c.holdStartAt.remove(bay)
        val holdE0 = c.holdStartEnergy.remove(bay)
        return when (k.interaction) {
            Interaction.HOLD -> {                                        // sustained — log the engagement
                if (holdStarted != null) {
                    val durS = (now - holdStarted) / 1000f
                    val drained = ((holdE0 ?: c.energy) - c.energy).coerceAtLeast(0f)
                    val eff = when (item.type) {
                        "shield" -> "shield up"; "boost" -> "boost"; "tractor_beam" -> "tractor"
                        "scrambler" -> "scramble"; "e-brake" -> "brake"; "reverse_drive" -> "reverse"; else -> item.type
                    }
                    Log.i(TAG, "hold ${who(c)} ${item.id} [$eff] ${"%.1f".format(durS)}s, energy -${"%.1f".format(drained)} (now ${c.energy.toInt()})")
                }
                true
            }
            Interaction.CHARGE -> {
                val ch = (c.charge.remove(bay) ?: 0f).coerceAtLeast(0.15f)
                if (now < (c.readyAt[bay] ?: 0L)) return false
                if (ch <= 0.16f) {                                       // quick tap: pay the base cost up front
                    val cost = k.baseEnergyCost.toFloat()
                    if (c.energy < cost) return false
                    c.energy -= cost
                }                                                        // held shot already drained via holdTick
                fireCharge(c, bay, item, telemetry, ch, now)
                true
            }
            Interaction.SINGLESHOT -> {
                if (now < (c.readyAt[bay] ?: 0L)) return false
                val cost = k.baseEnergyCost.toFloat()
                if (c.energy < cost) return false
                c.energy -= cost
                fireShot(c, item, telemetry, now, scale = 1f)
                c.readyAt[bay] = now + (maxOf(k.rechargeS, MIN_COOLDOWN_S) * 1000).toLong()
                true
            }
        }
    }

    private var lastTelemetry: Collection<CarTelemetry> = emptyList()

    /** A held weapon's per-tick effect: sustained damage (MG/scrambler) or a refreshed support effect. */
    private fun applyBaySlice(c: CombatCar, item: GameItem, telemetry: Collection<CarTelemetry>, dtS: Float) {
        val k = item.combat
        val now = SystemClock.elapsedRealtime()
        when {
            isSelfSupport(item) -> applySelf(c, item, now)
            k.sustainedDps > 0 -> {                                      // machine gun, scrambler
                resolveTargets(c, item, telemetry).forEach {
                    applyDamage(c, it, item, k.sustainedDps * dtS, now)
                }
            }
            else -> {                                                    // tractor beam etc. (effect-only hold)
                resolveTargets(c, item, telemetry).forEach { applyDamage(c, it, item, 0.0, now) }
            }
        }
    }

    /** A charged shot (sniper/EMP per charged-seconds; mine flat, charge-scaled). */
    private fun fireCharge(c: CombatCar, bay: Bay, item: GameItem, telemetry: Collection<CarTelemetry>, charge: Float, now: Long) {
        c.lastUseAt = now
        val k = item.combat
        val dmg = if (k.chargedDps > 0) k.chargedDps * k.chargeLimitS * charge
                  else k.flatDamage * (0.4 + 0.6 * charge)
        resolveTargets(c, item, telemetry).forEach { applyDamage(c, it, item, dmg, now) }
        val cd = lerp(maxOf(k.cooldownMinS, MIN_COOLDOWN_S), maxOf(k.cooldownMaxS, k.cooldownMinS), charge)
        c.readyAt[bay] = now + (cd * 1000).toLong()
        Log.i(TAG, "fire ${who(c)} ${item.id} CHARGE=${"%.2f".format(charge)} dmg=${"%.1f".format(dmg)} cd=${"%.1f".format(cd)}s")
    }

    /** A single-shot weapon (shotgun/horn/gravity/pulse-ram): flat damage + its effects, once. */
    private fun fireShot(c: CombatCar, item: GameItem, telemetry: Collection<CarTelemetry>, now: Long, scale: Float) {
        c.lastUseAt = now
        val targets = resolveTargets(c, item, telemetry)
        targets.forEach { applyDamage(c, it, item, item.combat.flatDamage * scale, now) }
        Log.i(TAG, "fire ${who(c)} ${item.id} SHOT dmg=${item.combat.flatDamage} -> ${targets.size} target(s)")
    }

    /** Self-affecting support items: shield / boost / e-brake / reverse — using their real values. */
    private fun isSelfSupport(item: GameItem): Boolean =
        item.targeter?.type == "self" || item.type in setOf("shield", "boost", "e-brake", "reverse_drive")

    private fun applySelf(c: CombatCar, item: GameItem, now: Long) {
        val k = item.combat
        when (item.type) {
            "boost" -> { c.boostUntil = now + HOLD_WINDOW_MS; c.boostSpeedAdd = k.boostSpeedAdd.toFloat() }
            "shield" -> { c.shieldUntil = now + HOLD_WINDOW_MS; c.shieldBlock = (if (k.shieldBlockPct > 0) k.shieldBlockPct else 1.0).toFloat() }
            "e-brake" -> { c.selfSlowUntil = now + HOLD_WINDOW_MS; c.selfSlowMul = 0.12f }
            "reverse_drive" -> { c.selfSlowUntil = now + HOLD_WINDOW_MS; c.selfSlowMul = 0.18f }
        }
    }

    /** Effects an item inflicts on a hit target: explicit source_effects + the one implied by type. */
    private fun effectsOf(item: GameItem): List<String> =
        item.sourceEffects + listOfNotNull(
            when (item.type) {
                "tractor_beam" -> "tractor_beam"
                "gravity_trap", "gravity_trap_rage" -> "gravity_beam"
                "scrambler" -> "invert_steering"
                else -> null
            }
        )

    private fun applyDamage(from: CombatCar, targetAddr: String, item: GameItem, rawDmg: Double, now: Long) {
        val t = cars[targetAddr] ?: return
        if (t.disabled) return
        var dmg = rawDmg.toFloat()
        if (from.isPlayer) dmg *= playerMods.damageMult                  // player's WEAPONS upgrade
        if (now < t.shieldUntil) dmg *= (1f - t.shieldBlock)             // real shield block %
        if (t.isPlayer) dmg *= playerMods.defenseMult                    // player's DEFENSE upgrade (incoming)
        if (dmg > 0f) { t.health -= dmg; t.lastHitAt = now }
        // status effects (tractor carries its real speed ratio; others use their effect defaults)
        for (effId in effectsOf(item)) {
            val eff = ItemRepository.effect(effId) ?: continue
            val durMs = ((if (eff.durationS > 0) eff.durationS else 1.2) * 1000).toLong()
            val speedMul = if (item.type == "tractor_beam" && item.combat.tractorRatio > 0)
                item.combat.tractorRatio.toFloat() else -1f
            t.effects.add(ActiveEffect(effId, now + durMs, speedMul))
        }
        if (t.health <= 0f) {
            t.health = 0f
            t.disabledUntil = now + RESPAWN_MS
            t.deaths += 1
            t.effects.clear()
            Log.i(TAG, "${who(t)} DISABLED by ${from.address} (${item.id}); deaths=${t.deaths}")
        }
    }

    /**
     * Resolve who a weapon hits, using segment-transition count as a track-progress scalar and lane
     * offset for spread. Range scales with the item's real targeter (rectangle length, proximity radius,
     * forward max_distance). Proximity/AOE hit everyone within range; others hit the single nearest.
     */
    private fun resolveTargets(from: CombatCar, item: GameItem, telemetry: Collection<CarTelemetry>): List<String> {
        val me = telemetry.firstOrNull { it.address == from.address } ?: return emptyList()
        val tg = item.targeter
        val rangeSegments = when (tg?.type) {
            "proximity" -> if (tg.radius >= 0.3) 2 else 1                 // emp/scrambler wide vs mine tight
            "rectangle" -> if (tg.length >= 1.5) 3 else 1                 // sniper long vs shotgun short
            "forward_track_distance" -> if (tg.maxDistance >= 2.0) 3 else 1 // tractor far vs pulse-ram close
            "self" -> return emptyList()
            else -> 2
        }
        // lane tolerance from the rectangle half-width (tight beams need lane alignment); wide AOE ignores lane
        val laneTol = when {
            tg?.type == "proximity" -> Float.MAX_VALUE
            (tg?.halfWidth ?: 0.0) in 0.0001..0.1 -> 40f
            else -> Float.MAX_VALUE
        }
        val candidates = telemetry.filter { o ->
            o.address != from.address && cars[o.address]?.disabled != true &&
                kotlin.math.abs(o.transitions - me.transitions) <= rangeSegments &&
                kotlin.math.abs(o.offsetMm - me.offsetMm) <= laneTol
        }
        if (candidates.isEmpty()) return emptyList()
        val multi = tg?.type == "proximity" || tg?.singleTarget == false
        return if (multi) candidates.map { it.address }
        else listOf(candidates.minByOrNull { kotlin.math.abs(it.transitions - me.transitions) }!!.address)
    }

    /**
     * Per-tick upkeep: regen energy, cool weapon heat, expire effects, respawn disabled cars, AI fire.
     * [dtMs] is the control-loop cadence.
     */
    fun tick(dtMs: Long, telemetry: Collection<CarTelemetry>) {
        if (!running) return
        lastTelemetry = telemetry
        val now = SystemClock.elapsedRealtime()
        val dt = dtMs / 1000f
        for (c in cars.values) {
            if (c.effects.isNotEmpty()) c.effects.removeAll { now >= it.untilMs }
            if (c.health <= 0f && now >= c.disabledUntil) c.health = MAX_HEALTH
            // cool any heated HOLD weapons whose button isn't being held (held weapons set readyAt past)
            if (c.heat.isNotEmpty()) {
                for ((bay, h) in c.heat) {
                    if (h <= 0f) continue
                    val k = cmb(c.equipped[bay]) ?: continue
                    val rate = if (k.cooldownRate > 0) k.cooldownRate.toFloat() else 0.25f
                    c.heat[bay] = (h - rate * dt).coerceAtLeast(0f)
                }
            }
            // Passive regen — paused briefly after any use so real drains (shield 1.5/s, boost 5/s) actually register.
            if (!c.disabled && now - c.lastUseAt >= REGEN_PAUSE_MS) {
                val regen = ENERGY_REGEN_PER_S * (if (c.isPlayer) playerMods.energyMult else 1f)
                c.energy = (c.energy + regen * dt).coerceAtMost(MAX_ENERGY)
            }
            // AI rivals fire per their 2.6 driver profile: `purerace` never fires; the aggressive trait
            // sets the item-use cadence (lazy ~9s … ultra ~1.25s — real 2.6 cooldowns); defensive cars
            // pop support/shield when they have the energy.
            if (!c.isPlayer && !c.disabled) {
                val prof = c.profile ?: DriverProfile.DEFAULT
                if (prof.weaponsOn && now - c.lastAiFireAt >= prof.fireCooldownMs && c.energy > MAX_ENERGY * 0.4f) {
                    c.lastAiFireAt = now
                    // Situational item choice (2.6 `equip_best_support_item`): pop SUPPORT (shield/boost) when
                    // hurt or freshly hit — any trait reacts defensively — or when a defensive commander is
                    // flush with energy; otherwise attack. Falls back to attack if no support is equipped.
                    val hurtOrHit = c.health < MAX_HEALTH * 0.45f || now - c.lastHitAt < 2500L
                    val useSupport = c.equipped[Bay.SUPPORT] != null &&
                        (hurtOrHit || (prof.defensive && c.energy > MAX_ENERGY * 0.7f))
                    fire(c.address, if (useSupport) Bay.SUPPORT else Bay.ATTACK, telemetry)
                }
            }
        }
    }

    // ---- HUD -----------------------------------------------------------------
    /**
     * HUD snapshot for one bay. [fillFrac] is the bay's own meter (CHARGE→charge, HOLD→heat,
     * SINGLESHOT→cooldown remaining); [interaction] lets the HUD label the affordance.
     */
    data class BayHud(
        val bay: String, val itemId: String?, val itemName: String, val ready: Boolean,
        val cooldownFrac: Float, val interaction: String, val fillFrac: Float, val active: Boolean,
    )
    data class PlayerHud(val health: Float, val energy: Float, val disabled: Boolean, val bays: List<BayHud>)

    fun playerHud(playerAddr: String?): PlayerHud? {
        if (!weaponsEnabled) return null   // RACE / TIME TRIAL: no weapon HUD
        val c = cars[playerAddr ?: return null] ?: return null
        val now = SystemClock.elapsedRealtime()
        fun bayHud(bay: Bay, label: String): BayHud {
            val id = c.equipped[bay]
            val item = ItemRepository.item(id)
            val k = item?.combat
            val active = when (item?.type) {        // support self-effect currently engaged
                "shield" -> now < c.shieldUntil
                "boost" -> now < c.boostUntil
                "e-brake", "reverse_drive" -> now < c.selfSlowUntil
                else -> false
            }
            val cd = (c.readyAt[bay] ?: 0L) - now
            val cdMaxMs = ((k?.let { maxOf(it.cooldownMaxS, it.rechargeS) } ?: MIN_COOLDOWN_S)
                .coerceAtLeast(MIN_COOLDOWN_S) * 1000)
            val interaction = k?.interaction ?: Interaction.HOLD
            val fill = when (interaction) {
                Interaction.CHARGE -> c.charge[bay] ?: 0f
                Interaction.HOLD -> c.heat[bay] ?: 0f
                Interaction.SINGLESHOT -> if (cd > 0) (cd / cdMaxMs).toFloat().coerceIn(0f, 1f) else 0f
            }
            return BayHud(
                bay = label, itemId = id, itemName = id?.let { ItemRepository.name(it) } ?: "—",
                ready = cd <= 0, cooldownFrac = (cd / cdMaxMs).toFloat().coerceIn(0f, 1f),
                interaction = interaction.name, fillFrac = fill, active = active,
            )
        }
        val bays = mutableListOf(bayHud(Bay.ATTACK, "attack"), bayHud(Bay.SUPPORT, "support"))
        if (c.equipped[Bay.SPECIAL] != null) bays.add(bayHud(Bay.SPECIAL, "special"))
        return PlayerHud(c.health, c.energy, c.disabled, bays)
    }

    private fun who(c: CombatCar) = if (c.isPlayer) "P1" else "AI"
    private fun lerp(a: Double, b: Double, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
}
