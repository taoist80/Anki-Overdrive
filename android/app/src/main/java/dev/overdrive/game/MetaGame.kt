package dev.overdrive.game

import kotlin.random.Random

/**
 * Client-side reward economy (the backend just persists the resulting profile). Loot rolls and
 * upgrade costs live here. Uses a curated pool of real Overdrive items for now; wiring the full
 * items.json/lootDrops.json tables is a Phase 5 refinement.
 */
object MetaGame {

    data class LootReward(
        val coins: Int,
        val itemId: String,
        val itemName: String,
        val rarity: String,
        val rarityColor: Long,
    )

    private val ITEM_POOL = listOf(
        "nitro" to "Nitro Boost",
        "tire_mod" to "Tire Mod",
        "plasma_cannon" to "Plasma Cannon",
        "emp" to "EMP Blast",
        "energy_shield" to "Energy Shield",
        "ram_plow" to "Ram Plow",
        "golden_sniper" to "Golden Sniper",
        "ice_mine" to "Ice Mine",
    )

    fun itemName(itemId: String): String =
        ITEM_POOL.firstOrNull { it.first == itemId }?.second ?: itemId

    /** Roll a loot box: rarity-weighted coins + a random item. */
    fun rollLoot(): LootReward {
        val r = Random.nextInt(100)
        val (rarity, color, coins) = when {
            r < 60 -> Triple("COMMON", 0xFF8AA0B6, (40..120).random())
            r < 88 -> Triple("RARE", 0xFF22B7E6, (100..250).random())
            r < 98 -> Triple("EPIC", 0xFFB060FF, (250..450).random())
            else -> Triple("LEGENDARY", 0xFFE6B800, (450..800).random())
        }
        val (id, name) = ITEM_POOL.random()
        return LootReward(coins, id, name, rarity, color)
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
}
