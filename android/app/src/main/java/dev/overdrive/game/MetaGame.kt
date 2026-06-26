package dev.overdrive.game

import dev.overdrive.data.ItemRepository

/**
 * Client-side reward economy (the backend just persists the resulting profile). Loot rolls and
 * upgrade costs live here. As of Phase 7 the item catalog + loot draw from the real
 * [ItemRepository] (items.json); the generic [UPGRADE_TRACKS] below are the fallback for the
 * pre-Phase-9 garage and get replaced by real per-vehicle upgrades (upgradeData.json) in Phase 9.
 */
object MetaGame {

    data class LootReward(
        val coins: Int,
        val itemId: String,
        val itemName: String,
        val rarity: String,
        val rarityColor: Long,
    )

    /** Display name for an item id, via the real catalog. */
    fun itemName(itemId: String): String = ItemRepository.name(itemId)

    /** Roll a loot box: rarity-weighted coins + a real catalog item (car-eligible if [vehicleName] set). */
    fun rollLoot(vehicleName: String? = null): LootReward {
        val r = ItemRepository.rollLoot(vehicleName)
        val name = if (r.itemId.isBlank()) "" else ItemRepository.name(r.itemId)
        return LootReward(r.coins, r.itemId, name, r.rarity, r.rarityColor)
    }

    data class UpgradeTrack(val key: String, val name: String, val baseCost: Int, val maxLevel: Int = 5)

    val UPGRADE_TRACKS = listOf(
        UpgradeTrack("speed", "Speed", 250),
        UpgradeTrack("weapons", "Weapons", 300),
        UpgradeTrack("defense", "Defense", 200),
        UpgradeTrack("energy", "Energy", 350),
    )

    /** Cost to take an upgrade track from [currentLevel] to the next, scaling with level. */
    fun upgradeCost(track: UpgradeTrack, currentLevel: Int): Int = track.baseCost * (currentLevel + 1)

    /** Profile key for a per-vehicle upgrade track. */
    fun upgradeKey(carId: Int, track: UpgradeTrack): String = "$carId:${track.key}"

    // ---- Gameplay effect of each upgrade track (per level; maxLevel 5) -------------------
    // These turn the garage upgrade levels into real in-race multipliers (read by RaceEngine/Combat).
    fun speedMult(level: Int): Float = 1f + 0.05f * level            // +5%/lvl straight-line top speed
    fun damageMult(level: Int): Float = 1f + 0.08f * level           // +8%/lvl weapon damage
    fun defenseMult(level: Int): Float = (1f - 0.08f * level).coerceAtLeast(0.5f)  // -8%/lvl incoming
    fun energyMult(level: Int): Float = 1f + 0.12f * level           // +12%/lvl energy regen
}
