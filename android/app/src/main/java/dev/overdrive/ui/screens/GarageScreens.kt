package dev.overdrive.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.overdrive.GameData
import dev.overdrive.game.MetaGame
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Routes
import dev.overdrive.profile.ProfileRepository
import dev.overdrive.ui.components.ButtonAccent
import dev.overdrive.ui.components.CarHero
import dev.overdrive.ui.components.CoinPill
import dev.overdrive.ui.components.NavAction
import dev.overdrive.ui.components.OverdrivePanel
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
fun GarageUpgradesScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    remember { ProfileRepository.load(ctx); 0 }
    val profile = ProfileRepository.profile
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    OverdriveScaffold(title = "Upgrades", onBack = { nav.back() }, right = { CoinPill(profile.coins, font) }) { mod ->
        Column(mod.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Spend coins to upgrade your fleet.", fontFamily = font, color = colors.textDim, fontSize = 13.sp)
            MetaGame.UPGRADE_TRACKS.forEach { track ->
                val level = profile.upgradeLevel(track.key)
                val cost = MetaGame.upgradeCost(track, level)
                val maxed = level >= track.maxLevel
                val canAfford = profile.coins >= cost
                OverdrivePanel(Modifier.fillMaxWidth()) { inner ->
                    Row(inner.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(track.name.uppercase(), fontFamily = font, color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(track.maxLevel) { i ->
                                    Box(Modifier.width(22.dp).height(8.dp).clip(RoundedCornerShape(2.dp))
                                        .background(if (i < level) colors.gold else colors.barEmpty))
                                }
                            }
                        }
                        if (maxed) Text("MAX", fontFamily = font, color = colors.gold, fontWeight = FontWeight.Bold)
                        else PrimaryButton(
                            "$cost ◉",
                            { ProfileRepository.buyUpgrade(ctx, track.key, cost) },
                            accent = if (canAfford) ButtonAccent.Gold else ButtonAccent.Outline,
                            enabled = canAfford,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GarageItemsScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    remember { ProfileRepository.load(ctx); 0 }
    val inv = ProfileRepository.profile.inventory
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    OverdriveScaffold(title = "Items", onBack = { nav.back() }) { mod ->
        Column(mod.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (inv.isEmpty()) {
                Text("No items yet — open loot boxes after races to earn weapons & mods.", fontFamily = font, color = colors.textDim, fontSize = 14.sp)
            }
            inv.entries.sortedByDescending { it.value }.forEach { (id, count) ->
                OverdrivePanel(Modifier.fillMaxWidth()) { inner ->
                    Row(inner.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(MetaGame.itemName(id), fontFamily = font, color = colors.textPrimary, fontSize = 16.sp)
                        Text("×$count", fontFamily = font, color = colors.gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

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
