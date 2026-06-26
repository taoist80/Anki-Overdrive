package dev.overdrive.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.overdrive.CarType
import dev.overdrive.GameData
import dev.overdrive.data.ItemRepository
import dev.overdrive.data.model.Bay
import dev.overdrive.data.model.GameItem
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
import dev.overdrive.ui.components.RacingName
import dev.overdrive.ui.components.StatBars
import dev.overdrive.ui.components.WireframeScreen
import dev.overdrive.ui.theme.OverdriveTheme
import dev.overdrive.ui.theme.rememberAsset

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
                PrimaryButton("Upgrades", { nav.go(Routes.GarageUpgrades(current.id)) }, Modifier.weight(1f), ButtonAccent.Outline)
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

/**
 * Car detail (4.0.4 layout): a hero card on the left + two big action cards — WEAPONS (→ the bay
 * loadout) and UPGRADES. The WEAPONS card previews the currently-equipped attack/support canisters.
 */
@Composable
fun VehicleDetailScreen(nav: OverdriveNav, carId: Int) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    remember { GameData.load(ctx); ItemRepository.load(ctx); ProfileRepository.load(ctx); 0 }
    val car = remember(carId) { GameData.cars.firstOrNull { it.id == carId } }
    val profile = ProfileRepository.profile

    if (car == null) {
        WireframeScreen(title = "Vehicle", onBack = { nav.back() }, subtitle = "Vehicle not found.")
        return
    }

    OverdriveScaffold(title = car.name, onBack = { nav.back() }) { mod ->
        Row(mod.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Hero card
            OverdrivePanel(Modifier.weight(1.2f).fillMaxHeight()) { inner ->
                Column(inner.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    RacingName(car.name, fontSize = 30, modifier = Modifier.align(Alignment.Start))
                    Text(car.category.label.uppercase(), fontFamily = font, color = colors.gold,
                        fontSize = 12.sp, letterSpacing = 1.5.sp, modifier = Modifier.align(Alignment.Start).padding(top = 2.dp))
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CarSprite(car, fraction = 0.92f)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        StatBars("ATTACK", car.attackBays)
                        StatBars("SUPPORT", car.supportBays)
                    }
                }
            }
            // WEAPONS — previews equipped attack + support canisters
            val atkId = profile.equipped(carId, "attack") ?: ItemRepository.defaultItem(Bay.ATTACK, car.name)?.id
            val supId = profile.equipped(carId, "support") ?: ItemRepository.defaultItem(Bay.SUPPORT, car.name)?.id
            DetailActionCard(
                label = "Weapons",
                accent = colors.blue,
                caption = "Equip your bays",
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { nav.go(Routes.WeaponLoadout(car.id)) },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    WeaponGlyph(atkId, size = 64)
                    WeaponGlyph(supId, size = 64)
                }
            }
            // UPGRADES
            DetailActionCard(
                label = "Upgrades",
                accent = colors.gold,
                caption = "Speed · armor · energy",
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { nav.go(Routes.GarageUpgrades(car.id)) },
            ) {
                Text("⬡", color = colors.gold.copy(alpha = 0.7f), fontSize = 52.sp)
            }
        }
    }
}

/**
 * Weapons loadout (4.0.4): one card per bay the car owns — ATTACK and SUPPORT always, SPECIAL only
 * when the car has special items. Each shows the equipped weapon (or its real default) and opens the
 * picker. Equipping writes [ProfileRepository.equipItem], which the in-race HUD + combat read live.
 */
@Composable
fun WeaponLoadoutScreen(nav: OverdriveNav, carId: Int) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    remember { GameData.load(ctx); ItemRepository.load(ctx); ProfileRepository.load(ctx); 0 }
    val car = remember(carId) { GameData.cars.firstOrNull { it.id == carId } }
    val profile = ProfileRepository.profile

    val bays = remember(carId) {
        buildList {
            add(Bay.ATTACK); add(Bay.SUPPORT)
            if (ItemRepository.equippableFor(Bay.SPECIAL, car?.name).isNotEmpty()) add(Bay.SPECIAL)
        }
    }

    OverdriveScaffold(title = "${car?.name ?: "Vehicle"} · Weapons", onBack = { nav.back() }) { mod ->
        Row(mod.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            bays.forEach { bay ->
                val bayKey = bay.name.lowercase()
                val explicit = profile.equipped(carId, bayKey)
                val effectiveId = explicit ?: ItemRepository.defaultItem(bay, car?.name)?.id
                BaySlotCard(
                    bay = bay,
                    itemId = effectiveId,
                    isDefault = explicit == null && effectiveId != null,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { nav.go(Routes.WeaponPicker(carId, bayKey)) },
                )
            }
        }
    }
}

