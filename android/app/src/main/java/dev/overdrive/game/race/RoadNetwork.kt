package dev.overdrive.game.race

import kotlin.math.sqrt

/**
 * The scanned track as an ordered ring of piece *instances* (Phase 0), modeled on 2.6's `RoadNetwork`.
 * Built during the scan lap from the ordered sequence of road-piece ids a car traverses; each ring slot
 * carries its piece-type id (→ [RoadPieceGeometry]). A track repeats piece *types* (many curves share an
 * id), so the ring is a list of instances, not a set.
 *
 * The key query for Phase 1 is [curveSafeSpeed]: a backward-pass look-ahead that returns the fastest
 * speed a car may currently hold so it can still brake to every upcoming curve's limit *before* entering
 * it — i.e. cars slow down ahead of a bend instead of reacting once already on it (the fly-off fix).
 */
class RoadNetwork {

    /** Ordered piece-type ids for one physical lap; empty until the scan builds it. */
    private var ring: IntArray = IntArray(0)
    /** Cumulative mm at the start of each ring slot (cum[0]=0); cum[size] = full lap length. */
    private var cum: IntArray = IntArray(0)

    val ready: Boolean get() = ring.isNotEmpty()
    val size: Int get() = ring.size
    /** Total ring (one physical lap) length in mm; 0 until built. */
    val lapLengthMm: Float get() = if (cum.isEmpty()) 0f else cum[cum.size - 1].toFloat()

    private fun slot(index: Int): Int = ((index % ring.size) + ring.size) % ring.size

    /** Piece-type id at a ring index (wraps). */
    fun pieceIdAt(index: Int): Int {
        if (ring.isEmpty()) return -1
        return ring[slot(index)]
    }

    /** Cumulative distance (mm) of (ringIndex, distAlong) from the ring origin, modulo one lap. */
    fun cumulativeMm(ringIndex: Int, distAlongMm: Float): Float {
        if (ring.isEmpty()) return 0f
        return cum[slot(ringIndex)] + distAlongMm
    }

    /** Ring slot (0..size-1) whose piece contains the given cumulative distance (wraps over the lap). */
    fun ringIndexAtCumulative(cumMm: Float): Int {
        if (ring.isEmpty()) return 0
        val lap = lapLengthMm
        var d = cumMm % lap
        if (d < 0f) d += lap
        for (i in ring.indices) if (d < cum[i + 1]) return i
        return ring.size - 1
    }

    /** Forward distance (mm) from A to B along the ring, in [0, lapLength). */
    fun forwardGapMm(fromRingIndex: Int, fromDist: Float, toRingIndex: Int, toDist: Float): Float {
        val lap = lapLengthMm
        if (lap <= 0f) return Float.MAX_VALUE
        val a = cumulativeMm(fromRingIndex, fromDist)
        val b = cumulativeMm(toRingIndex, toDist)
        var g = (b - a) % lap
        if (g < 0f) g += lap
        return g
    }

    private fun rebuildCum() {
        cum = IntArray(ring.size + 1)
        var acc = 0
        for (i in ring.indices) { cum[i] = acc; acc += RoadPieceGeometry.lengthMm(ring[i]) }
        cum[ring.size] = acc
    }

    /**
     * Build the ring from one car's full ordered piece sequence captured during the scan. One canonical
     * lap is the slice between two crossings of [finishPieceId], starting at the finish piece (so it's
     * independent of where the car began the scan). The finish id can re-register more than once per
     * physical lap (a known firmware quirk), so we take the consecutive-finish pair with the **largest**
     * gap — the real lap, not a short re-trigger. Returns true if a plausible ring (≥4 pieces) was built.
     */
    fun buildFromSequence(seq: List<Int>, finishPieceId: Int): Boolean {
        val finishes = seq.indices.filter { seq[it] == finishPieceId }
        val lap: List<Int> = if (finishes.size >= 2) {
            var best = 0
            for (k in 0 until finishes.size - 1) {
                if (finishes[k + 1] - finishes[k] > finishes[best + 1] - finishes[best]) best = k
            }
            seq.subList(finishes[best], finishes[best + 1])   // [finish .. just before the next finish]
        } else {
            seq   // finish never matched twice — fall back to the raw captured sequence
        }
        if (lap.size < 4) return false
        ring = lap.toIntArray()
        rebuildCum()
        return true
    }

