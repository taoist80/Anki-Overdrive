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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Routes
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

/** Extras hub: the secondary destinations, as a wrapping grid of tiles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtrasScreen(nav: OverdriveNav) {
    OverdriveScaffold(title = "Extras", onBack = { nav.back() }) { mod ->
        FlowRow(
            mod.padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExtrasTile("Store", "Browse & buy") { nav.go(Routes.StoreGraph) }
            ExtrasTile("Profile", "Driver & medals") { nav.go(Routes.ProfileGraph) }
            ExtrasTile("Coin Shop", "Top up coins") { nav.go(Routes.CoinShop) }
            ExtrasTile("Guide", "How to play") { nav.go(Routes.Guide) }
            ExtrasTile("Settings", "Audio, haptics, account") { nav.go(Routes.SettingsGraph) }
        }
    }
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
