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
import dev.overdrive.ui.components.ButtonAccent
import dev.overdrive.ui.components.CoinPill
import dev.overdrive.ui.components.OverdriveBackground
import dev.overdrive.ui.components.PrimaryButton
import dev.overdrive.ui.theme.OverdriveTheme

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
            // Top chrome: profile chip (left) + coin balance (right)
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
                Text("OVERDRIVE", fontFamily = font, color = colors.textPrimary, fontSize = 56.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Text("X  ·  REBUILT", fontFamily = font, color = colors.gold, fontSize = 16.sp, letterSpacing = 6.sp)
                Spacer(Modifier.height(36.dp))

                Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton("Campaign", { nav.go(Routes.CampaignGraph) }, Modifier.fillMaxWidth(), ButtonAccent.Gold)
                    PrimaryButton("Open Play", { nav.go(Routes.OpenPlayGraph) }, Modifier.fillMaxWidth(), ButtonAccent.Blue)
                    PrimaryButton("Garage", { nav.go(Routes.GarageGraph) }, Modifier.fillMaxWidth(), ButtonAccent.Outline)
                    PrimaryButton("Join a Friend", { nav.go(Routes.OpenPlayGraph) }, Modifier.fillMaxWidth(), ButtonAccent.Outline)
                }

                Spacer(Modifier.height(28.dp))
                FlowRow(
                    Modifier.widthIn(max = 520.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HomeLink("Profile") { nav.go(Routes.ProfileGraph) }
                    HomeLink("Store") { nav.go(Routes.StoreGraph) }
                    HomeLink("Coin Shop") { nav.go(Routes.CoinShop) }
                    HomeLink("Tracks") { nav.go(Routes.TracksGraph) }
                    HomeLink("Guide") { nav.go(Routes.Guide) }
                    HomeLink("Settings") { nav.go(Routes.SettingsGraph) }
                }
            }
        }
    }
}

@Composable
private fun HomeLink(label: String, onClick: () -> Unit) {
    Text(
        label.uppercase(),
        fontFamily = OverdriveTheme.font,
        color = OverdriveTheme.colors.textDim,
        fontSize = 13.sp,
        letterSpacing = 1.sp,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
    )
}
