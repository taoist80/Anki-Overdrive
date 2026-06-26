package dev.overdrive.profile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.overdrive.net.BackendClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Per-mission progress: the set of star-challenge ids the player has completed. */
@Serializable
data class MissionProgress(
    val completedTaskIds: Set<String> = emptySet(),
)

/** The local player profile — the single source of progression (synced to the backend in Phase 4). */
@Serializable
data class Profile(
    val driverName: String = "Driver 01",
    val coins: Int = 0,
    val xp: Int = 0,
    val missions: Map<String, MissionProgress> = emptyMap(),
    val inventory: Map<String, Int> = emptyMap(),       // itemId -> count (loot rewards)
    val vehicleUpgrades: Map<String, Int> = emptyMap(), // "<carId>:<track>" -> level
    val loadout: Map<String, String> = emptyMap(),      // "<carId>:<bay>" -> equipped itemId
) {
    val level: Int get() = 1 + xp / 1000
    val totalStars: Int get() = missions.values.sumOf { it.completedTaskIds.size }
    fun starsFor(missionId: String): Int = missions[missionId]?.completedTaskIds?.size ?: 0
    fun completedTasks(missionId: String): Set<String> = missions[missionId]?.completedTaskIds ?: emptySet()
    fun itemCount(itemId: String): Int = inventory[itemId] ?: 0
    fun upgradeLevel(key: String): Int = vehicleUpgrades[key] ?: 0
    /** The item equipped in [bay] (e.g. "attack"/"support") of car [carId], or null. */
    fun equipped(carId: Int, bay: String): String? = loadout["$carId:$bay"]
    /** All items equipped on a car, by bay name. */
    fun loadoutFor(carId: Int): Map<String, String> =
        loadout.filterKeys { it.startsWith("$carId:") }.mapKeys { it.key.substringAfter(':') }
}

/**
 * Local-persistent player profile (JSON in the app files dir), Compose-observable via [profile].
 * On any mutation it saves locally and, if signed in, best-effort syncs the same blob to the backend
 * ([BackendClient]) — local-first, so the game works fully offline and reconciles when online.
 */
object ProfileRepository {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private const val FILE = "profile.json"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var profile by mutableStateOf(Profile())
        private set

    @Volatile private var loaded = false

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        val f = File(ctx.filesDir, FILE)
        if (f.exists()) runCatching { profile = json.decodeFromString<Profile>(f.readText()) }
        loaded = true
    }

    /** Replace the local profile with one fetched from the backend (after login/signup). */
    fun adoptRemote(ctx: Context, remote: Profile) {
        profile = remote
        saveLocal(ctx)
    }

    /** Merge newly-completed star tasks into a mission and award coins/xp; persists + syncs. */
    fun awardMission(ctx: Context, missionId: String, taskIds: Set<String>, coins: Int, xp: Int) {
        val prev = profile.missions[missionId]?.completedTaskIds ?: emptySet()
        val merged = prev + taskIds
        val newlyEarned = merged.size - prev.size
        val missions = profile.missions.toMutableMap().apply { this[missionId] = MissionProgress(merged) }
        profile = profile.copy(
            missions = missions,
            coins = profile.coins + if (newlyEarned > 0) coins else 0,
            xp = profile.xp + if (newlyEarned > 0) xp else 0,
        )
        persist(ctx)
    }

    fun addCoins(ctx: Context, n: Int) {
        profile = profile.copy(coins = profile.coins + n)
        persist(ctx)
    }

    /** Spend coins if affordable; returns true on success. */
    fun spendCoins(ctx: Context, amount: Int): Boolean {
        if (profile.coins < amount) return false
        profile = profile.copy(coins = profile.coins - amount)
        persist(ctx)
        return true
    }

    /** Add a loot/item reward to the inventory. */
    fun addItem(ctx: Context, itemId: String, count: Int = 1) {
        val inv = profile.inventory.toMutableMap()
        inv[itemId] = (inv[itemId] ?: 0) + count
        profile = profile.copy(inventory = inv)
        persist(ctx)
    }

    /** Equip [itemId] into [bay] of car [carId] (or clear the slot when itemId is null); persists + syncs. */
    fun equipItem(ctx: Context, carId: Int, bay: String, itemId: String?) {
        val lo = profile.loadout.toMutableMap()
        val key = "$carId:$bay"
        if (itemId == null) lo.remove(key) else lo[key] = itemId
        profile = profile.copy(loadout = lo)
        persist(ctx)
    }

    /** Buy/level a vehicle upgrade for [cost] coins; returns true if purchased. */
    fun buyUpgrade(ctx: Context, key: String, cost: Int): Boolean {
        if (profile.coins < cost) return false
        val ups = profile.vehicleUpgrades.toMutableMap()
        ups[key] = (ups[key] ?: 0) + 1
        profile = profile.copy(coins = profile.coins - cost, vehicleUpgrades = ups)
        persist(ctx)
        return true
    }

    private fun persist(ctx: Context) {
        saveLocal(ctx)
        if (BackendClient.signedIn) scope.launch { BackendClient.pushProfile(profile) }
    }

    private fun saveLocal(ctx: Context) {
        runCatching { File(ctx.filesDir, FILE).writeText(json.encodeToString(profile)) }
    }
}