/**
 * Weapon picker (4.0.4 layout, 3.4.0 ownership): a horizontal rail of every weapon this car can equip
 * in the bay, plus a "No Weapon" card. Owned weapons (level-1 starters + looted/bought) equip on tap;
 * locked weapons show their price and buy on tap (spending coins), or are greyed when unaffordable.
 */
@Composable
fun WeaponPickerScreen(nav: OverdriveNav, carId: Int, bay: String) {
    val ctx = LocalContext.current
    val font = OverdriveTheme.font
    remember { GameData.load(ctx); ItemRepository.load(ctx); ProfileRepository.load(ctx); 0 }
    val car = remember(carId) { GameData.cars.firstOrNull { it.id == carId } }
    val bayEnum = Bay.from(bay)
    val options = remember(carId, bay) { ItemRepository.equippableFor(bayEnum, car?.name) }
    val profile = ProfileRepository.profile
    val equippedId = profile.equipped(carId, bay)

    OverdriveScaffold(title = "${bay.uppercase()} Bay", onBack = { nav.back() }, right = { CoinPill(profile.coins, font) }) { mod ->
        LazyRow(
            mod,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            item {
                WeaponCard(item = null, selected = equippedId == null, owned = true, price = 0, affordable = true) {
                    ProfileRepository.equipItem(ctx, carId, bay, null); nav.back()
                }
            }
            items(options) { it ->
                val owned = profile.owns(it)
                val price = ItemRepository.itemPrice(it)
                val affordable = profile.coins >= price
                WeaponCard(item = it, selected = it.id == equippedId, owned = owned, price = price, affordable = affordable) {
                    when {
                        owned -> { ProfileRepository.equipItem(ctx, carId, bay, it.id); nav.back() }
                        affordable && ProfileRepository.spendCoins(ctx, price) -> {
                            ProfileRepository.addItem(ctx, it.id)
                            ProfileRepository.equipItem(ctx, carId, bay, it.id)
                            nav.back()
                        }
                        // unowned + unaffordable: not clickable (card greyed)
                    }
                }
            }
        }
    }
}

// ---- garage building blocks -------------------------------------------------

@Composable
private fun CarSprite(car: CarType, fraction: Float) {
    val colors = OverdriveTheme.colors
    val sprite = rememberAsset(car.spriteFile?.let { "cars/$it" })
    if (sprite != null) Image(sprite, car.name, Modifier.fillMaxWidth(fraction), contentScale = ContentScale.Fit)
    else Box(Modifier.fillMaxWidth(fraction).height(80.dp).clip(RoundedCornerShape(8.dp)).background(colors.barEmpty))
}

/** A weapon's canister render (detail art) or its hitzone-class icon fallback; empty box if neither. */
@Composable
private fun WeaponGlyph(itemId: String?, size: Int) {
    val colors = OverdriveTheme.colors
    val art = rememberAsset(ItemRepository.imageAsset(itemId))
    if (art != null) Image(art, null, Modifier.height(size.dp), contentScale = ContentScale.Fit)
    else Box(Modifier.height(size.dp).fillMaxWidth(0.5f).clip(RoundedCornerShape(8.dp)).background(colors.barEmpty))
}

