package dev.overdrive.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.overdrive.nav.Overlay
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Routes
import dev.overdrive.ui.components.ButtonAccent
import dev.overdrive.ui.components.ModeTile
import dev.overdrive.ui.components.NavAction
import dev.overdrive.ui.components.OverdriveBackground
import dev.overdrive.ui.components.OverdriveScaffold
import dev.overdrive.ui.components.PrimaryButton
import dev.overdrive.ui.components.WireframeScreen
import dev.overdrive.ui.theme.OverdriveTheme

private data class GameMode(val name: String, val art: String, val blurb: String)

private val GAME_MODES = listOf(
    GameMode("Race", "ui/ui_selectMode_openPlay.png", "Classic lap race — first across the line wins."),
    GameMode("Battle", "ui/ui_selectMode_practice.png", "Blast opponents with weapons; last car standing."),
    GameMode("King of the Hill", "ui/ui_selectMode_tournament.png", "Hold the lead zone to bank points."),
    GameMode("Elimination", "ui/ui_selectMode_openPlay.png", "Last-place car is knocked out each round."),
    GameMode("Time Trial", "ui/ui_selectMode_practice.png", "Set the fastest solo lap."),
    GameMode("Takeover", "ui/ui_selectMode_tournament.png", "Capture and defend track segments."),
)

@Composable
fun GameModeSelectScreen(nav: OverdriveNav) {
    OverdriveScaffold(title = "Select Mode", onBack = { nav.back() }) { mod ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(180.dp),
            modifier = mod,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            items(GAME_MODES) { m ->
                ModeTile(
                    title = m.name,
                    image = m.art,
                    subtitle = m.blurb,
                    onClick = { nav.go(Routes.GameModeDetail(m.name)) },
                )
            }
        }
    }
}

@Composable
fun GameModeDetailScreen(nav: OverdriveNav, mode: String) {
    val blurb = GAME_MODES.firstOrNull { it.name == mode }?.blurb ?: "Game mode details."
    WireframeScreen(
        title = mode,
        onBack = { nav.back() },
        subtitle = "$blurb\n\nConfigure laps, teams, and mutators, then continue to match setup.",
        actions = listOf(
            NavAction("Mutators", { nav.showOverlay(Overlay.MutatorPicker) }, ButtonAccent.Outline),
            NavAction("Continue", { nav.go(Routes.MatchSetup(mode = mode)) }, ButtonAccent.Blue),
        ),
    )
}

@Composable
fun PlayerSelectScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Players",
    onBack = { nav.back() },
    subtitle = "Assign player slots / teams (up to 4). Lobby logic wires in Phase 2.",
    actions = listOf(NavAction("Continue", { nav.go(Routes.VehicleSelect) }, ButtonAccent.Blue)),
)

@Composable
fun VehicleSelectScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Assign Vehicles",
    onBack = { nav.back() },
    subtitle = "Discover cars over BLE and assign one to each player slot. Wires to CarManager in Phase 2.",
    actions = listOf(NavAction("To Match Setup", { nav.go(Routes.MatchSetup()) }, ButtonAccent.Blue)),
)

@Composable
fun MatchSetupScreen(nav: OverdriveNav, mode: String, campaignMissionId: String) {
    WireframeScreen(
        title = "Match Setup",
        onBack = { nav.back() },
        subtitle = buildString {
            append("Vehicle + player slot assignment, BLE discovery, and options.")
            if (mode.isNotBlank()) append("\nMode: $mode")
            if (campaignMissionId.isNotBlank()) append("\nCampaign mission: $campaignMissionId")
        },
        actions = listOf(
            NavAction("Players", { nav.go(Routes.PlayerSelect) }, ButtonAccent.Outline),
            NavAction("Scan Track", { nav.go(Routes.TrackScan) }, ButtonAccent.Blue),
        ),
    )
}

@Composable
fun TrackScanScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Scan Track",
    onBack = { nav.back() },
    subtitle = "Drive a car around the track once to map its segments. The mapped layout renders here. " +
        "Track model wires in Phase 2.",
    actions = listOf(NavAction("Ready — Countdown", { nav.go(Routes.Countdown) }, ButtonAccent.Gold)),
)

