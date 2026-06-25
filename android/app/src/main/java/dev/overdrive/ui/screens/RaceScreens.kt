package dev.overdrive.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.overdrive.AnkiCarManagerHolder
import dev.overdrive.CarState
import dev.overdrive.data.ContentRepository
import dev.overdrive.game.race.RaceEngineHolder
import dev.overdrive.nav.Overlay
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Routes
import dev.overdrive.ui.components.ButtonAccent
import dev.overdrive.ui.components.ModeTile
import dev.overdrive.ui.components.NavAction
import dev.overdrive.ui.components.OverdriveBackground
import dev.overdrive.ui.components.OverdrivePanel
import dev.overdrive.ui.components.OverdriveScaffold
import dev.overdrive.ui.components.PrimaryButton
import dev.overdrive.ui.components.WireframeScreen
import dev.overdrive.ui.theme.OverdriveTheme

@Composable
fun GameModeSelectScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    remember { ContentRepository.load(ctx); 0 }
    val modes = ContentRepository.modes
    OverdriveScaffold(title = "Select Mode", onBack = { nav.back() }) { mod ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(180.dp),
            modifier = mod,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            items(modes) { m ->
                ModeTile(
                    title = m.title,
                    image = m.imageAsset,
                    subtitle = m.description?.take(90)?.let { if (it.length == 90) "$it…" else it },
                    accent = Color(m.tintArgb),
                    onClick = { nav.go(Routes.GameModeDetail(m.internalName)) },
                )
            }
        }
    }
}

@Composable
fun GameModeDetailScreen(nav: OverdriveNav, mode: String) {
    val ctx = LocalContext.current
    remember { ContentRepository.load(ctx); 0 }
    val gm = ContentRepository.modeByInternalName(mode)
    WireframeScreen(
        title = gm?.title ?: mode,
        onBack = { nav.back() },
        subtitle = (gm?.description ?: "Game mode details.") +
            "\n\nConfigure laps, teams, and mutators, then continue to match setup.",
        actions = listOf(
            NavAction("Mutators", { nav.showOverlay(Overlay.MutatorPicker) }, ButtonAccent.Outline),
            NavAction("Continue", { nav.go(Routes.MatchSetup(mode = gm?.title ?: mode)) }, ButtonAccent.Blue),
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
    subtitle = "Vehicle discovery + slot assignment happens in Match Setup.",
    actions = listOf(NavAction("To Match Setup", { nav.go(Routes.MatchSetup()) }, ButtonAccent.Blue)),
)

/** Real BLE: scan, connect cars (up to 4), then arm the race engine. */
@Composable
fun MatchSetupScreen(nav: OverdriveNav, mode: String, campaignMissionId: String) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val mgr = remember { AnkiCarManagerHolder.require() }
    val engine = remember { RaceEngineHolder.engine }

    OverdriveScaffold(title = "Match Setup", onBack = { nav.back() }) { mod ->
        Column(mod, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                buildString {
                    append("Power on your cars and place them on the track. Scan, then tap a car to connect (up to ${mgr.maxCars}). The first connected car is yours.")
                    if (mode.isNotBlank()) append("\nMode: $mode")
                    if (campaignMissionId.isNotBlank()) append("   ·   Mission: $campaignMissionId")
                },
                fontFamily = font, color = colors.textDim, fontSize = 13.sp,
            )

            // Connected cars
            val connected = mgr.cars.filter { it.state != CarState.Disconnected }
            if (connected.isNotEmpty()) {
                Text("CONNECTED", fontFamily = font, color = colors.gold, fontSize = 12.sp, letterSpacing = 2.sp)
                connected.forEachIndexed { i, c ->
                    OverdrivePanel(Modifier.fillMaxWidth()) { inner ->
                        Row(inner.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text((if (i == 0) "P1 · " else "AI · ") + c.name, fontFamily = font, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("${c.state}", fontFamily = font, color = colors.textDim, fontSize = 11.sp)
                            }
                            val dot = if (c.state == CarState.Connected) colors.success else colors.gold
                            Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
                        }
                    }
                }
            }

            // Scan control + found list
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(if (mgr.scanning) "Scanning…" else "Scan", { mgr.startScan() }, accent = ButtonAccent.Blue)
                if (mgr.cars.isNotEmpty()) PrimaryButton("Disconnect All", { mgr.disconnectAll() }, accent = ButtonAccent.Outline)
            }
            mgr.found.forEach { d ->
                OverdrivePanel(Modifier.fillMaxWidth().clickable { mgr.connect(d) }) { inner ->
                    Row(inner.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(d.name, fontFamily = font, color = colors.textPrimary, fontSize = 15.sp)
                            Text(d.address, fontFamily = font, color = colors.textDim, fontSize = 11.sp)
                        }
                        Text("${d.rssi} dBm  ·  TAP TO CONNECT", fontFamily = font, color = colors.blue, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            PrimaryButton(
                "Scan Track",
                {
                    engine.arm(mode)
                    nav.go(Routes.TrackScan)
                },
                Modifier.fillMaxWidth(),
                accent = ButtonAccent.Gold,
                enabled = connected.isNotEmpty(),
            )
        }
    }
}

@Composable
fun TrackScanScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Scan Track",
    onBack = { nav.back() },
    subtitle = "Drive a car around the track once to map its segments. The mapped layout renders here. " +
        "Full track model wires in Phase 3.",
    actions = listOf(NavAction("Ready — Countdown", { nav.go(Routes.Countdown) }, ButtonAccent.Gold)),
)

@Composable
fun CountdownScreen(nav: OverdriveNav) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val engine = remember { RaceEngineHolder.engine }
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
            PrimaryButton("Go!", { engine.start(); nav.go(Routes.InRaceHud) }, accent = ButtonAccent.Gold)
        }
    }
}

