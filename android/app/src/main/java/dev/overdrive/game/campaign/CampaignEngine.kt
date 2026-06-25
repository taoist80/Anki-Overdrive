package dev.overdrive.game.campaign

import android.content.Context
import dev.overdrive.data.ContentRepository
import dev.overdrive.data.model.Chapter
import dev.overdrive.game.race.RaceState
import dev.overdrive.profile.ProfileRepository

/** The six star-objective rule types from star_challenges.json. */
enum class StarRule {
    WIN_GAME, TIME_LIMIT, PREF_CAR, WIN_BY_MARGIN, BANNED_ITEM, NO_DEATHS, UNKNOWN;

    companion object {
        fun from(s: String): StarRule = when (s) {
            "req_wingame" -> WIN_GAME
            "req_timelimit" -> TIME_LIMIT
            "req_prefcar" -> PREF_CAR
            "req_winbymargin" -> WIN_BY_MARGIN
            "req_banneditem" -> BANNED_ITEM
            "req_nodeaths" -> NO_DEATHS
            else -> UNKNOWN
        }
    }
}

/** A resolved star objective for a mission, with its earned + currently-evaluable state. */
data class StarObjective(
    val id: String,
    val rule: StarRule,
    val param: String,
    val description: String,
    val rewardPoints: Int,
    val earned: Boolean,
    val evaluable: Boolean,
)

/** Outcome of finishing a campaign mission. */
data class MissionResultSummary(
    val won: Boolean,
    val newStars: List<StarObjective>,
    val totalStarsForMission: Int,
    val coinsAwarded: Int,
)

/**
 * Campaign progression, ported from CampaignData/CampaignService (3.4.0): the require_for_unlock DAG
 * gates missions (a mission unlocks when every prerequisite has ≥1 star), star objectives resolve
 * mission → commander → star_challenges, and a finished race is evaluated against those objectives.
 *
 * Objectives needing systems we don't have yet (preferred-car / banned-item / no-deaths) are flagged
 * non-evaluable and can't be earned until Phase 4's item/vehicle tracking lands.
 */
object CampaignEngine {

    private val EVALUABLE = setOf(StarRule.WIN_GAME, StarRule.TIME_LIMIT, StarRule.WIN_BY_MARGIN)

    fun ensureLoaded(ctx: Context) {
        ContentRepository.load(ctx)
        ProfileRepository.load(ctx)
    }

    fun objectivesFor(missionId: String): List<StarObjective> {
        val mission = ContentRepository.missionsById[missionId] ?: return emptyList()
        val commander = ContentRepository.commander(mission.opponent) ?: return emptyList()
        val completed = ProfileRepository.profile.completedTasks(missionId)
        return commander.starUnlockIds.mapNotNull { sid ->
            val ch = ContentRepository.starChallengesById[sid] ?: return@mapNotNull null
            val rule = StarRule.from(ch.rule)
            StarObjective(
                id = sid,
                rule = rule,
                param = ch.param1,
                description = describe(rule, ch.param1),
                rewardPoints = ch.rewardPoints.toIntOrNull() ?: 0,
                earned = sid in completed,
                evaluable = rule in EVALUABLE,
            )
        }
    }

    fun starsFor(missionId: String): Int = ProfileRepository.profile.starsFor(missionId)
    fun totalStars(): Int = ProfileRepository.profile.totalStars
    fun maxStars(): Int = ContentRepository.missionsById.size * 3

    /** A mission is unlocked when all its require_for_unlock prerequisites have ≥1 star. */
    fun isMissionUnlocked(missionId: String): Boolean {
        val mission = ContentRepository.missionsById[missionId] ?: return false
        if (mission.requireForUnlock.isEmpty()) return true
        return mission.requireForUnlock.all { ProfileRepository.profile.starsFor(it) >= 1 }
    }

    fun isChapterUnlocked(chapter: Chapter): Boolean =
        chapter.missions.firstOrNull()?.let { isMissionUnlocked(it) } ?: false

    fun chapterStars(chapter: Chapter): Int = chapter.missions.sumOf { starsFor(it) }
    fun chapterMaxStars(chapter: Chapter): Int = chapter.missions.size * 3

    /** Evaluate a finished race against the mission's objectives, award new stars, return a summary. */
    fun completeMission(ctx: Context, missionId: String, result: RaceState): MissionResultSummary {
        val objectives = objectivesFor(missionId)
        val standings = result.standings
        val won = standings.firstOrNull()?.isPlayer == true
        val elapsedSec = result.elapsedMs / 1000
        val margin = if (standings.size >= 2 && standings[0].isPlayer)
            standings[0].transitions - standings[1].transitions else 0

        val met = objectives.filter { o ->
            if (o.earned) return@filter false
            when (o.rule) {
                StarRule.WIN_GAME -> won
                StarRule.TIME_LIMIT -> won && (o.param.toLongOrNull()?.let { elapsedSec in 1..it } ?: false)
                StarRule.WIN_BY_MARGIN -> won && (o.param.toIntOrNull()?.let { margin >= it } ?: false)
                else -> false  // not evaluable yet
            }
        }
        val coins = met.sumOf { it.rewardPoints }
        if (met.isNotEmpty()) {
            ProfileRepository.awardMission(ctx, missionId, met.map { it.id }.toSet(), coins, xp = coins / 2)
        }
        return MissionResultSummary(won, met, ProfileRepository.profile.starsFor(missionId), coins)
    }

    private fun describe(rule: StarRule, param: String): String = when (rule) {
        StarRule.WIN_GAME -> "Win the match"
        StarRule.TIME_LIMIT -> "Win in under ${formatSeconds(param)}"
        StarRule.PREF_CAR -> "Win in the preferred vehicle"
        StarRule.WIN_BY_MARGIN -> "Win by $param+"
        StarRule.BANNED_ITEM -> "Win without ${prettyItem(param)}"
        StarRule.NO_DEATHS -> "Win without being disabled"
        StarRule.UNKNOWN -> "Complete the objective"
    }

    private fun formatSeconds(param: String): String {
        val s = param.toIntOrNull() ?: return "${param}s"
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun prettyItem(param: String): String =
        param.substringAfterLast('_').ifBlank { param }
            .replaceFirstChar { it.uppercase() }
}