    /**
     * Assemble the ring by **loop closure** (2.6 `TrackMapper` style): the track is a cycle, so one lap is
     * the slice from the first piece a car saw to where its **start fingerprint** recurs ≥[minRing] pieces
     * later. [fingerprints] (`pieceId<<8 | locationId`) disambiguate repeated piece types — independent of
     * any specific finish-piece id, so it maps any track. Returns true once a loop closes.
     */
    fun buildByLoopClosure(pieces: List<Int>, fingerprints: List<Long>, minRing: Int = 4): Boolean {
        if (pieces.size <= minRing) return false
        val anchor = fingerprints[0]
        for (i in minRing until fingerprints.size) {
            if (fingerprints[i] == anchor) {           // returned to the start → ring = [0, i)
                ring = pieces.subList(0, i).toIntArray()
                rebuildCum()
                return true
            }
        }
        return false
    }

    /**
     * Set the ring from one full lap's ordered pieces (collapsing consecutive duplicates — the finish-piece
     * re-trigger reports the same id twice in a row). Returns true if a plausible ring (≥4 pieces) results.
     */
    fun setRingFromLap(lap: List<Int>): Boolean {
        val dedup = ArrayList<Int>(lap.size)
        for (p in lap) if (dedup.isEmpty() || dedup.last() != p) dedup.add(p)
        if (dedup.size < 4) return false
        ring = dedup.toIntArray(); rebuildCum(); return true
    }

    fun clear() { ring = IntArray(0); cum = IntArray(0) }

    /** The mapped ring as an ordered piece-id list (empty until built) — for the scan-screen visualization. */
    fun pieceIds(): List<Int> = ring.toList()

    /** One upcoming piece in the look-ahead window. */
    data class Upcoming(val ringIndex: Int, val pieceId: Int, val distToEntryMm: Float)

    /**
     * Walk forward from (currentRingIndex, distAlongMm) up to [horizonMm], returning each upcoming piece
     * with the distance from the car to that piece's entry. The current piece is not included.
     */
    fun lookAhead(currentRingIndex: Int, distAlongMm: Float, horizonMm: Float): List<Upcoming> {
        if (ring.isEmpty()) return emptyList()
        val out = ArrayList<Upcoming>()
        var dist = (RoadPieceGeometry.lengthMm(pieceIdAt(currentRingIndex)) - distAlongMm).coerceAtLeast(0f)
        var i = 1
        while (dist <= horizonMm && i <= ring.size) {
            val idx = currentRingIndex + i
            val pid = pieceIdAt(idx)
            out.add(Upcoming(idx, pid, dist))
            dist += RoadPieceGeometry.lengthMm(pid)
            i++
        }
        return out
    }

    /**
     * Look-ahead curve-safe speed (mm/s) at (currentRingIndex, distAlongMm): the min of the current
     * piece's own cap and, for every upcoming piece within braking range, `sqrt(cap² + 2·decel·dEntry)` —
     * the fastest you can go now and still decelerate to that piece's cap by the time you reach it.
     *
     * @param capOf  effective curve cap (mm/s) for a piece id; must return [noCap] for straights.
     * @param decel  braking deceleration used for the look-ahead (mm/s²).
     * @param noCap  the "uncapped" speed to clamp against (straight-line ceiling).
     */
    fun curveSafeSpeed(
        currentRingIndex: Int,
        distAlongMm: Float,
        decel: Float,
        noCap: Int,
        capOf: (pieceId: Int) -> Int,
    ): Int {
        if (ring.isEmpty()) return noCap
        var limit = capOf(pieceIdAt(currentRingIndex)).coerceAtMost(noCap)
        // Horizon: enough room to brake from the straight-line ceiling to a stop, plus a piece.
        val horizon = noCap * noCap / (2f * decel) + RoadPieceGeometry.DEFAULT_LENGTH_MM
        for (u in lookAhead(currentRingIndex, distAlongMm, horizon)) {
            val cap = capOf(u.pieceId)
            if (cap >= noCap) continue                       // straight — no constraint from this piece
            val allowed = sqrt(cap.toFloat() * cap + 2f * decel * u.distToEntryMm)
            if (allowed < limit) limit = allowed.toInt()
        }
        return limit.coerceIn(0, noCap)
    }
}
