package dev.overdrive.data

import android.content.Context
import dev.overdrive.data.model.Bay
import dev.overdrive.data.model.Effect
import dev.overdrive.data.model.GameItem
import dev.overdrive.data.model.ItemBays
import dev.overdrive.data.model.ItemsFile
import dev.overdrive.data.model.RawEffect
import dev.overdrive.data.model.RawItem
import dev.overdrive.data.model.RawTargeter
import dev.overdrive.data.model.Targeter
import dev.overdrive.data.model.RawUpgrade
import dev.overdrive.data.model.UpgradesFile
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * The real item / weapon / loot / upgrade catalog, parsed from `gamedata/items.json` +
 * `upgradeData.json` and resolved against the bundled item strings (`asset-strings-en.json`).
 * Replaces the hard-coded pools that used to live in [dev.overdrive.game.MetaGame].
 *
 * `real_items` `extends` an `abstract_items` template; [resolve] flattens that inheritance so combat
 * ([dev.overdrive.game.race]) and the garage/loadout UI read one flat [GameItem].
 */
object ItemRepository {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile var loaded = false; private set

    /** Every resolved item by id (abstract + real). */
    var byId: Map<String, GameItem> = emptyMap(); private set
    /** The purchasable / equippable catalog (resolved real items). */
    var catalog: List<GameItem> = emptyList(); private set
    var effects: Map<String, Effect> = emptyMap(); private set
    var bays: ItemBays = ItemBays(); private set
    /** Permanent upgrades grouped by their `target_id` (e.g. max_speed, weaponclass_*_damage). */
    var upgradesByTarget: Map<String, List<RawUpgrade>> = emptyMap(); private set

    private var strings: Strings = Strings.EMPTY
    private var bundledImages: Set<String> = emptySet()   // filenames present in assets/items/

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        strings = Strings.load(ctx, "gamedata/asset-strings-en.json")
        bundledImages = (ctx.assets.list("items") ?: emptyArray()).toSet()

        val file = json.decodeFromString<ItemsFile>(read(ctx, "gamedata/items.json"))
        bays = file.itemBays
        effects = file.effects.associate { it.id to it.toEffect() }

        val abstractIds = file.items.abstractItems.mapNotNull { it.id.ifBlank { null } }.toSet()
        // All raw rows by id; `extends` can chain real -> real -> abstract, so resolve the full chain.
        val rawById = (file.items.abstractItems + file.items.realItems)
            .filter { it.id.isNotBlank() }.associateBy { it.id }
        val all = LinkedHashMap<String, GameItem>()
        rawById.values.forEach { all[it.id] = resolve(it, rawById, isAbstract = it.id in abstractIds) }
        byId = all
        catalog = file.items.realItems.mapNotNull { byId[it.id] }

        val ups = json.decodeFromString<UpgradesFile>(read(ctx, "gamedata/upgradeData.json")).upgrades
        upgradesByTarget = ups.filter { it.implemented != "false" }.groupBy { it.targetId }

