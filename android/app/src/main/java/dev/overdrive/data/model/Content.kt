package dev.overdrive.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** gameData/gameTypes/gameModeDisplay.json — UI metadata for each game mode. */
@Serializable
data class GameModeDisplayFile(
    val standardModes: List<RawGameMode> = emptyList(),
    val secondaryModes: List<RawGameMode> = emptyList(),
    val devModes: List<RawGameMode> = emptyList(),
)

@Serializable
data class RawGameMode(
    val gameId: Int = -1,
    val name: String = "",
    val image: String = "",
    val icon: String = "",
    val tint: String = "#FFFFFF",
    val enabled: Boolean = false,
    val isLimitedTime: Boolean = false,
)

/** A mode resolved for the UI: display title/description from strings, image bundled in assets/ui. */
data class GameMode(
    val gameId: Int,
    val internalName: String,
    val title: String,
    val description: String?,
    val imageAsset: String?,
    val tintArgb: Long,
)

/** campaign/chapters.json — chapter → ordered mission ids. */
@Serializable
data class Chapter(
    val id: String = "",
    @SerialName("image_key") val imageKey: String = "",
    val tint: String = "#FFFFFF",
    val missions: List<String> = emptyList(),
)

/** campaign/campaign.json — one mission: opponent commander, game type, preferred track, unlock gate. */
@Serializable
data class Mission(
    val id: String = "",
    @SerialName("game_type") val gameType: String = "",
    val opponent: String = "",
    @SerialName("preference_track") val preferenceTrack: String = "",
    @SerialName("require_for_unlock") val requireForUnlock: List<String> = emptyList(),
    val cutscene: String = "",
    @SerialName("game_limit") val gameLimit: Int = 0,
    @SerialName("mission_select_scroll_pos") val scrollPos: Double = 0.0,
)

/** characters/commanders.json — an AI commander/opponent. */
@Serializable
data class Commander(
    val id: String = "",
    @SerialName("friendly_name") val friendlyName: String = "",
    @SerialName("preference_vehicle") val preferenceVehicle: String = "",
    @SerialName("preference_item") val preferenceItem: String = "",
    @SerialName("loot_drop") val lootDrop: String = "",
    @SerialName("ai_id") val aiId: String = "",
    @SerialName("vehicle_setup") val vehicleSetup: String = "",   // AI driver profile (purerace/race/battle × aggressive/defensive × tier)
    val tier: Int = 0,
    val number: Int = 0,
    @SerialName("vehicle_level") val vehicleLevel: Int = 0,
    @SerialName("1_star_unlock_id") val star1Id: String = "",
    @SerialName("2_star_unlock_id") val star2Id: String = "",
    @SerialName("3_star_unlock_id") val star3Id: String = "",
) {
    val starUnlockIds: List<String> get() = listOf(star1Id, star2Id, star3Id).filter { it.isNotBlank() }
}

/**
 * campaign/commanders_2_6.json — the real 2.6 Tournament roster: authentic name + bio (from the 2.6
 * `commander_NN_desc` strings) + the extracted portrait key + the 2.6 asset-bundle it came from. Keyed
 * by `commander_gen2_NN`, matching [Mission.opponent], so the campaign screens show the real opponent.
 */
@Serializable
data class Commander26(
    val n: Int = 0,
    val id: String = "",
    val name: String = "",
    val portrait: String = "",
    val bio: String = "",
    @SerialName("bundle_2_6") val bundle: String? = null,
) {
    /** Bundled portrait asset path (authentic 2.6 art under ui/commanders/). */
    val portraitAsset: String? get() = portrait.takeIf { it.isNotBlank() }?.let { "ui/commanders/$it.png" }
}

/** gameData/medals.json — the achievement medal catalog. `name`/`description` are string keys
 *  resolved against the asset-strings table at load time. */
@Serializable
data class MedalFile(val medals: List<Medal> = emptyList())

@Serializable
data class Medal(
    val id: String = "",
    val image: String = "",
    val reward: Int = 0,
    val sort: Int = 0,
    val enabled: Boolean = true,
    val name: String = "",
    val description: String = "",
) {
    /** Bundled 2.6 medal icon (extracted ui_icon_medal_* art under ui/medals/). */
    val iconAsset: String get() = "ui/medals/$image.png"
}

/** characters/star_challenges.json — a campaign star objective (the directed task for a star). */
@Serializable
data class RawStarChallenge(
    val id: String = "",
    val rule: String = "",
    @SerialName("param1") val param1: String = "",
    val description: String = "",
    @SerialName("reward_points") val rewardPoints: String = "0",
    @SerialName("loot_drop") val lootDrop: String = "",
)
