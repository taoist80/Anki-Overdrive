package dev.overdrive.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Routes
import dev.overdrive.profile.ProfileRepository
import dev.overdrive.ui.components.ButtonAccent
import dev.overdrive.ui.components.CoinPill
import dev.overdrive.ui.components.NavAction
import dev.overdrive.ui.components.WireframeScreen
import dev.overdrive.ui.theme.OverdriveTheme

@Composable
fun StoreHomeScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Store",
    onBack = { nav.back() },
    subtitle = "Purchasable bundles, vehicles, and cosmetics. Backed by the local store/economy service (Phase 4).",
    actions = listOf(NavAction("Sample Checkout", { nav.go(Routes.StoreCheckoutSummary) }, ButtonAccent.Gold)),
)

@Composable
fun StoreCheckoutSummaryScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Checkout",
    onBack = { nav.back() },
    subtitle = "Order summary before payment.",
    actions = listOf(NavAction("Continue", { nav.go(Routes.StoreCheckoutOptions) }, ButtonAccent.Blue)),
)

@Composable
fun StoreCheckoutOptionsScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Payment",
    onBack = { nav.back() },
    subtitle = "Select a payment method and confirm (local emulation — no real charges).",
    actions = listOf(NavAction("Confirm Purchase", { nav.back() }, ButtonAccent.Gold)),
)

@Composable
fun CoinShopScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    remember { ProfileRepository.load(ctx); 0 }
    val profile = ProfileRepository.profile
    WireframeScreen(
        title = "Coin Shop",
        onBack = { nav.back() },
        subtitle = "Buy in-game coins. Balance persists locally (and syncs to the backend in Phase 4).",
        body = { CoinPill(profile.coins, OverdriveTheme.font) },
        actions = listOf(NavAction("Get 1,000 Coins", { ProfileRepository.addCoins(ctx, 1000) }, ButtonAccent.Gold)),
    )
}
