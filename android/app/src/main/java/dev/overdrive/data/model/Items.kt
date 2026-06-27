package dev.overdrive.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Item / weapon catalog models for `gamedata/items.json` (DDL's authoritative table, identical
 * schema across Overdrive 4.0.4 / 3.4 / Drive 2.4.5). The file has three parts:
 *  - `item_bays`  — the three bays (support/attack/special), how many are active, the switch penalty.
 *  - `items`      — `abstract_items` (18 base templates) + `real_items` (141 leveled variants that
 *                   `extends` a base; includes Gen1, Groundshock, Skull variants etc.).
 *  - `effects`    — status effects a weapon applies to its target (tractor/gravity/hacked/wiggle…).
 *
 * Raw* types mirror the JSON; [ItemRepository] resolves `extends` into flat [GameItem]s.
 */
@Serializable
data class ItemsFile(
    @SerialName("item_bays") val itemBays: ItemBays = ItemBays(),
    val items: ItemsBlock = ItemsBlock(),
    val effects: List<RawEffect> = emptyList(),
)

@Serializable
data class ItemsBlock(
    @SerialName("abstract_items") val abstractItems: List<RawItem> = emptyList(),
    @SerialName("real_items") val realItems: List<RawItem> = emptyList(),
)

@Serializable
data class ItemBays(
    val bays: List<String> = listOf("support", "attack", "special"),
    @SerialName("number_active_bays") val numberActiveBays: Int = 2,
    @SerialName("switch_penalty") val switchPenalty: SwitchPenalty = SwitchPenalty(),
)

@Serializable
data class SwitchPenalty(
    @SerialName("_energy") val energy: Int = 0,
    @SerialName("time_s") val timeS: Double = 0.0,
)

/** One item row — used for both abstract templates and real variants (real `extends` a base). */
@Serializable
data class RawItem(
    val id: String = "",
    val extends: String? = null,
    val bay: String? = null,
    val type: String? = null,
    val level: Int? = null,
    val cost: Int? = null,
    @SerialName("card_power") val cardPower: Double? = null,
    @SerialName("interaction_style") val interactionStyle: String? = null,   // hold | charge | singleshot
    @SerialName("damage_per_target_per_s") val damagePerTargetPerS: Double? = null,
    @SerialName("damage_per_target_per_second_charged") val damageChargedPerS: Double? = null,
    @SerialName("damage_per_target") val damagePerTarget: Double? = null,
    @SerialName("damage_type") val damageType: String? = null,
    @SerialName("damage_multiplier_at_max_distance") val damageMultAtMaxDist: Double? = null,
    @SerialName("energy_use_per_s") val energyUsePerS: Double? = null,
    @SerialName("energy_use_in_charge_mode_per_s") val energyChargePerS: Double? = null,
    @SerialName("base_energy_cost") val baseEnergyCost: Double? = null,
    @SerialName("cooldown_data") val cooldownData: RawCooldown? = null,
    @SerialName("recharge_time_s") val rechargeTimeS: Double? = null,
    @SerialName("recharge_time_no_target_s") val rechargeTimeNoTargetS: Double? = null,
    @SerialName("activation_delay") val activationDelay: Double? = null,
    // support-item specifics
    @SerialName("shield_blocked_damage_percent") val shieldBlockedPct: Double? = null,
    @SerialName("boost_speed_addition") val boostSpeedAddition: Double? = null,
    @SerialName("tractor_beam_ratio") val tractorBeamRatio: Double? = null,
    val deceleration: Double? = null,
    @SerialName("script_config") val scriptConfig: RawScriptConfig? = null,
    @SerialName("name_localized") val nameLocalized: String? = null,
    @SerialName("description_localized") val descriptionLocalized: String? = null,
    val description: String? = null,
    @SerialName("image_icon") val imageIcon: String? = null,
    @SerialName("image_detail") val imageDetail: String? = null,
    @SerialName("image_schematic") val imageSchematic: String? = null,
    @SerialName("addon_style") val addonStyle: String? = null,
    @SerialName("audio_package") val audioPackage: String? = null,
    @SerialName("source_effects") val sourceEffects: List<String> = emptyList(),
    @SerialName("self_effects") val selfEffects: List<String> = emptyList(),
    val targeter: RawTargeter? = null,
    @SerialName("vehicle_restriction") val vehicleRestriction: List<String> = emptyList(),
    @SerialName("use_class") val useClass: String? = null,
)

