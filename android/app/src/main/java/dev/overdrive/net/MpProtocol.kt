package dev.overdrive.net

import kotlinx.serialization.Serializable

/**
 * Multiplayer (Phase 12) wire model — mirrors the Node race-room broker (server/src/rooms.js) and the
 * 3.4.0 `GameLobbyDef` / `PlayerMessage` catalog. JSON over WebSocket; every message carries a `t` tag.
 * See the MP build plan (ARTIFACTS.md) §05.
 */

object Mp {
    // game mode ids (3.4.0 gameDefinitionId)
    const val MODE_RACE = 6
    const val MODE_BATTLE = 1
    const val MODE_KOTH = 43

    const val NO_PLAYER_ID = 254

    fun modeName(mode: Int) = when (mode) {
        MODE_BATTLE -> "Battle"
        MODE_KOTH -> "King of the Hill"
        else -> "Race"
    }
}

/** One lobby slot. Faithful to 3.4.0 GamePlayerData; `connectionType` keeps the BLE/Wifi distinction. */
@Serializable
data class MpPlayer(
    val gamePlayerId: Int = Mp.NO_PLAYER_ID,
    val slotId: Int = -1,
    val displayName: String? = null,
    val isHost: Boolean = false,
    val vehicleId: Int? = null,
    val vehicleConnectionUUID: String? = null,
    val connectionType: String = "NoConnection",   // NoConnection | Wifi | VirtualWifi | BLE
    val connectionState: String = "Disconnected",
    val batteryState: String = "Normal",
    val clientLobbyState: String = "Initial",
    val teamId: Int = -1,
    val ready: Boolean = false,
    val emptySlot: Boolean = true,
)

/** Full lobby snapshot (responseLobbyData / gameLobbyUpdate payload). */
@Serializable
data class MpLobby(
    val code: String = "",
    val gameId: String = "",
    val state: String = "Lobby",                   // Lobby | Countdown | Running | Results
    val mode: Int = Mp.MODE_RACE,
    val roadMapFileName: String? = null,
    val valueToReach: Int = 3,
    val teamSelectionEnabled: Boolean = false,
    val hostPlayerId: Int? = null,
    val maxPlayers: Int = 4,
    val players: List<MpPlayer> = emptyList(),
) {
    val occupied: List<MpPlayer> get() = players.filter { !it.emptySlot }
    val allReady: Boolean get() = occupied.size >= 2 && occupied.all { it.ready }
}

/** One row in the host-discovery list (roomList). */
@Serializable
data class MpRoomSummary(
    val code: String = "",
    val hostName: String = "",
    val mode: Int = Mp.MODE_RACE,
    val playerCount: Int = 0,
    val maxPlayers: Int = 4,
    val state: String = "Lobby",
)

/** A remote player's control input for a race tick (client → host → RaceEngine). */
@Serializable
data class MpControl(
    val playerId: Int = Mp.NO_PLAYER_ID,
    val throttle: Float = 0f,
    val lane: Int? = null,        // requested lane index (left/center/right…), null = no change
    val fireBay: String? = null,  // "attack" | "support" | null
)

/** One car's authoritative state in a raceState broadcast (host → all clients → remote HUD). */
@Serializable
data class MpCarState(
    val gamePlayerId: Int = Mp.NO_PLAYER_ID,
    val vehicleId: Int = 0,
    val ringIndex: Int = 0,
    val distAlongMm: Float = 0f,
    val speed: Int = 0,
    val lap: Int = 0,
    val place: Int = 0,
    val health: Int = 100,
    val energy: Int = 100,
    val offTrack: Boolean = false,
)

/** A final standings row (raceResults / results). */
@Serializable
data class MpStanding(
    val gamePlayerId: Int = Mp.NO_PLAYER_ID,
    val place: Int = 0,
    val displayName: String = "",
    val vehicleId: Int = 0,
    val laps: Int = 0,
    val finished: Boolean = false,
)
