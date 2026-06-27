package dev.overdrive.game.race

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** A previously-confirmed track ring (ordered piece-type ids for one lap). */
@Serializable
data class SavedTrack(val ring: List<Int>)

/**
 * Caches the last *player-confirmed* track ring (4.0.4 `SavedTrack` / 2.6 `GetLastScannedTrackConfig`).
 * Re-racing the same physical track can then pre-load the ring and skip the discovery mapping lap — the
 * cars still drive to localize and stage at the start, but the planner is ready immediately and staging
 * happens on the first finish crossing instead of the second (no re-mapping). Persisted as JSON in the app
 * files dir, mirroring [dev.overdrive.profile.ProfileRepository]. We only ever save a ring the player
 * confirmed on the scan screen, so a cached track is one they've eyeballed as correct.
 */
object TrackCache {
    private val json = Json { ignoreUnknownKeys = true }
    private const val FILE = "last_track.json"

    var last: SavedTrack? = null
        private set

    @Volatile private var loaded = false

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        val f = File(ctx.filesDir, FILE)
        if (f.exists()) runCatching { last = json.decodeFromString<SavedTrack>(f.readText()) }
        loaded = true
    }

    fun save(ctx: Context, ring: List<Int>) {
        if (ring.size < 4) return
        last = SavedTrack(ring)
        runCatching { File(ctx.filesDir, FILE).writeText(json.encodeToString(last)) }
    }

    fun clear(ctx: Context) {
        last = null
        runCatching { File(ctx.filesDir, FILE).delete() }
    }
}