/** Nested `cooldown_data` — three shapes: charge (charge weapons), heat (hold), simple (singleshot). */
@Serializable
data class RawCooldown(
    @SerialName("charge_limit_s") val chargeLimitS: Double? = null,
    @SerialName("cooldown_time_min_s") val cooldownTimeMinS: Double? = null,
    @SerialName("cooldown_time_max_s") val cooldownTimeMaxS: Double? = null,
    @SerialName("heatup_rate_pct") val heatupRatePct: Double? = null,
    @SerialName("overheat_rate_pct") val overheatRatePct: Double? = null,
    @SerialName("cooldown_rate_pct") val cooldownRatePct: Double? = null,
    @SerialName("recharge_time_s") val rechargeTimeS: Double? = null,
)

/** scrambler/aoe `script_config` — carries a sustained damage rate for the support-bay scrambler. */
@Serializable
data class RawScriptConfig(
    @SerialName("damage_per_target_per_s") val damagePerTargetPerS: Double? = null,
)

@Serializable
data class RawTargeter(
    val type: String = "self",
    val length: Double? = null,
    val theta: Double? = null,
    val radius: Double? = null,
    @SerialName("half_width") val halfWidth: Double? = null,
    @SerialName("max_distance") val maxDistance: Double? = null,
    @SerialName("min_distance") val minDistance: Double? = null,
    @SerialName("single_target") val singleTarget: Boolean = false,
)

@Serializable
data class RawEffect(
    val id: String = "",
    val type: String = "plain",
    @SerialName("duration_s") val durationS: Double? = null,
    val filter: EffectFilter = EffectFilter(),
)

@Serializable
data class EffectFilter(
    @SerialName("allow_speed_change") val allowSpeedChange: Boolean = true,
    @SerialName("allow_steering") val allowSteering: Boolean = true,
    @SerialName("allow_item_use") val allowItemUse: Boolean = true,
    @SerialName("invert_steering") val invertSteering: Boolean = false,
)

/** Which bay an item lives in. The HUD shows attack (top) + support (bottom); special = truck rage. */
enum class Bay { ATTACK, SUPPORT, SPECIAL, UNKNOWN;
    companion object {
        fun from(s: String?): Bay = when (s?.lowercase()) {
            "attack" -> ATTACK; "support" -> SUPPORT; "special" -> SPECIAL; else -> UNKNOWN
        }
    }
}

/** How a weapon picks its victims — drives in-race targeting in [dev.overdrive.game.race]. */
data class Targeter(
    val type: String,            // cone | rectangle | proximity | self | forward_track_distance
    val length: Double = 0.0,    // reach along the track (m), for cone/rectangle/forward
    val theta: Double = 0.0,     // cone half-angle (rad)
    val radius: Double = 0.0,    // proximity radius (m)
    val halfWidth: Double = 0.0, // rectangle half-width (lane spread)
    val maxDistance: Double = 0.0, // forward_track_distance reach (tractor/pulse-ram)
    val singleTarget: Boolean = false,
)

/**
 * How the player engages a bay — the real `interaction_style` from items.json, which determines the
 * whole fire model:
 *  - [HOLD]       sustained while the button is held (machine gun, shield, boost, tractor, scrambler,
 *                 e-brake, reverse): drains [ItemCombat.energyPerS], builds heat, overheats then cools.
 *  - [CHARGE]     hold to wind up to [ItemCombat.chargeLimitS] then release (sniper, EMP, mine): drains
 *                 [ItemCombat.energyChargePerS] while charging, fires scaled by how full the charge was.
 *  - [SINGLESHOT] instant tap (shotgun, horn, gravity trap, pulse ram): fixed recharge, no hold.
 */
enum class Interaction { HOLD, CHARGE, SINGLESHOT;
    companion object {
        fun from(s: String?): Interaction = when (s?.lowercase()) {
            "charge" -> CHARGE; "singleshot" -> SINGLESHOT; else -> HOLD
        }
    }
}

