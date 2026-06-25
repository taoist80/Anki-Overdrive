package dev.overdrive.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import dev.overdrive.AnkiCarManagerHolder
import dev.overdrive.LabScreen
import dev.overdrive.ui.screens.AccountDateOfBirthScreen
import dev.overdrive.ui.screens.AccountForgotPasswordScreen
import dev.overdrive.ui.screens.AccountHomeScreen
import dev.overdrive.ui.screens.AccountLoginScreen
import dev.overdrive.ui.screens.AccountNameCheckScreen
import dev.overdrive.ui.screens.AccountNewOrExistingScreen
import dev.overdrive.ui.screens.AccountSignupConfirmationScreen
import dev.overdrive.ui.screens.AccountSignupEmailScreen
import dev.overdrive.ui.screens.AccountSignupPasswordScreen
import dev.overdrive.ui.screens.AcknowledgementsScreen
import dev.overdrive.ui.screens.AppSettingsScreen
import dev.overdrive.ui.screens.AvatarDetailScreen
import dev.overdrive.ui.screens.AvatarSelectScreen
import dev.overdrive.ui.screens.ChapterSelectScreen
import dev.overdrive.ui.screens.CoinShopScreen
import dev.overdrive.ui.screens.CountdownScreen
import dev.overdrive.ui.screens.DevSettingsScreen
import dev.overdrive.ui.screens.GameModeDetailScreen
import dev.overdrive.ui.screens.GameModeSelectScreen
import dev.overdrive.ui.screens.GameOverScreen
import dev.overdrive.ui.screens.GarageDailySpecialsScreen
import dev.overdrive.ui.screens.GarageHomeScreen
import dev.overdrive.ui.screens.GarageItemsScreen
import dev.overdrive.ui.screens.GarageUpgradesScreen
import dev.overdrive.ui.screens.GuideScreen
import dev.overdrive.ui.screens.HomeScreen
import dev.overdrive.ui.screens.InRaceHudScreen
import dev.overdrive.ui.screens.ItemShopDetailScreen
import dev.overdrive.ui.screens.ItemShopScreen
import dev.overdrive.ui.screens.MatchSetupScreen
import dev.overdrive.ui.screens.MissionDetailScreen
import dev.overdrive.ui.screens.MissionSelectScreen
import dev.overdrive.ui.screens.NotificationPromptScreen
import dev.overdrive.ui.screens.PlayerSelectScreen
import dev.overdrive.ui.screens.ProfileHomeScreen
import dev.overdrive.ui.screens.ProfileMedalsScreen
import dev.overdrive.ui.screens.RaceResultsScreen
import dev.overdrive.ui.screens.SavedAccountsScreen
import dev.overdrive.ui.screens.StoreCheckoutOptionsScreen
import dev.overdrive.ui.screens.StoreCheckoutSummaryScreen
import dev.overdrive.ui.screens.StoreHomeScreen
import dev.overdrive.ui.screens.TrackDetailScreen
import dev.overdrive.ui.screens.TrackScanScreen
import dev.overdrive.ui.screens.TracksHomeScreen
import dev.overdrive.ui.screens.VehicleDetailScreen
import dev.overdrive.ui.screens.VehicleSelectScreen

/**
 * The full navigation graph. Each storyboard flow is a nested [navigation] graph so flow-scoped
 * ViewModels can attach later (Phase 2/3); for now every destination renders a chrome stub or a
 * real screen. Mirrors the 3.4.0 NavigationController/Storyboard topology.
 */
