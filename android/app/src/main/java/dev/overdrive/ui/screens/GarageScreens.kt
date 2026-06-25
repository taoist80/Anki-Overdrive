package dev.overdrive.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.overdrive.GameData
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Routes
import dev.overdrive.profile.ProfileRepository
import dev.overdrive.ui.components.ButtonAccent
import dev.overdrive.ui.components.CarHero
import dev.overdrive.ui.components.CoinPill
import dev.overdrive.ui.components.NavAction
import dev.overdrive.ui.components.OverdriveScaffold
import dev.overdrive.ui.components.PrimaryButton
import dev.overdrive.ui.components.WireframeScreen
import dev.overdrive.ui.theme.OverdriveTheme

@Composable
fun GarageHomeScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    remember { GameData.load(ctx); ProfileRepository.load(ctx); 0 }
    val cars = GameData.cars
    val coins = ProfileRepository.profile.coins

    OverdriveScaffold(
        title = "Garage",
        onBack = { nav.back() },
        right = { CoinPill(coins, font) },
    ) { mod ->
        Column(mod) {
            if (cars.isEmpty()) {
                Text("No vehicles loaded", color = colors.textDim, fontFamily = font, modifier = Modifier.padding(24.dp))
                return@Column
            }
            val pager = rememberPagerState(pageCount = { cars.size })
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                CarHero(cars[page])
            }
            Text(
                "${pager.currentPage + 1} / ${cars.size}   ·   swipe to browse",
                fontFamily = font, color = colors.textDim, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 10.dp),
            )
            val current = cars[pager.currentPage]
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Details", { nav.go(Routes.VehicleDetail(current.id)) }, Modifier.weight(1f), ButtonAccent.Blue)
                PrimaryButton("Upgrades", { nav.go(Routes.GarageUpgrades) }, Modifier.weight(1f), ButtonAccent.Outline)
                PrimaryButton("Items", { nav.go(Routes.GarageItems) }, Modifier.weight(1f), ButtonAccent.Outline)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Daily Specials", { nav.go(Routes.GarageDailySpecials) }, Modifier.weight(1f), ButtonAccent.Gold)
                PrimaryButton("Item Shop", { nav.go(Routes.ItemShop) }, Modifier.weight(1f), ButtonAccent.Outline)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun VehicleDetailScreen(nav: OverdriveNav, carId: Int) {
    val car = remember(carId) { GameData.cars.firstOrNull { it.id == carId } }
    WireframeScreen(
        title = car?.name ?: "Vehicle",
        onBack = { nav.back() },
        subtitle = "Vehicle detail: portrait, stats, equipped weapon/support bays, upgrade slots, " +
            "and tuning. Wires to ContentRepository + ProfileRepository in Phase 1/3.",
        actions = listOf(
            NavAction("Upgrades", { nav.go(Routes.GarageUpgrades) }, ButtonAccent.Blue),
            NavAction("Items", { nav.go(Routes.GarageItems) }, ButtonAccent.Outline),
        ),
    )
}

@Composable
fun GarageUpgradesScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Upgrades",
    onBack = { nav.back() },
    subtitle = "Spend coins/XP on per-vehicle upgrade cards (speed, weapons, defense). " +
        "Sourced from upgradeData.json.",
)

@Composable
fun GarageItemsScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Items",
    onBack = { nav.back() },
    subtitle = "Equip weapons/support items into the vehicle's bays. Sourced from items.json.",
)

@Composable
fun GarageDailySpecialsScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Daily Specials",
    onBack = { nav.back() },
    subtitle = "Rotating daily offers (itemShopSchedule). Buy with coins.",
    actions = listOf(NavAction("Open Item Shop", { nav.go(Routes.ItemShop) }, ButtonAccent.Gold)),
)

@Composable
fun ItemShopScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Item Shop",
    onBack = { nav.back() },
    subtitle = "Catalog of purchasable upgrades/items. Tap an item for detail.",
    actions = listOf(NavAction("Sample Item Detail", { nav.go(Routes.ItemShopDetail("nitro")) }, ButtonAccent.Blue)),
)

@Composable
fun ItemShopDetailScreen(nav: OverdriveNav, itemId: String) = WireframeScreen(
    title = "Item: $itemId",
    onBack = { nav.back() },
    subtitle = "Item detail card with stats, price, and a Buy action (economy via local backend, Phase 4).",
)
