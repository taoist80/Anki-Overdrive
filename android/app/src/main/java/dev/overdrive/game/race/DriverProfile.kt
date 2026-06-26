package dev.overdrive.game.race

/**
 * An AI commander's **driver stats**, modelled from its real `vehicle_setup` profile + tier/level
 * (see commanders.json: `purerace` / `race` / `battle` × `aggressive` / `defensive` × t1–t3). The
 * exact numeric tuning of those profiles lives in the original firmware AI planner (not in any readable
 * config — see the `anki-overdrive-ai-commanders` note), so we derive sensible, tunable parameters
 * from the profile name here. Drives the opponent car's speed ([RaceEngine]) and weapon behaviour ([Combat]).
 */
data class DriverProfile(
    val commanderId: String? = null,
    val displayName: String? = null,
    val speedScale: Float = 1f,      // multiplier on the AI base speed (faster at higher tier/level)
    val aggression: Float = 0.55f,   // 0 cautious … 1 relentless — scales how often it fires
    val weaponsOn: Boolean = true,   // `purerace` opponents never fire (race-only)
    val defensive: Boolean = false,  // prefers support/shield when it has the energy
) {
    companion object {
        /** A generic rival with no commander identity (Open Play, or extra cars in a campaign race). */
        val DEFAULT = DriverProfile()

        /**
         * Build a profile from a commander's real fields. [vehicleSetup] is the 2.6 `vehicle_setup`
         * string; [tier] 1–3 and [level] 1–10 scale the speed.
         */
        fun fromSetup(commanderId: String?, displayName: String?, vehicleSetup: String?, tier: Int, level: Int): DriverProfile {
            val s = (vehicleSetup ?: "").lowercase()
            val purerace = s.startsWith("purerace")
            val aggressive = "aggressive" in s
            val defensive = "defensive" in s
            val t = tier.coerceIn(1, 3)
            val lv = level.coerceIn(1, 10)
            // tier sets the band (t1 ~0.82, t2 ~0.98, t3 ~1.14); vehicle level nudges within it.
            val speedScale = (0.82f + 0.16f * (t - 1) + (lv - 1) * 0.012f).coerceIn(0.7f, 1.4f)
            val aggression = when {
                aggressive -> 0.85f
                defensive -> 0.35f
                else -> 0.55f
            }
            return DriverProfile(
                commanderId = commanderId,
                displayName = displayName,
                speedScale = speedScale,
                aggression = aggression,
                weaponsOn = !purerace,
                defensive = defensive,
            )
        }
    }
}
