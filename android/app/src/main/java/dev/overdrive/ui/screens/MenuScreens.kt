package dev.overdrive.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import dev.overdrive.data.ContentRepository
import dev.overdrive.data.ItemRepository
import dev.overdrive.data.model.GameItem
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Routes
import dev.overdrive.profile.ProfileRepository
import dev.overdrive.ui.components.CoinPill
import dev.overdrive.ui.components.OverdriveBackground
import dev.overdrive.ui.components.OverdriveScaffold
import dev.overdrive.ui.components.RacingName
import dev.overdrive.ui.theme.OverdriveTheme
import dev.overdrive.ui.theme.rememberAsset

/**
 * The three 4.0.4 main-menu hubs reached from [HomeScreen] (Garage is its own top-level entry and
 * routes straight into the Garage graph). Layout mirrors the DDL 4.0.4 captures; chrome inherits the
 * violet theme via OverdriveScaffold + the shared components.
 */

/** Single Player: Campaign · Tournament · Open Play · Test Track, as full-height portrait cards. */
@Composable
fun SinglePlayerScreen(nav: OverdriveNav) {
    OverdriveScaffold(title = "Single Player", onBack = { nav.back() }) { mod ->
        val colors = OverdriveTheme.colors
        Row(mod.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HubCard("Campaign", "Earn stars, unlock chapters", colors.blue,
                "ui/ui_selectMode_tournament.png", Modifier.weight(1f)) { nav.go(Routes.CampaignGraph) }
            HubCard("Tournament", "Climb the AI commander ladder", colors.danger,
                "ui/ui_selectMode_tournament.png", Modifier.weight(1f)) { nav.go(Routes.TournamentLadder) }
            HubCard("Open Play", "Quick race or battle — your rules", colors.gold,
                "ui/ui_selectMode_openPlay.png", Modifier.weight(1f)) { nav.go(Routes.OpenPlayGraph) }
            HubCard("Test Track", "Free drive, no scoring", colors.success,
                "ui/ui_selectMode_practice.png", Modifier.weight(1f)) { nav.go(Routes.TracksGraph) }
        }
    }
}

/**
 * Extras — the authentic 4.0.4 tabbed shell (reference 14_extras / 20_settings): a top bar with the
 * coin balance, a help (?) link, SHOP / SETTINGS tabs and a GARAGE button; the content swaps by tab.
 * SHOP is a horizontal item carousel with coin prices; SETTINGS is the in-game controls list.
 */
@Composable
fun ExtrasScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    remember { ContentRepository.load(ctx); ProfileRepository.load(ctx); 0 }
    var tab by remember { mutableStateOf("shop") }
    val coins = ProfileRepository.profile.coins

    OverdriveBackground(heroImage = null) {
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Column(Modifier.fillMaxSize().padding(top = top)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ExtrasBack { nav.back() }
                Box(Modifier.clickable { nav.go(Routes.CoinShop) }) { CoinPill(coins, font) }
                TabBtn("?", false) { nav.go(Routes.Guide) }
                Spacer(Modifier.weight(1f))
                TabBtn("SHOP", tab == "shop") { tab = "shop" }
                TabBtn("SETTINGS", tab == "settings") { tab = "settings" }
                TabBtn("GARAGE", false) { nav.go(Routes.GarageGraph) }
            }
            when (tab) {
                "shop" -> ExtrasShop(nav)
                else -> ExtrasSettings(nav)
            }
        }
    }
}

