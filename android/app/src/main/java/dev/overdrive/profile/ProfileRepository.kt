package dev.overdrive.profile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
) {
    val level: Int get() = 1 + xp / 1000
    val totalStars: Int get() = missions.values.sumOf { it.completedTaskIds.size }
    fun starsFor(missionId: String): Int = missions[missionId]?.completedTaskIds?.size ?: 0
    fun completedTasks(missionId: String): Set<String> = missions[missionId]?.completedTaskIds ?: emptySet()
}

/**
 * Local-persistent player profile, stored as JSON in the app's files dir. Compose-observable via
 * [profile]. Phase 4 will sync this same shape to the local backend; the JSON model is the contract.
 */
object ProfileRepository {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private const val FILE = "profile.json"

    var profile by mutableStateOf(Profile())
        private set

    @Volatile private var loaded = false

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        val f = File(ctx.filesDir, FILE)
        if (f.exists()) {
            runCatching { profile = json.decodeFromString<Profile>(f.readText()) }
        }
        loaded = true
    }

    /** Merge newly-completed star tasks into a mission and award coins/xp; persists immediately. */
    fun awardMission(ctx: Context, missionId: String, taskIds: Set<String>, coins: Int, xp: Int) {
        val prev = profile.missions[missionId]?.completedTaskIds ?: emptySet()
        val merged = prev + taskIds
        val newlyEarned = merged.size - prev.size
        val missions = profile.missions.toMutableMap().apply {
            this[missionId] = MissionProgress(merged)
        }
        // Only grant coins/xp for stars not previously earned (no farming the same run).
        profile = profile.copy(
            missions = missions,
            coins = profile.coins + if (newlyEarned > 0) coins else 0,
            xp = profile.xp + if (newlyEarned > 0) xp else 0,
        )
        save(ctx)
    }

    fun addCoins(ctx: Context, n: Int) {
        profile = profile.copy(coins = profile.coins + n)
        save(ctx)
    }

    private fun save(ctx: Context) {
        runCatching { File(ctx.filesDir, FILE).writeText(json.encodeToString(profile)) }
    }
}