@Composable
fun CountdownScreen(nav: OverdriveNav) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    OverdriveBackground(heroImage = null) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("GET READY", fontFamily = font, color = colors.textDim, fontSize = 18.sp, letterSpacing = 4.sp)
            Spacer(Modifier.height(16.dp))
            Text("3", fontFamily = font, color = colors.gold, fontSize = 140.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Go!", { nav.go(Routes.InRaceHud) }, accent = ButtonAccent.Gold)
        }
    }
}

@Composable
fun InRaceHudScreen(nav: OverdriveNav) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    OverdriveBackground(heroImage = null) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            // Top HUD row: position / lap / time
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HudStat("POS", "1st")
                HudStat("LAP", "1 / 3")
                HudStat("TIME", "0:00.0")
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(colors.danger.copy(alpha = 0.85f))
                        .clickable { nav.go(Routes.GameOver) }.padding(horizontal = 16.dp, vertical = 10.dp),
                ) { Text("END", color = androidx.compose.ui.graphics.Color.White, fontFamily = font, fontWeight = FontWeight.Bold) }
            }

            // Center: track / minimap placeholder
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(vertical = 16.dp)
                    .clip(RoundedCornerShape(10.dp)).border(1.dp, colors.panelBorder, RoundedCornerShape(10.dp))
                    .background(colors.panel.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("TRACK MINIMAP + LIVE CAR POSITIONS\n(0x27 telemetry → RaceEngine, Phase 2)",
                    fontFamily = font, color = colors.textDim, fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }

            // Bottom controls: lane left/right, throttle, weapon
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ControlButton("◄", colors.blue, Modifier.weight(1f))
                Column(Modifier.weight(2f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("THROTTLE", fontFamily = font, color = colors.textDim, fontSize = 11.sp, letterSpacing = 1.sp)
                    Box(Modifier.fillMaxWidth().height(48.dp).padding(top = 4.dp).clip(RoundedCornerShape(24.dp)).background(colors.barEmpty)) {
                        Box(Modifier.fillMaxWidth(0.7f).fillMaxSize().clip(RoundedCornerShape(24.dp)).background(colors.gold))
                    }
                }
                ControlButton("►", colors.blue, Modifier.weight(1f))
                WeaponButton()
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun HudStat(label: String, value: String) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontFamily = font, color = colors.textDim, fontSize = 11.sp, letterSpacing = 1.sp)
        Text(value, fontFamily = font, color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ControlButton(glyph: String, tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Box(
        modifier.height(56.dp).clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = androidx.compose.ui.graphics.Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun WeaponButton() {
    val colors = OverdriveTheme.colors
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(colors.orange),
        contentAlignment = Alignment.Center,
    ) { Text("⚡", fontSize = 24.sp) }
}

@Composable
fun GameOverScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Game Over",
    onBack = { nav.back() },
    heroImage = null,
    subtitle = "Finish flourish before results.",
    actions = listOf(NavAction("See Results", { nav.go(Routes.RaceResults()) }, ButtonAccent.Gold)),
)

@Composable
fun RaceResultsScreen(nav: OverdriveNav, campaignMissionId: String) {
    val isCampaign = campaignMissionId.isNotBlank()
    WireframeScreen(
        title = if (isCampaign) "Mission Complete" else "Results",
        onBack = { nav.back() },
        heroImage = null,
        subtitle = if (isCampaign)
            "Stars earned, XP, and rewards for mission $campaignMissionId. Loot reveal plays over this screen."
        else
            "Final leaderboard, lap times, XP gained, and loot earned.",
        actions = buildList {
            add(NavAction("Open Loot", { nav.showOverlay(Overlay.LootReveal) }, ButtonAccent.Gold))
            if (isCampaign) add(NavAction("Next Mission", { nav.go(Routes.CampaignGraph) }, ButtonAccent.Blue))
            else add(NavAction("Rematch", { nav.go(Routes.MatchSetup()) }, ButtonAccent.Outline))
            add(NavAction("Home", { nav.home() }, ButtonAccent.Outline))
        },
    )
}