        loaded = true
        val atk = catalog.count { it.bay == Bay.ATTACK }
        val sup = catalog.count { it.bay == Bay.SUPPORT }
        val spc = catalog.count { it.bay == Bay.SPECIAL }
        val nUp = upgradesByTarget.values.sumOf { it.size }
        val sample = catalog.firstOrNull()?.let { "${it.id}='${name(it.id)}'" } ?: "-"
        android.util.Log.i("OverdriveX",
            "ItemRepository: ${catalog.size} items ($atk atk/$sup sup/$spc special), " +
            "${effects.size} effects, $nUp upgrades. e.g. $sample")
        // Verify the real per-item combat values resolved (the values must differ per weapon).
        listOf("base_machine_gun","base_sniper","base_shotgun","base_emp","base_mine",
               "base_shield","base_boost","base_tractor_beam").forEach { id ->
            byId[id]?.combat?.let { c ->
                android.util.Log.i("OverdriveX", "  combat $id: ${c.interaction} " +
                    "dps=${c.sustainedDps}/chg=${c.chargedDps}/flat=${c.flatDamage} " +
                    "e/s=${c.energyPerS}/chgE=${c.energyChargePerS} " +
                    "chargeLim=${c.chargeLimitS} cd=${c.cooldownMinS}..${c.cooldownMaxS} " +
                    "heat=${c.heatupRate}/${c.cooldownRate} recharge=${c.rechargeS} " +
                    "shield=${c.shieldBlockPct} boost=${c.boostSpeedAdd} tractor=${c.tractorRatio}")
            }
        }
    }

    /** Flatten the full `extends` chain (real -> real -> abstract): most-derived field wins. */
    private fun resolve(raw: RawItem, rawById: Map<String, RawItem>, isAbstract: Boolean): GameItem {
        // chain = [self, parent, grandparent, ...]; guard against cycles.
        val chain = ArrayList<RawItem>()
        var cur: RawItem? = raw; val seen = HashSet<String>()
        while (cur != null && seen.add(cur.id)) { chain.add(cur); cur = cur.extends?.let { rawById[it] } }
        fun <T> first(sel: (RawItem) -> T?): T? = chain.firstNotNullOfOrNull(sel)
        // cooldown_data may be defined on the base and partially overridden up the chain — resolve per field.
        fun cd(sel: (dev.overdrive.data.model.RawCooldown) -> Double?): Double? =
            chain.firstNotNullOfOrNull { it.cooldownData?.let(sel) }
        val combat = dev.overdrive.data.model.ItemCombat(
            interaction = dev.overdrive.data.model.Interaction.from(first { it.interactionStyle }),
            sustainedDps = first { it.damagePerTargetPerS } ?: first { it.scriptConfig?.damagePerTargetPerS } ?: 0.0,
            chargedDps = first { it.damageChargedPerS } ?: 0.0,
            flatDamage = first { it.damagePerTarget } ?: 0.0,
            damageType = first { it.damageType },
            maxDistFalloff = first { it.damageMultAtMaxDist } ?: 1.0,
            energyPerS = first { it.energyUsePerS } ?: 0.0,
            energyChargePerS = first { it.energyChargePerS } ?: 0.0,
            baseEnergyCost = first { it.baseEnergyCost } ?: 0.0,
            chargeLimitS = cd { it.chargeLimitS } ?: 0.0,
            cooldownMinS = cd { it.cooldownTimeMinS } ?: 0.0,
            cooldownMaxS = cd { it.cooldownTimeMaxS } ?: 0.0,
            heatupRate = cd { it.heatupRatePct } ?: 0.0,
            overheatRate = cd { it.overheatRatePct } ?: 0.0,
            cooldownRate = cd { it.cooldownRatePct } ?: 0.0,
            rechargeS = cd { it.rechargeTimeS } ?: first { it.rechargeTimeS } ?: 0.0,
            shieldBlockPct = first { it.shieldBlockedPct } ?: 0.0,
            boostSpeedAdd = first { it.boostSpeedAddition } ?: 0.0,
            tractorRatio = first { it.tractorBeamRatio } ?: 0.0,
            deceleration = first { it.deceleration } ?: 0.0,
        )
        return GameItem(
            id = raw.id,
            bay = Bay.from(first { it.bay }),
            type = first { it.type } ?: "",
            level = first { it.level } ?: 1,
            cost = first { it.cost } ?: 0,
            cardPower = first { it.cardPower } ?: 0.0,
            // weapons spell damage three ways: continuous (machine gun), charged (sniper/EMP), per-shot (shotgun/mine/ram)
            damagePerSec = first { it.damagePerTargetPerS } ?: first { it.damageChargedPerS } ?: first { it.damagePerTarget } ?: 0.0,
            energyUsePerS = first { it.energyUsePerS } ?: 0.0,
            rechargeTimeS = first { it.rechargeTimeS } ?: 0.0,
            activationDelayS = first { it.activationDelay } ?: 0.0,
            nameKey = first { it.nameLocalized },
            descKey = first { it.descriptionLocalized ?: it.description },
            iconImage = first { it.imageIcon },
            detailImage = first { it.imageDetail },
            addonStyle = first { it.addonStyle },
            audioPackage = first { it.audioPackage },
            sourceEffects = chain.firstOrNull { it.sourceEffects.isNotEmpty() }?.sourceEffects ?: emptyList(),
            targeter = first { it.targeter }?.toTargeter(),
            combat = combat,
            vehicleRestriction = chain.firstOrNull { it.vehicleRestriction.isNotEmpty() }?.vehicleRestriction ?: emptyList(),
            useClass = first { it.useClass },
            isAbstract = isAbstract,
        )
    }

    fun item(id: String?): GameItem? = id?.let { byId[it] }
    fun effect(id: String): Effect? = effects[id]

    /**
     * Bundled asset path for an item's art: the large/medium detail render if we have it, else the
     * weapon-class hitzone icon (always bundled), else null. Used by the HUD + garage to show weapons.
     */
    fun imageAsset(id: String?): String? {
        val it = item(id) ?: return null
        it.detailImage?.let { d ->
            // PNG (from the DDL extraction) or WebP (carved from 4.0.4 .ctex), large then medium.
            for (name in listOf("$d-large.png", "$d-large.webp", "$d-medium.png", "$d-medium.webp"))
                if (name in bundledImages) return "items/$name"
        }
        it.iconImage?.let { ic ->
            if ("$ic.png" in bundledImages) return "items/$ic.png"
            if ("ui_icon_$ic.png" in bundledImages) return "items/ui_icon_$ic.png"
        }
        return null
    }

    /** Display name for an item id, resolved through the localized strings (falls back to a tidy id). */
    fun name(id: String?): String {
        val it = item(id) ?: return prettyId(id)
        return it.nameKey?.let { k -> strings.opt(k) } ?: prettyId(id)
    }

    fun description(id: String?): String =
        item(id)?.descKey?.let { strings.opt(it) } ?: ""

    /** Whether an item has a *resolvable, non-blank* localized name (some rows resolve to ""). */
    fun named(item: GameItem): Boolean = strings.opt(item.nameKey)?.isNotBlank() == true

    /**
     * A real, player-equippable weapon: it has a non-blank name AND a defined detail image. The
     * detail-image requirement drops the internal `NukeParent*` template family (universal weapons
     * with `image_detail == null` that otherwise show a generic class-icon placeholder and duplicate
     * the real Gen1/car-specific weapons). `ZFXDisable` and the blank `NukeParentShortRange*` rows
     * fail the name check.
     */
    fun equippable(item: GameItem): Boolean =
        named(item) && item.detailImage != null && item.combat.flatDamage < 9999   // exclude instakill mode weapons (OneShotKillGun, pulse-ram)

    /**
     * Real, equippable weapons in [bay] the given car may equip (empty restriction = universal),
     * lowest level first.
     */
    fun equippableFor(bay: Bay, vehicleName: String?): List<GameItem> =
        catalog.filter { it.bay == bay && equippable(it) && it.usableBy(vehicleName) }
            .sortedBy { it.level }

    /** True if this item has a bundled detail render (`-large`/`-medium`), not just a hitzone icon. */
    fun hasDetailArt(id: String?): Boolean {
        val d = item(id)?.detailImage ?: return false
        return "$d-large.png" in bundledImages || "$d-medium.png" in bundledImages
    }

    /**
     * The starter weapon for [bay] on a car: the *lowest-level* equippable (always a level-1 starter,
     * which is free/owned), preferring one with real canister art so the HUD shows a render not a
     * generic icon. Used as the default loadout and as the implicit equip when a bay is untouched.
     */
    fun defaultItem(bay: Bay, vehicleName: String?): GameItem? {
        val pool = equippableFor(bay, vehicleName)
        val minLevel = pool.firstOrNull()?.level ?: return null
        val lowest = pool.filter { it.level == minLevel }
        return lowest.firstOrNull { hasDetailArt(it.id) } ?: lowest.firstOrNull()
    }

    /**
     * Coin price to buy an item: its catalog [GameItem.cost] when set, else a level-scaled fallback.
     * Level-1 weapons are free starters (owned by default) — see [dev.overdrive.profile] ownership.
     */
    fun itemPrice(item: GameItem): Int = if (item.cost > 0) item.cost else 200 * item.level * item.level

    /**
     * The implicit starting loadout for a car (attack + support) used when the profile has none —
     * so a brand-new profile and AI rivals always fire a real, art-backed weapon out of the box.
     */
    fun defaultLoadout(vehicleName: String?): Map<String, String> = buildMap {
        defaultItem(Bay.ATTACK, vehicleName)?.let { put("attack", it.id) }
        defaultItem(Bay.SUPPORT, vehicleName)?.let { put("support", it.id) }
    }

    // ---- Loot ----------------------------------------------------------------
    data class LootResult(val coins: Int, val itemId: String, val rarity: String, val rarityColor: Long)

    /**
     * Roll a loot reward: rarity-weighted coins + a real item drawn from the catalog (preferring
     * those the player's car can equip). Faithful prize-box table resolution (lootDrops.json
     * `extends` graph + per-car `contents`) is Phase 9; this already grants real catalog items.
     */
    fun rollLoot(vehicleName: String? = null): LootResult {
        val r = Random.nextInt(100)
        val (rarity, color, coins, maxLevel) = when {
            r < 60 -> Quad("COMMON", 0xFF8AA0B6, (40..120).random(), 1)
            r < 88 -> Quad("RARE", 0xFF22B7E6, (100..250).random(), 2)
            r < 98 -> Quad("EPIC", 0xFFB060FF, (250..450).random(), 3)
            else -> Quad("LEGENDARY", 0xFFE6B800, (450..800).random(), 9)
        }
        val pool = catalog.filter { it.level <= maxLevel && it.usableBy(vehicleName) }
            .ifEmpty { catalog.filter { it.level <= maxLevel } }
            .ifEmpty { catalog }
        val item = pool.randomOrNull()?.id ?: ""   // "" = coins-only reward (no item granted)
        return LootResult(coins, item, rarity, color)
    }

    private data class Quad(val a: String, val b: Long, val c: Int, val d: Int)

    private fun RawEffect.toEffect() = Effect(
        id = id, type = type, durationS = durationS ?: 0.0,
        allowSpeedChange = filter.allowSpeedChange, allowSteering = filter.allowSteering,
        allowItemUse = filter.allowItemUse, invertSteering = filter.invertSteering,
    )

    private fun RawTargeter.toTargeter() = Targeter(
        type = type, length = length ?: 0.0, theta = theta ?: 0.0, radius = radius ?: 0.0,
        halfWidth = halfWidth ?: 0.0, maxDistance = maxDistance ?: 0.0,
        singleTarget = singleTarget,
    )

    /** "GroundshockMachineGunL01" -> "Groundshock Machine Gun L01"; "base_machine_gun" -> "Machine Gun". */
    private fun prettyId(id: String?): String {
        if (id.isNullOrBlank()) return "Item"
        if (id.startsWith("base_")) return id.removePrefix("base_").split('_')
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        return id.replace(Regex("([a-z])([A-Z])"), "$1 $2").replace(Regex("([A-Za-z])([0-9])"), "$1 $2")
    }

    private fun read(ctx: Context, path: String): String =
        ctx.assets.open(path).bufferedReader().use { it.readText() }
}
