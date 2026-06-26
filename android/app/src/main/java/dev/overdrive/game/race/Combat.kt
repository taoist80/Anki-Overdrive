package dev.overdrive.game.race

import android.os.SystemClock
import android.util.Log
import dev.overdrive.data.ItemRepository
import dev.overdrive.data.model.Bay
import dev.overdrive.data.model.GameItem

/**
 * Client-side virtual combat — the model the original game ran in `libDriveEngine` and re-expressed
 * over BLE as speed/lane commands (the cars carry no weapons). When a bay fires we resolve a target
 * from 0x27 telemetry (track progress via segment-transition count + lane offset), apply damage and
 * the item's [GameItem.sourceEffects], and surface per-car modifiers — [speedFactor], [steerBlocked],
 * [invertSteer], [isDisabled] — that [RaceEngine] folds into the cars it's already driving.
 *
 * Health/energy/cooldowns are virtual state here; "disabled" (health ≤ 0) forces a brief spin-out +
 * respawn and increments a death counter (consumed by the NO_DEATHS campaign objective in Phase 10).
 */
class Combat {

    companion object {
        private const val TAG = "OverdriveX"
        const val MAX_HEALTH = 100f
        const val MAX_ENERGY = 100f
        const val ENERGY_REGEN_PER_S = 14f      // energy/sec passive regen
        const val RESPAWN_MS = 2600L            // spin-out duration when disabled
        const val MIN_COOLDOWN_S = 0.6          // floor so 0-recharge items can't be spammed
        const val SHIELD_BLOCK = 0.30f          // incoming damage multiplier while shielded
        const val BOOST_FACTOR = 1.40f          // self speed multiplier while boosting
        const val TRACTOR_FACTOR = 0.35f        // speed multiplier while caught (allow_speed_change=false)
        const val GRAVITY_FACTOR = 0.08f        // near-stop (also no steering)
        const val AI_FIRE_PERIOD_MS = 2200L     // how often an AI rival tries to fire
        // Default loadout when nothing is equipped, so combat always works out of the box.
        const val DEFAULT_ATTACK = "base_machine_gun"
        const val DEFAULT_SUPPORT = "base_shield"
    }

    class ActiveEffect(val effectId: String, val untilMs: Long)

    inner class CombatCar(val address: String, val isPlayer: Boolean, val vehicleName: String?) {
        var health = MAX_HEALTH
        var energy = MAX_ENERGY
        var deaths = 0
        var disabledUntil = 0L
        var boostUntil = 0L
        var shieldUntil = 0L
        var lastAiFireAt = 0L
        val equipped = HashMap<Bay, String>()     // bay -> itemId
        val readyAt = HashMap<Bay, Long>()        // bay -> cooldown-until ms
        val effects = ArrayList<ActiveEffect>()
        val disabled: Boolean get() = SystemClock.elapsedRealtime() < disabledUntil
    }

    private val cars = LinkedHashMap<String, CombatCar>()
    var running = false; private set
    /** Whether this game mode allows weapons (RACE / TIME TRIAL are weapon-free). */
    var weaponsEnabled = true; private set

    /** Snapshot the race roster: equip the player's loadout, AI rivals a sensible default. */
    fun init(roster: List<Triple<String, Boolean, String?>>, playerLoadout: Map<String, String>, weaponsEnabled: Boolean) {
        cars.clear()
        this.weaponsEnabled = weaponsEnabled
        roster.forEach { (addr, isPlayer, vehName) ->
            val c = CombatCar(addr, isPlayer, vehName)
            if (isPlayer) {
                c.equipped[Bay.ATTACK] = playerLoadout["attack"] ?: DEFAULT_ATTACK
                c.equipped[Bay.SUPPORT] = playerLoadout["support"] ?: DEFAULT_SUPPORT
                playerLoadout["special"]?.let { c.equipped[Bay.SPECIAL] = it }
            } else {
                c.equipped[Bay.ATTACK] = DEFAULT_ATTACK
                c.equipped[Bay.SUPPORT] = DEFAULT_SUPPORT
            }
            cars[addr] = c
        }
        running = true
    }

