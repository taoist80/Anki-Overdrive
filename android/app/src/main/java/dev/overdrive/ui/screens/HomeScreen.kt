package dev.overdrive.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Routes
import dev.overdrive.profile.ProfileRepository
import dev.overdrive.ui.components.CoinPill
import dev.overdrive.ui.components.OverdriveBackground
import dev.overdrive.ui.theme.OverdriveTheme

/**
 * 4.0.4 main menu: rose ANKI/OVERDRIVE wordmark over the violet nebula, with the four entries as
 * glowing letter-spaced text — Extras · Single Player · Multiplayer · Garage (Garage kept as its own
 * top-level entry per the restyle decision). Top chrome keeps the driver name + coin balance.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    remember { ProfileRepository.load(ctx); 0 }
    val profile = ProfileRepository.profile
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    OverdriveBackground {
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Column(Modifier.fillMaxSize().padding(top = top)) {
            // Top chrome: driver name (left) + coin balance (right)
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text(
                    profile.driverName.uppercase(),
                    fontFamily = font, color = colors.textDim, fontSize = 14.sp, letterSpacing = 1.sp,
                    modifier = Modifier.align(Alignment.CenterStart).clickable { nav.go(Routes.ProfileGraph) },
                )
                Box(Modifier.align(Alignment.CenterEnd).clickable { nav.go(Routes.CoinShop) }) {
                    CoinPill(amount = profile.coins, font = font)
                }
            }

            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("ANKI", fontFamily = font, color = colors.rose, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 9.sp)
                Text("OVERDRIVE", fontFamily = font, color = colors.rose, fontSize = 56.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Text("X  ·  REBUILT", fontFamily = font, color = colors.gold, fontSize = 16.sp, letterSpacing = 6.sp)
                Spacer(Modifier.height(48.dp))

                FlowRow(
                    Modifier.widthIn(max = 680.dp),
                    horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    MenuEntry("Extras") { nav.go(Routes.Extras) }
                    MenuEntry("Single Player") { nav.go(Routes.SinglePlayer) }
                    MenuEntry("Multiplayer") { nav.go(Routes.Multiplayer) }
                    MenuEntry("Garage") { nav.go(Routes.GarageGraph) }
                }
            }
        }
    }
}

/** A main-menu entry: plain glowing uppercase text, the 4.0.4 menu vernacular (not a button). */
@Composable
private fun MenuEntry(label: String, onClick: () -> Unit) {
    Text(
        label.uppercase(),
        fontFamily = OverdriveTheme.font,
        color = OverdriveTheme.colors.textPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 3.sp,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
    )
}
