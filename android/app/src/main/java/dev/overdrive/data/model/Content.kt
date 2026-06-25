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
)

/** characters/commanders.json — an AI commander/opponent. */
@Serializable
data class Commander(
    val id: String = "",
    @SerialName("friendly_name") val friendlyName: String = "",
    @SerialName("preference_vehicle") val preferenceVehicle: String = "",
    @SerialName("preference_item") val preferenceItem: String = "",
    @SerialName("loot_drop") val lootDrop: String = "",
    val tier: Int = 0,
    val number: Int = 0,
    @SerialName("vehicle_level") val vehicleLevel: Int = 0,
)
