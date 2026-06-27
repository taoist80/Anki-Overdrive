package dev.overdrive.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.overdrive.data.ContentRepository
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Overlay
import dev.overdrive.nav.Routes
import dev.overdrive.profile.ProfileRepository
import dev.overdrive.ui.components.ButtonAccent
import dev.overdrive.ui.components.CoinPill
import dev.overdrive.ui.components.OverdrivePanel
import dev.overdrive.ui.components.OverdriveScaffold
import dev.overdrive.ui.components.PrimaryButton
import dev.overdrive.ui.theme.OverdriveTheme
import dev.overdrive.ui.theme.rememberAsset

/**
 * Store catalog. The original Store sold real-money coin packs + virtual "bonus loot" packs; here the
 * layout/copy/art are matched (2.6 loot crates + store strings) and coin packs grant coins on checkout.
 * Real-money goods are non-functional offline — checkout is a local emulation.
 */
data class StoreProduct(
    val id: String, val category: String, val title: String,
    val priceUsd: Double, val coins: Int, val art: String?, val includes: String, val bonus: String? = null,
)

private val STORE_CATALOG = listOf(
    StoreProduct("coin_starter", "coins", "Starter Pack", 0.99, 1_000, null, "1,000 Coins"),
    StoreProduct("coin_racer", "coins", "Racer Pack", 4.99, 6_000, null, "6,000 Coins", bonus = "+500 BONUS"),
    StoreProduct("coin_champion", "coins", "Champion Pack", 9.99, 14_000, null, "14,000 Coins", bonus = "+2,000 BONUS"),
    StoreProduct("coin_legend", "coins", "Legend Pack", 19.99, 30_000, null, "30,000 Coins", bonus = "+6,000 BONUS"),
    StoreProduct("loot_bonus", "loot", "Bonus Loot Pack", 2.99, 0, "ui/loot/Loot_FF_Virtual_Single.webp", "Multiple items!", bonus = "BONUS LOOT"),
    StoreProduct("loot_gold", "loot", "Gold Crate", 4.99, 0, "ui/loot/Loot_Groundshock_Gold.webp", "Rare & Epic items"),
    StoreProduct("loot_silver", "loot", "Silver Crate", 1.99, 0, "ui/loot/Loot_Groundshock_Silver.webp", "Uncommon items"),
)
private val CATALOG_BY_ID = STORE_CATALOG.associateBy { it.id }

/** In-memory store cart (real-money goods don't persist). */
object StoreCart {
    val lines = mutableStateListOf<Pair<String, Int>>()   // productId -> qty
    val count: Int get() = lines.sumOf { it.second }
    val subtotal: Double get() = lines.sumOf { (CATALOG_BY_ID[it.first]?.priceUsd ?: 0.0) * it.second }
    fun add(id: String) {
        val i = lines.indexOfFirst { it.first == id }
        if (i >= 0) lines[i] = id to (lines[i].second + 1) else lines.add(id to 1)
    }
    fun clear() = lines.clear()
}

private fun usd(v: Double) = "$" + "%.2f".format(v)

@Composable
fun StoreHomeScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    remember { ContentRepository.load(ctx); ProfileRepository.load(ctx); 0 }
    val s = ContentRepository.strings
    var tab by remember { mutableStateOf("coins") }
    val products = STORE_CATALOG.filter { it.category == tab }

    OverdriveScaffold(
        title = s.get("ankiButton.ankiStore", "Store"),
        onBack = { nav.back() },
        right = { CoinPill(ProfileRepository.profile.coins) },
    ) { mod ->
        Column(mod.padding(vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StoreTab("COINS", tab == "coins") { tab = "coins" }
                StoreTab(s.get("store.virtualGoods.bonusTitle", "BONUS LOOT"), tab == "loot") { tab = "loot" }
            }
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                products.forEach { ProductCard(it, s.get("store.addToCart", "Add to Cart")) { StoreCart.add(it.id) } }
                Spacer(Modifier.height(8.dp))
            }
            // Review-cart bar (3.4 ReviewCartButton + quantity)
            PrimaryButton(
                if (StoreCart.count > 0) "${s.get("store.reviewCart", "Review Cart")}  ·  ${StoreCart.count}"
                else s.get("store.reviewCart", "Review Cart"),
                onClick = { if (StoreCart.count > 0) nav.go(Routes.StoreCheckoutSummary) },
                modifier = Modifier.fillMaxWidth(),
                accent = ButtonAccent.Gold,
                enabled = StoreCart.count > 0,
            )
        }
    }
}

@Composable
private fun StoreTab(label: String, on: Boolean, onClick: () -> Unit) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier.clip(shape).background(if (on) Color(0x1CFFFFFF) else Color(0x0DFFFFFF))
            .border(1.dp, if (on) colors.blue else colors.panelBorder, shape)
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(label.uppercase(), fontFamily = font, color = if (on) colors.textPrimary else colors.textDim,
            fontSize = 13.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, letterSpacing = 1.sp)
    }
}

