package dev.overdrive.nav

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes, grouped to mirror the original 3.4.0 storyboards (Root, Garage,
 * OpenPlay/Game, Campaign/Tournament, Profile, Account, Store, Tracks, Settings). Each flow is a
 * nested graph marked by a `*Graph` object; the screens inside are the storyboard view controllers.
 *
 * The shared Race flow (MatchSetup → TrackScan → Countdown → InRaceHud → GameOver → RaceResults) is
 * reachable from both OpenPlay and Campaign, so it lives in its own graph.
 */
object Routes {

    @Serializable object Splash
    @Serializable object Home

    // ---- Garage graph (Garage_Storyboard) ----
    @Serializable object GarageGraph
    @Serializable object GarageHome
    @Serializable data class VehicleDetail(val carId: Int = -1)
    @Serializable data class WeaponLoadout(val carId: Int = -1)
    @Serializable data class WeaponPicker(val carId: Int = -1, val bay: String = "attack")
    @Serializable data class GarageUpgrades(val carId: Int = -1)
    @Serializable object GarageItems
    @Serializable object GarageDailySpecials
    @Serializable object ItemShop
    @Serializable data class ItemShopDetail(val itemId: String = "")

    // ---- OpenPlay graph (OpenPlayV2_Storyboard / OpenPlayHostLobby) ----
    @Serializable object OpenPlayGraph
    @Serializable object GameModeSelect
    @Serializable data class GameModeDetail(val mode: String = "")
    @Serializable object PlayerSelect
    @Serializable object VehicleSelect

    // ---- Race graph (GameV2_Storyboard) — shared by OpenPlay and Campaign ----
    @Serializable object RaceGraph
    @Serializable data class MatchSetup(val mode: String = "", val campaignMissionId: String = "", val ladderRung: Int = -1)
    @Serializable object TrackScan
    @Serializable object Countdown
    @Serializable object InRaceHud
    @Serializable object GameOver
    @Serializable data class RaceResults(val campaignMissionId: String = "")

    // ---- Tournament ladder (standalone bracket of AI commanders, separate from campaign missions) ----
    @Serializable object TournamentLadder

    // ---- Campaign graph (ChapterSelect_Storyboard / Tournament_Storyboard) ----
    @Serializable object CampaignGraph
    @Serializable object ChapterSelect
    @Serializable data class MissionSelect(val chapterId: Int = 0)
    @Serializable data class MissionDetail(val missionId: String = "")

    // ---- Profile graph (Profile_Storyboard) ----
    @Serializable object ProfileGraph
    @Serializable object ProfileHome
    @Serializable object AvatarSelect
    @Serializable object AvatarDetail
    @Serializable object ProfileMedals

    // ---- Account graph (Account_Storyboard) ----
    @Serializable object AccountGraph
    @Serializable object AccountHome
    @Serializable object AccountLogin
    @Serializable object AccountForgotPassword
    @Serializable object SavedAccounts
    @Serializable object AccountNewOrExisting
    @Serializable object AccountNameCheck
    @Serializable object AccountDateOfBirth
    @Serializable object AccountSignupEmail
    @Serializable object AccountSignupPassword
    @Serializable object AccountSignupConfirmation

    // ---- Store graph (Store_Storyboard) ----
    @Serializable object StoreGraph
    @Serializable object StoreHome
    @Serializable object StoreCheckoutSummary
    @Serializable object StoreCheckoutOptions

    // ---- Tracks graph (Tracks_Storyboard) ----
    @Serializable object TracksGraph
    @Serializable object TracksHome
    @Serializable data class TrackDetail(val trackId: String = "")

    // ---- 4.0.4 main-menu hubs (Home → Extras | Single Player | Multiplayer | Garage) ----
    @Serializable object SinglePlayer   // Campaign · Open Play · Test Track
    @Serializable object Extras         // Store · Profile · Coin Shop · Guide · Settings
    @Serializable object Multiplayer    // "coming soon" placeholder until Phase 12

    // ---- Top-level singletons ----
    @Serializable object CoinShop
    @Serializable object Guide
    @Serializable object NotificationPrompt
    @Serializable object Acknowledgements

    // ---- Settings graph ----
    @Serializable object SettingsGraph
    @Serializable object AppSettings
    @Serializable object DevSettings
    @Serializable object BleLab   // our diagnostic tool, lives under Settings
}