/** One of the two big Car-Detail action cards (WEAPONS / UPGRADES). */
@Composable
private fun DetailActionCard(
    label: String,
    accent: Color,
    caption: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    glyph: @Composable () -> Unit,
) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(colors.panel.copy(alpha = 0.7f))
            .border(1.dp, accent.copy(alpha = 0.45f), shape)
            .clickable(onClick = onClick)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(Modifier.height(2.dp))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { glyph() }
        Text(label.uppercase(), fontFamily = font, color = colors.textPrimary, fontSize = 18.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text(caption, fontFamily = font, color = colors.textDim, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

/** A bay slot on the loadout screen: bay label + equipped weapon canister/name/description. */
@Composable
private fun BaySlotCard(bay: Bay, itemId: String?, isDefault: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val accent = when (bay) {
        Bay.ATTACK -> colors.orange
        Bay.SUPPORT -> colors.blue
        else -> colors.gold
    }
    val shape = RoundedCornerShape(12.dp)
    val hasItem = itemId != null
    Column(
        modifier
            .clip(shape)
            .background(colors.panel.copy(alpha = 0.7f))
            .border(1.dp, accent.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text("${bay.name} BAY", fontFamily = font, color = accent, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        Text(
            if (hasItem) ItemRepository.name(itemId) else "No Weapon",
            fontFamily = font, color = if (hasItem) colors.textPrimary else colors.textDim,
            fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(top = 8.dp),
        )
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (hasItem) WeaponGlyph(itemId, size = 112)
            else Box(
                Modifier.fillMaxWidth(0.8f).height(96.dp).clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, colors.panelBorder, RoundedCornerShape(12.dp)),
            )
        }
        Text(
            if (hasItem) ItemRepository.description(itemId).take(80) else "This bay is empty.",
            fontFamily = font, color = colors.textDim, fontSize = 11.sp, maxLines = 3,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (isDefault) "DEFAULT · TAP TO CHANGE ▸" else "TAP TO CHANGE ▸",
            fontFamily = font, color = colors.gold, fontSize = 10.sp, letterSpacing = 1.sp,
        )
    }
}

/**
 * A weapon option in the picker rail (or the "No Weapon" clear card when [item] is null). Renders
 * the ownership state: equipped, owned, buyable (shows price), or locked-unaffordable (greyed).
 */
@Composable
private fun WeaponCard(
    item: GameItem?,
    selected: Boolean,
    owned: Boolean,
    price: Int,
    affordable: Boolean,
    onClick: () -> Unit,
) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val shape = RoundedCornerShape(12.dp)
    val locked = item != null && !owned
    val enabled = owned || affordable      // owned → equip; buyable → buy; else not tappable
    val border = when { selected -> colors.blue; locked && affordable -> colors.gold; else -> colors.panelBorder }
    Column(
        Modifier
            .width(190.dp)
            .fillMaxHeight()
            .clip(shape)
            .background(colors.panel.copy(alpha = if (selected) 0.9f else 0.6f))
            .border(if (selected) 2.dp else 1.dp, border, shape)
            .alpha(if (locked && !affordable) 0.5f else 1f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
    ) {
        Text(item?.let { ItemRepository.name(it.id) } ?: "No Weapon",
            fontFamily = font, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        val (statusText, statusColor) = when {
            item == null -> "Clear this bay" to colors.textDim
            selected -> "Level ${item.level} · equipped" to colors.blue
            owned -> "Level ${item.level} · owned" to colors.success
            affordable -> "🔒 BUY  ·  $price ◉" to colors.gold
            else -> "🔒 LOCKED  ·  $price ◉" to colors.textDim
        }
        Text(statusText, fontFamily = font, color = statusColor, fontSize = 10.sp, letterSpacing = 1.sp)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (item != null) Box(Modifier.alpha(if (locked) 0.6f else 1f)) { WeaponGlyph(item.id, size = 96) }
            else Box(
                Modifier.fillMaxWidth(0.8f).height(80.dp).clip(RoundedCornerShape(10.dp))
                    .border(1.5.dp, colors.panelBorder, RoundedCornerShape(10.dp)),
            )
        }
        Text(
            item?.let { ItemRepository.description(it.id).take(72) } ?: "Run this bay empty — pure driving.",
            fontFamily = font, color = colors.textDim, fontSize = 10.sp, maxLines = 3,
        )
    }
}

@Composable
fun GarageUpgradesScreen(nav: OverdriveNav, carId: Int) {
    val ctx = LocalContext.current
    remember { GameData.load(ctx); ProfileRepository.load(ctx); 0 }
    val profile = ProfileRepository.profile
    val car = remember(carId) { GameData.cars.firstOrNull { it.id == carId } }
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    OverdriveScaffold(title = "${car?.name ?: "Vehicle"} · Upgrades", onBack = { nav.back() }, right = { CoinPill(profile.coins, font) }) { mod ->
        Column(mod.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Spend coins to upgrade ${car?.name ?: "this car"}. Upgrades apply in your races: " +
                    "Speed (top straight-line speed), Weapons (damage), Defense (incoming damage), Energy (recharge).",
                fontFamily = font, color = colors.textDim, fontSize = 13.sp,
            )
            MetaGame.UPGRADE_TRACKS.forEach { track ->
                val key = MetaGame.upgradeKey(carId, track)
                val level = profile.upgradeLevel(key)
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
                            { ProfileRepository.buyUpgrade(ctx, key, cost) },
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

/** Items you can acquire: every named real weapon above level 1 (level-1s are free starters). */
private fun buyablePool(): List<GameItem> =
    ItemRepository.catalog.filter { ItemRepository.equippable(it) && it.level >= 2 }
        .sortedWith(compareBy({ it.bay.name }, { it.level }, { ItemRepository.name(it.id) }))

/** Item shop: a browsable grid of every acquirable weapon; tap one for its detail + Buy. */
@Composable
fun ItemShopScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    val font = OverdriveTheme.font
    val colors = OverdriveTheme.colors
    remember { ItemRepository.load(ctx); ProfileRepository.load(ctx); 0 }
    val profile = ProfileRepository.profile
    val pool = remember { buyablePool() }

    OverdriveScaffold(title = "Item Shop", onBack = { nav.back() }, right = { CoinPill(profile.coins, font) }) { mod ->
        Column(mod) {
            Text("Buy weapons & upgrades with coins. Equip them per bay in the garage.",
                fontFamily = font, color = colors.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                gridItems(pool, key = { it.id }) { item ->
                    ShopCard(
                        item = item,
                        owned = profile.owns(item),
                        price = ItemRepository.itemPrice(item),
                        onClick = { nav.go(Routes.ItemShopDetail(item.id)) },
                    )
                }
            }
        }
    }
}

/** Item detail: big render, stats, description, and a Buy (or OWNED) action. */
@Composable
fun ItemShopDetailScreen(nav: OverdriveNav, itemId: String) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    remember { ItemRepository.load(ctx); ProfileRepository.load(ctx); 0 }
    val item = remember(itemId) { ItemRepository.item(itemId) }
    val profile = ProfileRepository.profile

    if (item == null) {
        WireframeScreen(title = "Item", onBack = { nav.back() }, subtitle = "Item not found.")
        return
    }
    val owned = profile.owns(item)
    val price = ItemRepository.itemPrice(item)
    val affordable = profile.coins >= price

    OverdriveScaffold(title = ItemRepository.name(itemId), onBack = { nav.back() }, right = { CoinPill(profile.coins, font) }) { mod ->
        Column(mod.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { WeaponGlyph(itemId, size = 150) }
            Text("LEVEL ${item.level} · ${item.bay.name}", fontFamily = font, color = colors.gold, fontSize = 12.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(8.dp))
            Text(ItemRepository.description(itemId), fontFamily = font, color = colors.textDim, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                if (item.damagePerSec > 0) StatChip("DAMAGE", "${item.damagePerSec.toInt()}")
                if (item.energyUsePerS > 0) StatChip("ENERGY", "${item.energyUsePerS.toInt()}")
                if (item.rechargeTimeS > 0) StatChip("COOLDOWN", "${item.rechargeTimeS}s")
            }
            Spacer(Modifier.height(22.dp))
            if (owned) {
                Text("OWNED", fontFamily = font, color = colors.success, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            } else {
                PrimaryButton(
                    "Buy  ·  $price ◉",
                    { if (ProfileRepository.spendCoins(ctx, price)) ProfileRepository.addItem(ctx, item.id) },
                    Modifier.widthIn(min = 200.dp),
                    accent = if (affordable) ButtonAccent.Gold else ButtonAccent.Outline,
                    enabled = affordable,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Daily specials: a deterministic daily rotation of acquirable weapons at a discount; buy inline. */
@Composable
fun GarageDailySpecialsScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    val font = OverdriveTheme.font
    val colors = OverdriveTheme.colors
    remember { ItemRepository.load(ctx); ProfileRepository.load(ctx); 0 }
    val profile = ProfileRepository.profile
    // Same set all day: seed the pick by the epoch-day so it rotates once per day.
    val specials = remember {
        val day = System.currentTimeMillis() / 86_400_000L
        buyablePool().shuffled(kotlin.random.Random(day)).take(6)
    }

    OverdriveScaffold(title = "Daily Specials", onBack = { nav.back() }, right = { CoinPill(profile.coins, font) }) { mod ->
        Column(mod) {
            Text("Today's deals — 25% off. New picks every day.",
                fontFamily = font, color = colors.gold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                gridItems(specials, key = { it.id }) { item ->
                    val owned = profile.owns(item)
                    val deal = (ItemRepository.itemPrice(item) * 3) / 4   // 25% off
                    ShopCard(
                        item = item, owned = owned, price = deal,
                        onClick = {
                            if (!owned && ProfileRepository.spendCoins(ctx, deal)) ProfileRepository.addItem(ctx, item.id)
                        },
                    )
                }
            }
        }
    }
}

/** A small stat readout (label over value) for the item detail. */
@Composable
private fun StatChip(label: String, value: String) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = font, color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, fontFamily = font, color = colors.textDim, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

/** A shop tile: weapon render, name, level/bay, and price (or an OWNED badge). */
@Composable
private fun ShopCard(item: GameItem, owned: Boolean, price: Int, onClick: () -> Unit) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .clip(shape)
            .background(colors.panel.copy(alpha = 0.7f))
            .border(1.dp, colors.panelBorder, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) { WeaponGlyph(item.id, size = 64) }
        Spacer(Modifier.height(6.dp))
        Text(ItemRepository.name(item.id), fontFamily = font, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text("Lvl ${item.level} · ${item.bay.name}", fontFamily = font, color = colors.textDim, fontSize = 9.sp, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(4.dp))
        if (owned) Text("OWNED", fontFamily = font, color = colors.success, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        else Text("$price ◉", fontFamily = font, color = colors.gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