    fun stop() { running = false; cars.clear() }
    fun car(addr: String): CombatCar? = cars[addr]

    // ---- per-car driving modifiers (queried by RaceEngine) -------------------
    fun isDisabled(addr: String): Boolean = cars[addr]?.disabled == true

    /** Multiplier on a car's target speed from its active effects (boost > 1, tractor/gravity < 1). */
    fun speedFactor(addr: String): Float {
        val c = cars[addr] ?: return 1f
        if (c.disabled) return 0f
        var f = 1f
        val now = SystemClock.elapsedRealtime()
        if (now < c.boostUntil) f *= BOOST_FACTOR
        for (e in c.effects) {
            val eff = ItemRepository.effect(e.effectId) ?: continue
            if (!eff.allowSpeedChange) f *= if (!eff.allowSteering) GRAVITY_FACTOR else TRACTOR_FACTOR
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
    /** Fire [bay] for the car at [addr]. Returns true if it actually fired (ready + energy + target). */
    fun fire(addr: String, bay: Bay, telemetry: Collection<CarTelemetry>): Boolean {
        if (!weaponsEnabled) return false
        val c = cars[addr] ?: return false
        if (c.disabled || !running) return false
        val itemId = c.equipped[bay] ?: return false
        val item = ItemRepository.item(itemId) ?: return false
        val now = SystemClock.elapsedRealtime()
        if (now < (c.readyAt[bay] ?: 0L)) return false               // cooling down
        val cost = (item.energyUsePerS.toFloat()).coerceAtLeast(6f)   // per-fire energy
        if (c.energy < cost) return false
        c.energy -= cost
        val cooldownMs = (maxOf(item.rechargeTimeS, MIN_COOLDOWN_S) * 1000).toLong()
        c.readyAt[bay] = now + cooldownMs + (item.activationDelayS * 1000).toLong()

        when {
            isSupport(item) -> applySupport(c, item)
            else -> {
                val targets = resolveTargets(c, item, telemetry)
                targets.forEach { applyHit(c, it, item, now) }
                Log.i(TAG, "fire ${if (c.isPlayer) "P1" else "AI"} ${item.id} -> ${targets.size} target(s)")
            }
        }
        return true
    }

    private fun isSupport(item: GameItem): Boolean = when (item.type) {
        "shield", "boost", "e-brake", "reverse_drive" -> true
        else -> item.bay == Bay.SUPPORT && item.sourceEffects.isEmpty() && item.damagePerSec <= 0.0
    }

    private fun applySupport(c: CombatCar, item: GameItem) {
        val now = SystemClock.elapsedRealtime()
        when (item.type) {
            "boost" -> c.boostUntil = now + 2500
            "shield" -> c.shieldUntil = now + 4000
            else -> c.boostUntil = now + 1500   // e-brake/reverse etc. — minor self nudge
        }
    }

    private fun applyHit(from: CombatCar, targetAddr: String, item: GameItem, now: Long) {
        val t = cars[targetAddr] ?: return
        if (t.disabled) return
        var dmg = item.damagePerSec.toFloat().coerceAtLeast(8f)
        if (now < t.shieldUntil) dmg *= SHIELD_BLOCK
        t.health -= dmg
        // Apply the weapon's status effects to the target for their configured duration.
        for (effId in item.sourceEffects) {
            val eff = ItemRepository.effect(effId) ?: continue
            val durMs = ((if (eff.durationS > 0) eff.durationS else 1.5) * 1000).toLong()
            t.effects.add(ActiveEffect(effId, now + durMs))
        }
        if (t.health <= 0f) {
            t.health = 0f
            t.disabledUntil = now + RESPAWN_MS
            t.deaths += 1
            t.effects.clear()
            Log.i(TAG, "${if (t.isPlayer) "P1" else "AI"} DISABLED by ${from.address} (${item.id}); deaths=${t.deaths}")
        }
    }

    /**
     * Resolve who a weapon hits, using segment-transition count as a track-progress scalar and lane
     * offset for cone width. Proximity/AOE hit everyone within range; others hit the single nearest.
     */
    private fun resolveTargets(from: CombatCar, item: GameItem, telemetry: Collection<CarTelemetry>): List<String> {
        val me = telemetry.firstOrNull { it.address == from.address } ?: return emptyList()
        val targeter = item.targeter
        val rangeSegments = when (targeter?.type) {
            "proximity" -> 1
            "cone" -> 2
            "rectangle" -> if ((targeter.length) >= 1.5) 3 else 1   // sniper long vs shotgun short
            "forward_track_distance" -> 2
            "self" -> return emptyList()
            else -> 2
        }
        val laneTol = if (targeter?.type == "cone") 50f else Float.MAX_VALUE
        val candidates = telemetry.filter { o ->
            o.address != from.address && cars[o.address]?.disabled != true &&
                kotlin.math.abs(o.transitions - me.transitions) <= rangeSegments &&
                kotlin.math.abs(o.offsetMm - me.offsetMm) <= laneTol
        }
        if (candidates.isEmpty()) return emptyList()
        val multi = targeter?.type == "proximity"
        return if (multi) candidates.map { it.address }
        else listOf(candidates.minByOrNull { kotlin.math.abs(it.transitions - me.transitions) }!!.address)
    }

    /**
     * Per-tick upkeep: regen energy, expire effects, respawn disabled cars, and let AI rivals fire.
     * [dtMs] is the control-loop cadence. Returns nothing; RaceEngine reads modifiers afterwards.
     */
    fun tick(dtMs: Long, telemetry: Collection<CarTelemetry>) {
        if (!running) return
        val now = SystemClock.elapsedRealtime()
        val dt = dtMs / 1000f
        for (c in cars.values) {
            // expire timed effects
            if (c.effects.isNotEmpty()) c.effects.removeAll { now >= it.untilMs }
            // respawn
            if (c.health <= 0f && now >= c.disabledUntil) c.health = MAX_HEALTH
            // energy regen (not while disabled)
            if (!c.disabled) c.energy = (c.energy + ENERGY_REGEN_PER_S * dt).coerceAtMost(MAX_ENERGY)
            // AI auto-fire at whoever's near
            if (!c.isPlayer && !c.disabled && now - c.lastAiFireAt >= AI_FIRE_PERIOD_MS) {
                c.lastAiFireAt = now
                if (c.energy > MAX_ENERGY * 0.5f) fire(c.address, Bay.ATTACK, telemetry)
            }
        }
    }

    /** HUD snapshot for the player's two bays + vitals. */
    data class BayHud(val bay: String, val itemId: String?, val itemName: String, val ready: Boolean, val cooldownFrac: Float)
    data class PlayerHud(val health: Float, val energy: Float, val disabled: Boolean, val bays: List<BayHud>)

    fun playerHud(playerAddr: String?): PlayerHud? {
        if (!weaponsEnabled) return null   // RACE / TIME TRIAL: no weapon HUD
        val c = cars[playerAddr ?: return null] ?: return null
        val now = SystemClock.elapsedRealtime()
        fun bayHud(bay: Bay, label: String): BayHud {
            val id = c.equipped[bay]
            val item = ItemRepository.item(id)
            val cd = (c.readyAt[bay] ?: 0L) - now
            val cdMax = (maxOf(item?.rechargeTimeS ?: MIN_COOLDOWN_S, MIN_COOLDOWN_S) * 1000)
            return BayHud(label, id, id?.let { ItemRepository.name(it) } ?: "—",
                ready = cd <= 0, cooldownFrac = (cd / cdMax).toFloat().coerceIn(0f, 1f))
        }
        return PlayerHud(c.health, c.energy, c.disabled,
            listOf(bayHud(Bay.ATTACK, "attack"), bayHud(Bay.SUPPORT, "support")))
    }
}
