package dev.overdrive.game.race

import dev.overdrive.data.ContentRepository

/**
 * Builds AI commander [DriverProfile]s from the Tournament roster — the bridge between the content data
 * (commanders.json driver stats + commanders_2_6.json authentic name/portrait) and the race engine's
 * profile-driven AI. Used by the campaign, the Open Play opponent picker, and the Tournament ladder.
 */
object Rivals {

    /** A DriverProfile for one commander id (stats from commanders.json, real name from commanders_2_6). */
    fun profile(commanderId: String?): DriverProfile? {
        val id = commanderId ?: return null
        val legacy = ContentRepository.commander(id) ?: return null
        val name = ContentRepository.commander26(id)?.name
        return DriverProfile.fromSetup(id, name, legacy.vehicleSetup, legacy.tier, legacy.vehicleLevel)
    }

    /** Every commander that has both driver stats and an authentic name — the pickable/ladder roster. */
    fun roster(): List<DriverProfile> =
        ContentRepository.commandersById.keys.mapNotNull { profile(it) }.sortedBy { it.displayName }

    /**
     * An ordered field of [size] rivals: [primaryId] first (the campaign opponent / chosen rival), then
     * other distinct commanders as fillers — preferring a similar tier so the field is balanced — so a
     * 3–4 car race is a field of named rivals. Extra cars past the field stay generic.
     */
    fun field(primaryId: String?, size: Int): List<DriverProfile> {
        if (size <= 0) return emptyList()
        val out = ArrayList<DriverProfile>()
        val used = HashSet<String>()
        profile(primaryId)?.let { out.add(it); used.add(primaryId!!) }
        val primaryTier = ContentRepository.commander(primaryId ?: "")?.tier ?: 1
        val pool = ContentRepository.commandersById.values
            .filter { it.id !in used && ContentRepository.commander26(it.id)?.name != null }
            .sortedWith(compareBy({ kotlin.math.abs(it.tier - primaryTier) }, { it.number }))
        for (c in pool) {
            if (out.size >= size) break
            profile(c.id)?.let { out.add(it); used.add(c.id) }
        }
        return out
    }
}
