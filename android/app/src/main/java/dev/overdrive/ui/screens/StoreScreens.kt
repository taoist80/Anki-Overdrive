package dev.overdrive.ui.screens

import androidx.compose.runtime.Composable
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Routes
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
fun CoinShopScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Coin Shop",
    onBack = { nav.back() },
    subtitle = "Buy in-game coins. Balance updates against the local economy service.",
    body = { CoinPill(0, OverdriveTheme.font) },
    actions = listOf(NavAction("Get 1,000 Coins", { nav.back() }, ButtonAccent.Gold)),
)