@Composable
private fun ExtrasBack(onBack: () -> Unit) {
    val colors = OverdriveTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.size(40.dp, 36.dp).clip(shape).background(Color(0x14FFFFFF)).border(1.dp, colors.panelBorder, shape).clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) { Text("‹", color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun TabBtn(label: String, on: Boolean, onClick: () -> Unit) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier.clip(shape).background(if (on) Color(0x1CFFFFFF) else Color(0x0DFFFFFF))
            .border(1.dp, if (on) colors.blue else colors.panelBorder, shape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(label, fontFamily = font, color = if (on) colors.textPrimary else colors.textDim, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun ExtrasShop(nav: OverdriveNav) {
    val shop = remember { ItemRepository.catalog.filter { ItemRepository.imageAsset(it.id) != null && it.cost > 0 }.take(16) }
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    if (shop.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Shop unavailable.", fontFamily = font, color = colors.textDim, fontSize = 14.sp)
        }
        return
    }
    LazyRow(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(shop) { gi -> ShopItemCard(gi) { nav.go(Routes.ItemShopDetail(gi.id)) } }
    }
}

@Composable
private fun ShopItemCard(item: GameItem, onClick: () -> Unit) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val nm = ItemRepository.name(item.id)
    val art = rememberAsset(ItemRepository.imageAsset(item.id))
    val price = ItemRepository.itemPrice(item)
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.width(160.dp).fillMaxHeight().clip(shape)
            .background(Brush.verticalGradient(listOf(colors.panel.copy(alpha = 0.82f), colors.surface.copy(alpha = 0.82f))))
            .border(1.dp, colors.panelBorder, shape).clickable(onClick = onClick).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (art != null) Image(art, nm, Modifier.fillMaxWidth().weight(1f), contentScale = ContentScale.Fit)
        else Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(8.dp))
        Text(nm.uppercase(), fontFamily = font, color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(colors.gold))
            Text("$price", fontFamily = font, color = colors.gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExtrasSettings(nav: OverdriveNav) {
    var steer by remember { mutableStateOf(50) }
    var music by remember { mutableStateOf(100) }
    var hGame by remember { mutableStateOf(true) }
    var hUi by remember { mutableStateOf(true) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsHeader("IN GAME")
        Stepper("Steering Sensitivity", "$steer%", { steer = (steer - 10).coerceAtLeast(0) }, { steer = (steer + 10).coerceAtMost(100) })
        SettingsHeader("AUDIO")
        Stepper("Music", "$music%", { music = (music - 10).coerceAtLeast(0) }, { music = (music + 10).coerceAtMost(100) })
        SettingsHeader("HAPTICS")
        ToggleX("Enable in game", hGame) { hGame = !hGame }
        ToggleX("Enable in UI", hUi) { hUi = !hUi }
        SettingsHeader("ACCOUNT")
        SettingLink("Driver Profile") { nav.go(Routes.ProfileGraph) }
        SettingLink("Anki Account") { nav.go(Routes.AccountGraph) }
        SettingLink("Acknowledgements") { nav.go(Routes.Acknowledgements) }
        SettingLink("Developer / BLE Lab") { nav.go(Routes.DevSettings) }
    }
}

@Composable
private fun SettingsHeader(t: String) {
    Text(t, fontFamily = OverdriveTheme.font, color = OverdriveTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StepBtn("–", onMinus)
        StepBtn("+", onPlus)
        Text("$label  $value", fontFamily = font, color = colors.textDim, fontSize = 14.sp)
    }
}

@Composable
private fun StepBtn(s: String, onClick: () -> Unit) {
    val colors = OverdriveTheme.colors
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier.size(30.dp).clip(shape).background(Color(0x16FFFFFF)).border(1.dp, colors.panelBorder, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(s, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ToggleX(label: String, on: Boolean, onClick: () -> Unit) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val shape = RoundedCornerShape(6.dp)
    Row(Modifier.clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(26.dp).clip(shape).background(if (on) colors.blue.copy(alpha = 0.3f) else Color(0x14FFFFFF)).border(1.dp, if (on) colors.blue else colors.panelBorder, shape),
            contentAlignment = Alignment.Center,
        ) { Text(if (on) "✓" else "✕", color = if (on) colors.blue else colors.textDim, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        Text(label, fontFamily = font, color = colors.textDim, fontSize = 14.sp)
    }
}

@Composable
private fun SettingLink(label: String, onClick: () -> Unit) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    Text(label, fontFamily = font, color = colors.blue, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp))
}

// MultiplayerScreen now lives in MultiplayerScreens.kt (Phase 12 — host/join + lobby).

// ---- building blocks --------------------------------------------------------

@Composable
private fun HubCard(
    name: String,
    desc: String,
    accent: Color,
    image: String?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val art = rememberAsset(image)
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .fillMaxHeight()
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(colors.panel.copy(alpha = 0.5f), colors.surface.copy(alpha = 0.88f)))
            )
            .border(1.dp, colors.panelBorder, shape)
            .clickable(onClick = onClick),
    ) {
        // full-bleed illustration, then a bottom scrim so the label band stays legible
        if (art != null) {
            Image(art, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xE6120A22))
                )
            )
        }
        Column(
            Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            RacingName(name, fontSize = 26, hlColor = accent)
            Spacer(Modifier.height(6.dp))
            Text(desc, fontFamily = font, color = colors.textDim, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "▸ ENTER", fontFamily = font, color = accent, fontSize = 12.sp,
                letterSpacing = 2.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ExtrasTile(label: String, sub: String, onClick: () -> Unit) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .width(168.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(colors.panel.copy(alpha = 0.82f), colors.surface.copy(alpha = 0.82f)))
            )
            .border(1.dp, colors.panelBorder, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(
            label.uppercase(), fontFamily = font, color = colors.textPrimary, fontSize = 16.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(sub, fontFamily = font, color = colors.textDim, fontSize = 12.sp)
    }
}