@Composable
fun InRaceHudScreen(nav: OverdriveNav) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val engine = remember { RaceEngineHolder.engine }
    val st = engine.state
    val player = st.cars.firstOrNull { it.isPlayer }
    val rank = st.standings.indexOfFirst { it.isPlayer }.let { if (it >= 0) it + 1 else 1 }
    var throttle by remember { mutableFloatStateOf(0.6f) }

    OverdriveBackground(heroImage = null) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HudStat("POS", ordinal(rank))
                HudStat("SPEED", "${player?.speedMmPerSec ?: 0}")
                HudStat("SEG", "${player?.transitions ?: 0}")
                HudStat("TIME", formatTime(st.elapsedMs))
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(colors.danger.copy(alpha = 0.85f))
                        .clickable { engine.stop(); nav.go(Routes.GameOver) }.padding(horizontal = 16.dp, vertical = 10.dp),
                ) { Text("END", color = Color.White, fontFamily = font, fontWeight = FontWeight.Bold) }
            }

            // Live telemetry for all cars (from 0x27)
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(vertical = 16.dp)
                    .clip(RoundedCornerShape(10.dp)).border(1.dp, colors.panelBorder, RoundedCornerShape(10.dp))
                    .background(colors.panel.copy(alpha = 0.4f)).padding(14.dp),
            ) {
                if (st.cars.isEmpty()) {
                    Text("No cars armed — connect cars in Match Setup.", fontFamily = font, color = colors.textDim, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("LIVE TELEMETRY (0x27)", fontFamily = font, color = colors.textDim, fontSize = 11.sp, letterSpacing = 2.sp)
                        st.standings.forEachIndexed { i, c ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${i + 1}. ${if (c.isPlayer) "▸ " else ""}${c.name}", fontFamily = font, color = if (c.isPlayer) colors.gold else colors.textPrimary, fontSize = 14.sp)
                                Text("seg ${c.transitions}  ·  piece ${c.roadPieceId}  ·  ${c.speedMmPerSec} mm/s", fontFamily = font, color = colors.textDim, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Controls
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ControlButton("◄", colors.blue, Modifier.weight(1f)) { engine.nudgeLane(-1) }
                Column(Modifier.weight(2f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("THROTTLE", fontFamily = font, color = colors.textDim, fontSize = 11.sp, letterSpacing = 1.sp)
                    Slider(
                        value = throttle,
                        onValueChange = { throttle = it; engine.setThrottle(it) },
                        colors = SliderDefaults.colors(thumbColor = colors.gold, activeTrackColor = colors.gold),
                    )
                }
                ControlButton("►", colors.blue, Modifier.weight(1f)) { engine.nudgeLane(1) }
                WeaponButton { engine.fireWeapon() }
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
private fun ControlButton(glyph: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(56.dp).clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.85f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun WeaponButton(onClick: () -> Unit) {
    val colors = OverdriveTheme.colors
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(colors.orange).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("⚡", fontSize = 24.sp) }
}

@Composable
fun GameOverScreen(nav: OverdriveNav) {
    val engine = remember { RaceEngineHolder.engine }
    remember { engine.stop(); 0 }   // ensure cars are stopped
    WireframeScreen(
        title = "Game Over",
        onBack = { nav.back() },
        heroImage = null,
        subtitle = "Finish flourish before results.",
        actions = listOf(NavAction("See Results", { nav.go(Routes.RaceResults()) }, ButtonAccent.Gold)),
    )
}

@Composable
fun RaceResultsScreen(nav: OverdriveNav, campaignMissionId: String) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val engine = remember { RaceEngineHolder.engine }
    val standings = engine.state.standings
    val isCampaign = campaignMissionId.isNotBlank()

    OverdriveScaffold(title = if (isCampaign) "Mission Complete" else "Results", onBack = { nav.back() }, heroImage = null) { mod ->
        Column(mod, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (standings.isEmpty()) {
                Text("No race data.", fontFamily = font, color = colors.textDim)
            } else {
                Text("FINISH ORDER", fontFamily = font, color = colors.gold, fontSize = 12.sp, letterSpacing = 2.sp)
                standings.forEachIndexed { i, c ->
                    OverdrivePanel(Modifier.fillMaxWidth()) { inner ->
                        Row(inner.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${ordinal(i + 1)}   ${if (c.isPlayer) "▸ " else ""}${c.name}", fontFamily = font, color = if (c.isPlayer) colors.gold else colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("${c.transitions} segments", fontFamily = font, color = colors.textDim, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Open Loot", { nav.showOverlay(Overlay.LootReveal) }, Modifier.weight(1f), ButtonAccent.Gold)
                if (isCampaign) PrimaryButton("Next Mission", { nav.go(Routes.CampaignGraph) }, Modifier.weight(1f), ButtonAccent.Blue)
                else PrimaryButton("Rematch", { nav.go(Routes.MatchSetup()) }, Modifier.weight(1f), ButtonAccent.Outline)
                PrimaryButton("Home", { nav.home() }, Modifier.weight(1f), ButtonAccent.Outline)
            }
        }
    }
}

private fun ordinal(n: Int): String = when (n) {
    1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"; else -> "${n}th"
}

private fun formatTime(ms: Long): String {
    val totalTenths = ms / 100
    val m = totalTenths / 600
    val s = (totalTenths / 10) % 60
    val t = totalTenths % 10
    return "%d:%02d.%d".format(m, s, t)
}