/**
 * The real combat numbers for an item, resolved from items.json (+ its `extends` base). This is what
 * makes each weapon/support item behave differently — honored verbatim by [dev.overdrive.game.race.Combat].
 */
data class ItemCombat(
    val interaction: Interaction,
    val sustainedDps: Double,     // damage_per_target_per_s — continuous (machine gun, scrambler)
    val chargedDps: Double,       // damage_per_target_per_second_charged — ×charge-seconds (sniper/EMP)
    val flatDamage: Double,       // damage_per_target — per shot (shotgun/mine/pulse-ram)
    val damageType: String?,      // e.g. "explosive"
    val maxDistFalloff: Double,   // damage_multiplier_at_max_distance (1.0 = no falloff)
    val energyPerS: Double,       // hold/sustained energy drain
    val energyChargePerS: Double, // charge-mode energy drain
    val baseEnergyCost: Double,   // per-activation energy
    // charge model
    val chargeLimitS: Double,     // seconds of hold to reach full charge
    val cooldownMinS: Double,     // cooldown after a minimal-charge shot
    val cooldownMaxS: Double,     // cooldown after a full-charge shot
    // heat model (hold)
    val heatupRate: Double,       // heat gained per second held (0..1/s)
    val overheatRate: Double,     // (reserved) overheat penalty rate
    val cooldownRate: Double,     // heat shed per second when released (0..1/s)
    // simple model (singleshot) + fallback
    val rechargeS: Double,        // fixed recharge between shots
    // support specifics
    val shieldBlockPct: Double,   // incoming damage blocked while shielded (1.0 = 100%)
    val boostSpeedAdd: Double,    // self speed added while boosting (1.0 = +100%)
    val tractorRatio: Double,     // victim speed multiplier while tractor-beamed
    val deceleration: Double,     // e-brake decel
)

/** A status effect applied to a hit car: re-expressed in-race as speed/steer/item-use modulation. */
data class Effect(
    val id: String,
    val type: String,
    val durationS: Double,
    val allowSpeedChange: Boolean,
    val allowSteering: Boolean,
    val allowItemUse: Boolean,
    val invertSteering: Boolean,
)

/** A fully-resolved item (real merged over the abstract it `extends`). Drives combat, loadout, shop. */
data class GameItem(
    val id: String,
    val bay: Bay,
    val type: String,
    val level: Int,
    val cost: Int,
    val cardPower: Double,
    val damagePerSec: Double,
    val energyUsePerS: Double,
    val rechargeTimeS: Double,
    val activationDelayS: Double,
    val nameKey: String?,
    val descKey: String?,
    val iconImage: String?,
    val detailImage: String?,
    val addonStyle: String?,
    val audioPackage: String?,
    val sourceEffects: List<String>,
    val targeter: Targeter?,
    val combat: ItemCombat,
    val vehicleRestriction: List<String>,
    val useClass: String?,
    val isAbstract: Boolean,
) {
    /** Can the car named [vehicleName] equip this item? Empty restriction = universal. */
    fun usableBy(vehicleName: String?): Boolean =
        vehicleRestriction.isEmpty() || (vehicleName != null && vehicleRestriction.any { it.equals(vehicleName, true) })
}

/** gamedata/upgradeData.json — a single permanent vehicle upgrade (per-car, by addon_style/target). */
@Serializable
data class UpgradesFile(val upgrades: List<RawUpgrade> = emptyList())

@Serializable
data class RawUpgrade(
    val id: String = "",
    val name: String = "",
    @SerialName("name_localized") val nameLocalized: String? = null,
    val description: String? = null,
    val level: Int = 1,
    val cost: Int = 0,
    @SerialName("slots_used") val slotsUsed: Int = 1,
    @SerialName("addon_style") val addonStyle: String = "",
    @SerialName("target_id") val targetId: String = "",
    val value: Double = 0.0,
    val type: String = "scalar",
    @SerialName("image_detail") val imageDetail: String? = null,
    val implemented: String = "true",
    @SerialName("vehicle_restriction") val vehicleRestriction: List<String> = emptyList(),
)