@Composable
private fun ProductCard(p: StoreProduct, cta: String, onAdd: () -> Unit) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val art = rememberAsset(p.art)
    OverdrivePanel(Modifier.fillMaxWidth()) { inner ->
        Row(inner, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                if (art != null) Image(art, p.title, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).background(colors.gold))
            }
            Column(Modifier.weight(1f)) {
                Text(p.title.uppercase(), fontFamily = font, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                Text(p.includes, fontFamily = font, color = colors.textDim, fontSize = 12.sp)
                if (p.bonus != null) Text(p.bonus, fontFamily = font, color = colors.gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(usd(p.priceUsd), fontFamily = font, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Box(
                    Modifier.clip(RoundedCornerShape(7.dp)).background(colors.blue).clickable(onClick = onAdd)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) { Text(cta.uppercase(), fontFamily = font, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
            }
        }
    }
}

/** 3.4 sb_StoreCheckoutSummary: cart line items, subtotal, checkout CTAs. */
@Composable
fun StoreCheckoutSummaryScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    remember { ContentRepository.load(ctx); 0 }
    val s = ContentRepository.strings

    OverdriveScaffold(title = s.get("store.reviewCart", "Review Cart"), onBack = { nav.back() }) { mod ->
        Column(mod.verticalScroll(rememberScrollState()).widthIn(max = 520.dp).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (StoreCart.lines.isEmpty()) {
                Text(s.get("store.cart.empty.body", "Your cart is empty! Please add items before checking out."), fontFamily = font, color = colors.textDim, fontSize = 14.sp)
            } else {
                StoreCart.lines.forEach { (id, qty) ->
                    val p = CATALOG_BY_ID[id] ?: return@forEach
                    OverdrivePanel(Modifier.fillMaxWidth()) { inner ->
                        Row(inner, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${p.title.uppercase()}  ×$qty", fontFamily = font, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(p.includes, fontFamily = font, color = colors.textDim, fontSize = 12.sp)
                            }
                            Text(usd(p.priceUsd * qty), fontFamily = font, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    s.get("store.cart.subtotal", "SUBTOTAL: {0}").replace("{0}", usd(StoreCart.subtotal)),
                    fontFamily = font, color = colors.gold, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                PrimaryButton(s.get("store.webCheckout", "Checkout"), { nav.go(Routes.StoreCheckoutOptions) }, Modifier.fillMaxWidth(), ButtonAccent.Gold)
            }
            PrimaryButton(s.get("ankiButton.ankiStore", "Store"), { nav.back() }, Modifier.fillMaxWidth(), ButtonAccent.Outline)
        }
    }
}

/** 3.4 sb_StoreCheckoutOptions: payment methods (local emulation; coin packs grant coins). */
@Composable
fun StoreCheckoutOptionsScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    remember { ContentRepository.load(ctx); ProfileRepository.load(ctx); 0 }
    val s = ContentRepository.strings

    fun confirm() {
        StoreCart.lines.forEach { (id, qty) ->
            val grant = (CATALOG_BY_ID[id]?.coins ?: 0) * qty
            if (grant > 0) ProfileRepository.addCoins(ctx, grant)
        }
        StoreCart.clear()
        nav.showOverlay(Overlay.CelebrationUnlock(s.get("store.virtualGoods.titleCopy", "Thank you!")))
        nav.home()
    }

    OverdriveScaffold(title = "Payment", onBack = { nav.back() }) { mod ->
        Column(mod.verticalScroll(rememberScrollState()).widthIn(max = 460.dp).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                s.get("store.cart.subtotal", "SUBTOTAL: {0}").replace("{0}", usd(StoreCart.subtotal)),
                fontFamily = font, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            )
            Text("Local emulation — no real charges.", fontFamily = font, color = colors.textDim, fontSize = 12.sp)
            PrimaryButton(s.get("store.button.webCheckout", "Checkout on Web"), { confirm() }, Modifier.fillMaxWidth(), ButtonAccent.Gold)
            PrimaryButton("Apple Pay", { confirm() }, Modifier.fillMaxWidth(), ButtonAccent.Blue)
            PrimaryButton(s.get("store.emailCart", "Email Your Cart"), { confirm() }, Modifier.fillMaxWidth(), ButtonAccent.Outline)
        }
    }
}

/** Coin shop — buy in-game coins (grants immediately; persists + syncs). */
@Composable
fun CoinShopScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    remember { ContentRepository.load(ctx); ProfileRepository.load(ctx); 0 }
    val s = ContentRepository.strings
    val packs = STORE_CATALOG.filter { it.category == "coins" }

    OverdriveScaffold(
        title = "Coin Shop",
        onBack = { nav.back() },
        right = { CoinPill(ProfileRepository.profile.coins) },
    ) { mod ->
        Column(mod.verticalScroll(rememberScrollState()).widthIn(max = 520.dp).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            packs.forEach { p ->
                ProductCard(p, "Buy") { ProfileRepository.addCoins(ctx, p.coins) }
            }
        }
    }
}