@Composable
fun OverdriveNavHost(nav: OverdriveNav) {
    NavHost(navController = nav.controller, startDestination = Routes.Home) {

        composable<Routes.Home> { HomeScreen(nav) }

        // ---- Garage ----
        navigation<Routes.GarageGraph>(startDestination = Routes.GarageHome) {
            composable<Routes.GarageHome> { GarageHomeScreen(nav) }
            composable<Routes.VehicleDetail> { VehicleDetailScreen(nav, it.toRoute<Routes.VehicleDetail>().carId) }
            composable<Routes.GarageUpgrades> { GarageUpgradesScreen(nav) }
            composable<Routes.GarageItems> { GarageItemsScreen(nav) }
            composable<Routes.GarageDailySpecials> { GarageDailySpecialsScreen(nav) }
            composable<Routes.ItemShop> { ItemShopScreen(nav) }
            composable<Routes.ItemShopDetail> { ItemShopDetailScreen(nav, it.toRoute<Routes.ItemShopDetail>().itemId) }
        }

        // ---- OpenPlay (mode + lobby) ----
        navigation<Routes.OpenPlayGraph>(startDestination = Routes.GameModeSelect) {
            composable<Routes.GameModeSelect> { GameModeSelectScreen(nav) }
            composable<Routes.GameModeDetail> { GameModeDetailScreen(nav, it.toRoute<Routes.GameModeDetail>().mode) }
            composable<Routes.PlayerSelect> { PlayerSelectScreen(nav) }
            composable<Routes.VehicleSelect> { VehicleSelectScreen(nav) }
        }

        // ---- Race (shared by OpenPlay + Campaign) ----
        navigation<Routes.RaceGraph>(startDestination = Routes.MatchSetup()) {
            composable<Routes.MatchSetup> {
                val a = it.toRoute<Routes.MatchSetup>()
                MatchSetupScreen(nav, a.mode, a.campaignMissionId)
            }
            composable<Routes.TrackScan> { TrackScanScreen(nav) }
            composable<Routes.Countdown> { CountdownScreen(nav) }
            composable<Routes.InRaceHud> { InRaceHudScreen(nav) }
            composable<Routes.GameOver> { GameOverScreen(nav) }
            composable<Routes.RaceResults> { RaceResultsScreen(nav, it.toRoute<Routes.RaceResults>().campaignMissionId) }
        }

        // ---- Campaign ----
        navigation<Routes.CampaignGraph>(startDestination = Routes.ChapterSelect) {
            composable<Routes.ChapterSelect> { ChapterSelectScreen(nav) }
            composable<Routes.MissionSelect> { MissionSelectScreen(nav, it.toRoute<Routes.MissionSelect>().chapterId) }
            composable<Routes.MissionDetail> { MissionDetailScreen(nav, it.toRoute<Routes.MissionDetail>().missionId) }
        }

        // ---- Profile ----
        navigation<Routes.ProfileGraph>(startDestination = Routes.ProfileHome) {
            composable<Routes.ProfileHome> { ProfileHomeScreen(nav) }
            composable<Routes.AvatarDetail> { AvatarDetailScreen(nav) }
            composable<Routes.AvatarSelect> { AvatarSelectScreen(nav) }
            composable<Routes.ProfileMedals> { ProfileMedalsScreen(nav) }
        }

        // ---- Account ----
        navigation<Routes.AccountGraph>(startDestination = Routes.AccountHome) {
            composable<Routes.AccountHome> { AccountHomeScreen(nav) }
            composable<Routes.AccountLogin> { AccountLoginScreen(nav) }
            composable<Routes.AccountForgotPassword> { AccountForgotPasswordScreen(nav) }
            composable<Routes.SavedAccounts> { SavedAccountsScreen(nav) }
            composable<Routes.AccountNewOrExisting> { AccountNewOrExistingScreen(nav) }
            composable<Routes.AccountNameCheck> { AccountNameCheckScreen(nav) }
            composable<Routes.AccountDateOfBirth> { AccountDateOfBirthScreen(nav) }
            composable<Routes.AccountSignupEmail> { AccountSignupEmailScreen(nav) }
            composable<Routes.AccountSignupPassword> { AccountSignupPasswordScreen(nav) }
            composable<Routes.AccountSignupConfirmation> { AccountSignupConfirmationScreen(nav) }
        }

        // ---- Store ----
        navigation<Routes.StoreGraph>(startDestination = Routes.StoreHome) {
            composable<Routes.StoreHome> { StoreHomeScreen(nav) }
            composable<Routes.StoreCheckoutSummary> { StoreCheckoutSummaryScreen(nav) }
            composable<Routes.StoreCheckoutOptions> { StoreCheckoutOptionsScreen(nav) }
        }

        // ---- Tracks ----
        navigation<Routes.TracksGraph>(startDestination = Routes.TracksHome) {
            composable<Routes.TracksHome> { TracksHomeScreen(nav) }
            composable<Routes.TrackDetail> { TrackDetailScreen(nav, it.toRoute<Routes.TrackDetail>().trackId) }
        }

        // ---- Settings ----
        navigation<Routes.SettingsGraph>(startDestination = Routes.AppSettings) {
            composable<Routes.AppSettings> { AppSettingsScreen(nav) }
            composable<Routes.DevSettings> { DevSettingsScreen(nav) }
            composable<Routes.BleLab> { LabScreen(AnkiCarManagerHolder.require()) }
        }

        // ---- Top-level singletons ----
        composable<Routes.CoinShop> { CoinShopScreen(nav) }
        composable<Routes.Guide> { GuideScreen(nav) }
        composable<Routes.NotificationPrompt> { NotificationPromptScreen(nav) }
        composable<Routes.Acknowledgements> { AcknowledgementsScreen(nav) }
    }
}
