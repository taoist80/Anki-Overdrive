package dev.overdrive.game.race

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class RoadPieceTypes(val curveIds: List<Int> = emptyList())

/**
 * Road-piece geometry classification, precomputed from the 3.4.0 roadPieceDefinitionFiles (a piece
 * is a "curve" if its path heading sweeps > ~0.3 rad). Used by the RaceEngine to auto-slow cars
 * through bends — the proper fix for high-speed fishtailing.
 */
object RoadPieces {
    private var curve: Set<Int> = emptySet()
    @Volatile private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        runCatching {
            val txt = ctx.assets.open("gamedata/roadPieceTypes.json").bufferedReader().use { it.readText() }
            curve = Json { ignoreUnknownKeys = true }.decodeFromString<RoadPieceTypes>(txt).curveIds.toSet()
        }
        loaded = true
    }

    fun isCurve(pieceId: Int): Boolean = pieceId in curve
}
