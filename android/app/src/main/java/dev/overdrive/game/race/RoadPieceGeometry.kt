package dev.overdrive.game.race

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Geometry of one road-piece *type*, keyed by the id the firmware reports in 0x27 localization.
 * Ported from 2.6's `roadPieceDefinitionFiles/racing/<id>.txt` (see PLANNER-PLAN.md): line 1 is the
 * centerline arc length (m), line 2 the signed curve radius (m, 0 = straight). `maxSpeedMmps` is the
 * 2.6 curve-limit `v = sqrt(curveLimitFrictionConstant · g · |radius|)` (friction 0.87) — the
 * theoretical lateral-grip ceiling; the engine scales it to our hardware (RaceEngine.CURVE_SPEED_SCALE).
 */
@Serializable
data class PieceGeometry(
    val id: Int,
    val lengthMm: Int,
    val radiusMm: Int,        // signed; sign = turn direction; 0 = straight
    val isCurve: Boolean,
    val maxSpeedMmps: Int,    // 2.6 parity curve cap; 0 = no cap (straight / gentle)
    val numLanes: Int,
)

@Serializable
private data class GeometryBundle(
    val mu: Double = 0.87,
    val g: Double = 9.81,
    val laneSpacingMm: Double = 9.0,
    val pieces: List<PieceGeometry> = emptyList(),
)

/**
 * The bundled road-piece geometry table (Phase 0). Loaded once from `gamedata/roadPieceGeometry.json`,
 * which is regenerated from the 2.6 spec via the commands in PLANNER-PLAN.md. Replaces the old
 * curve-id-only heuristic with real per-piece geometry (length + radius + curve cap), which the
 * [RoadNetwork] / [VehicleStateEstimator] / planner build on.
 */
object RoadPieceGeometry {
    /** Fallback piece length (mm) for an id we have no geometry for — keeps dead-reckoning sane. */
    const val DEFAULT_LENGTH_MM = 300

    private val json = Json { ignoreUnknownKeys = true }
    private var byId: Map<Int, PieceGeometry> = emptyMap()
    var laneSpacingMm: Float = 9f; private set
    @Volatile private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        runCatching {
            val txt = ctx.assets.open("gamedata/roadPieceGeometry.json").bufferedReader().use { it.readText() }
            val bundle = json.decodeFromString<GeometryBundle>(txt)
            byId = bundle.pieces.associateBy { it.id }
            laneSpacingMm = bundle.laneSpacingMm.toFloat()
        }
        loaded = true
    }

    fun of(pieceId: Int): PieceGeometry? = byId[pieceId]

    fun isCurve(pieceId: Int): Boolean = byId[pieceId]?.isCurve == true

    /** Centerline length of a piece (mm); falls back to [DEFAULT_LENGTH_MM] for unknown ids. */
    fun lengthMm(pieceId: Int): Int = byId[pieceId]?.lengthMm ?: DEFAULT_LENGTH_MM

    /** Raw 2.6 parity curve cap (mm/s) for a piece; 0 = no cap (straight / gentle). */
    fun rawCurveCapMmps(pieceId: Int): Int = byId[pieceId]?.maxSpeedMmps ?: 0
}
