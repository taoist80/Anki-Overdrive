package dev.overdrive.game.race

/**
 * An AI commander's **driver profile**, ported from the 2.6 drive-engine AI trait model
 * (`aic_commander_high_level_ai.json` → `ai_trait_configuration`). Each of the 27 Tournament commanders
 * has a real `vehicle_setup` archetype (`purerace`/`race`/`battle` × `aggressive`/`defensive` × tier),
 * which we map to 2.6's two actionable trait axes:
 *  - **aggressive** (lazy / low / hostile / tactical / ultra) → how close it tails (followFactor) + how
 *    often it fires items ([fireCooldownMs]) — the *real* 2.6 cooldowns (lazy 8–10s … ultra 1–1.5s).
 *  - **speedy** (autopilot / novice / relaxed / normal / hyper) → race speed (snail 0.34 … racing ~1.1 m/s),
 *    which we scale onto our AI base speed ([speedScale]).
 *
 * 2.6 runs a full cost-based planner over these traits; we apply them as a rule-based model on top of
 * direct speed control + [Combat]'s AI fire. Tunable, but grounded in the known-good 2.6 config.
 */
data class DriverProfile(
    val commanderId: String? = null,
    val displayName: String? = null,
    val speedScale: Float = 1f,        // ×AI base speed (from the 2.6 "speedy" trait)
    val fireCooldownMs: Long = 2200L,  // AI item-use cadence (from the 2.6 "aggressive" trait)
    val followFactor: Float = 0.3f,    // how close it tails the target (0.1 hostile … 0.9 lazy)
    val weaponsOn: Boolean = true,     // `purerace` opponents never fire (race-only)
    val defensive: Boolean = false,    // prefers support/shield when it has the energy
    val tier: Int = 1,                 // commander tier (1–3) — difficulty band
    val attackMult: Float = 1f,        // AI weapon-damage ×scale by tier (commanders only fire the basic
                                       // weapon, so tier strength is expressed as harder hits, not items)
    val trait: String = "default",     // e.g. "battle_aggressive_t3 → ultra/hyper" (logging)
) {
    enum class Aggressive(val cooldownMs: Long, val follow: Float) {
        LAZY(9000, 0.9f), LOW(6000, 0.3f), HOSTILE(3500, 0.1f), TACTICAL(2000, 0.15f), ULTRA(1250, 0.15f)
    }
    enum class Speedy(val scale: Float) {           // scale onto AI base (≈0.8 m/s = scale 1.0)
        AUTOPILOT(0.45f), NOVICE(0.78f), RELAXED(1.0f), NORMAL(1.15f), HYPER(1.25f)
    }

    companion object {
        val DEFAULT = DriverProfile()

        fun fromSetup(commanderId: String?, displayName: String?, vehicleSetup: String?, tier: Int, level: Int): DriverProfile {
            val s = (vehicleSetup ?: "").lowercase()
            val purerace = s.startsWith("purerace")
            val battle = s.startsWith("battle")
            val aggressive = "aggressive" in s
            val defensive = "defensive" in s
            val t = tier.coerceIn(1, 3)

            // aggressive trait: stance sets the band, tier escalates within it (2.6 difficulty curve)
            val aggr = when {
                aggressive -> arrayOf(Aggressive.HOSTILE, Aggressive.TACTICAL, Aggressive.ULTRA)[t - 1]
                defensive  -> arrayOf(Aggressive.LAZY, Aggressive.LOW, Aggressive.HOSTILE)[t - 1]
                else       -> arrayOf(Aggressive.LAZY, Aggressive.LOW, Aggressive.LOW)[t - 1]
            }
            // speedy trait: pure racers run fast, battlers slower (busy fighting); tier escalates
            val speedy = when {
                purerace -> arrayOf(Speedy.RELAXED, Speedy.NORMAL, Speedy.HYPER)[t - 1]
                battle   -> arrayOf(Speedy.NOVICE, Speedy.RELAXED, Speedy.RELAXED)[t - 1]
                else     -> arrayOf(Speedy.NOVICE, Speedy.RELAXED, Speedy.NORMAL)[t - 1]   // race
            }
            val lvlNudge = (level.coerceIn(1, 10) - 1) * 0.008f
            return DriverProfile(
                commanderId = commanderId,
                displayName = displayName,
                speedScale = (speedy.scale + lvlNudge).coerceIn(0.45f, 1.35f),
                fireCooldownMs = aggr.cooldownMs,
                followFactor = aggr.follow,
                weaponsOn = !purerace,
                defensive = defensive,
                tier = t,
                attackMult = arrayOf(1f, 1.15f, 1.3f)[t - 1],
                trait = "${s.ifBlank { "?" }} → ${aggr.name.lowercase()}/${speedy.name.lowercase()}",
            )
        }
    }
}
