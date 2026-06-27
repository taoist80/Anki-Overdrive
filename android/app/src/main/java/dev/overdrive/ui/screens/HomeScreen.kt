package dev.overdrive.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import dev.overdrive.ui.theme.rememberAsset

/**
 * 4.0.4 main menu (reference/screenshots/ddl404/01_main_menu): the light ANKI/OVERDRIVE wordmark
 * centered over the violet nebula, with three glowing letter-spaced entries — Extras · Single Player ·
 * Multiplayer. Clean: no tagline, no top chrome. Garage lives under Extras, matching the original.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(nav: OverdriveNav) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val bg = rememberAsset("ui/global-background.png")   // authentic 2.6 menu backdrop (cityscape horizon)
    Box(Modifier.fillMaxSize().background(colors.background)) {
        if (bg != null) Image(bg, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        // violet legibility scrim so the wordmark + menu read over the bright 2.6 horizon
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66140A22), Color(0xCC140A22)))))
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val logo = rememberAsset("ui/overdrive_title.png")
            if (logo != null) {
                Image(logo, "ANKI OVERDRIVE", Modifier.fillMaxWidth(0.44f), contentScale = ContentScale.Fit)
            } else {
                Text("ANKI", fontFamily = font, color = colors.rose, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 9.sp)
                Text("OVERDRIVE", fontFamily = font, color = colors.rose, fontSize = 56.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            }
            Spacer(Modifier.height(56.dp))
            FlowRow(
                Modifier.widthIn(max = 680.dp),
                horizontalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MenuEntry("Extras") { nav.go(Routes.Extras) }
                MenuEntry("Single Player") { nav.go(Routes.SinglePlayer) }
                MenuEntry("Multiplayer") { nav.go(Routes.Multiplayer) }
            }
        }
        // tagline kept, subtle, as a footer so the menu stays authentic
        Text(
            "X · REBUILT",
            fontFamily = font, color = colors.gold.copy(alpha = 0.7f), fontSize = 11.sp, letterSpacing = 5.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
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
