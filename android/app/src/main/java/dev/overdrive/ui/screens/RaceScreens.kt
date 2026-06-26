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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import dev.overdrive.AnkiCarManagerHolder
import dev.overdrive.CarCategory
import dev.overdrive.CarState
import dev.overdrive.GameData
import dev.overdrive.data.ContentRepository
import dev.overdrive.data.ItemRepository
import dev.overdrive.ui.theme.rememberAsset
import dev.overdrive.game.MetaGame
import dev.overdrive.game.campaign.CampaignEngine
import dev.overdrive.game.race.RaceEngineHolder
import dev.overdrive.game.race.RoadPieces
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
import dev.overdrive.ui.components.StarRow
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

/**
 * Match Setup (4.0.4 full-roster car select): every car shown under Supercars / Supertrucks / Drive
 * tabs. Connected cars are selectable (tap to set P1); nearby cars connect on tap; the rest read
 * "POWER ON". Connection state is matched to the live BLE manager by `modelId`. Below the roster:
 * the laps-to-finish stepper + the Scan Track CTA (enabled once a car is connected).
 */
@Composable
fun MatchSetupScreen(nav: OverdriveNav, mode: String, campaignMissionId: String) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val mgr = remember { AnkiCarManagerHolder.require() }
    val engine = remember { RaceEngineHolder.engine }
    remember { GameData.load(ctx); 0 }
    var playerAddr by remember { mutableStateOf<String?>(null) }
    var lapCount by remember { mutableStateOf(3) }
    var tab by remember { mutableStateOf(CarCategory.SUPERCARS) }

    val connected = mgr.cars.filter { it.state != CarState.Disconnected }
    val effectivePlayer = playerAddr ?: connected.firstOrNull()?.address
    val roster = GameData.cars.filter { it.category == tab }
    // Connected cars whose model didn't resolve to a catalog car — kept selectable so none are lost.
    val unknownConnected = connected.filter { GameData.byModelId(it.modelId) == null }

    OverdriveScaffold(
        title = "Match Setup", onBack = { nav.back() },
        right = { Text(if (mode.isNotBlank()) mode.uppercase() else "RACE", fontFamily = font, color = colors.gold, fontSize = 12.sp, letterSpacing = 1.5.sp) },
    ) { mod ->
        Column(mod, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(if (mgr.scanning) "Scanning…" else "Scan", { mgr.startScan() }, accent = ButtonAccent.Blue)
                if (mgr.cars.isNotEmpty()) PrimaryButton("Disconnect", { mgr.disconnectAll() }, accent = ButtonAccent.Outline)
                Spacer(Modifier.weight(1f))
                Text("${connected.size}/${mgr.maxCars} connected", fontFamily = font, color = colors.textDim, fontSize = 12.sp)
            }
            Text(
                "Tap a connected car to make it yours (P1). Tap a nearby car to connect it." +
                    if (campaignMissionId.isNotBlank()) "   ·   Mission: $campaignMissionId" else "",
                fontFamily = font, color = colors.textDim, fontSize = 12.sp,
            )

            // Category tabs
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CarCategory.values().forEach { c ->
                    CategoryTab(c.label, c == tab) { tab = c }
                }
            }

            // The roster grid takes the remaining height; the stepper + CTA pin to the bottom.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(roster, key = { it.id }) { car ->
                    val conn = mgr.cars.firstOrNull { it.modelId == car.id && it.state != CarState.Disconnected }
                    val found = mgr.found.firstOrNull { it.modelId == car.id }
                    val connecting = conn != null && conn.state != CarState.Connected
                    val (slot, detail) = when {
                        conn != null && conn.address == effectivePlayer -> Slot.PLAYER to (if (connecting) "P1 · CONNECTING" else "P1 · YOU")
                        conn != null -> Slot.CONNECTED to (if (connecting) "CONNECTING…" else "TAP TO SET P1")
                        found != null -> Slot.NEARBY to "TAP TO CONNECT · ${found.rssi}dBm"
                        else -> Slot.OFFLINE to "POWER ON"
                    }
                    RosterCard(car.name, car.id, slot, detail) {
                        when {
                            conn != null -> playerAddr = conn.address
                            found != null -> mgr.connect(found)
                            else -> {}
                        }
                    }
                }
                items(unknownConnected, key = { it.address }) { c ->
                    val slot = if (c.address == effectivePlayer) Slot.PLAYER else Slot.CONNECTED
                    RosterCard(c.name.ifBlank { "Unknown Car" }, c.modelId, slot,
                        if (slot == Slot.PLAYER) "P1 · YOU" else "TAP TO SET P1") { playerAddr = c.address }
                }
            }

            // Laps-to-finish stepper (mirrors 4.0.4's LAP COUNT)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("LAPS TO FINISH", fontFamily = font, color = colors.textDim, fontSize = 12.sp, letterSpacing = 1.sp)
                ControlButton("–", colors.panelBorder, Modifier.size(40.dp)) { if (lapCount > 1) lapCount-- }
                Text("$lapCount", fontFamily = font, color = colors.gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                ControlButton("+", colors.panelBorder, Modifier.size(40.dp)) { if (lapCount < 20) lapCount++ }
            }
            PrimaryButton(
                "Scan Track",
                {
                    engine.setPlayer(effectivePlayer)
                    engine.setLapTarget(lapCount)
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

/** Which roster slot a car occupies — drives its card's accent, dot, and tap behaviour. */
private enum class Slot { PLAYER, CONNECTED, NEARBY, OFFLINE }

@Composable
private fun CategoryTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.clip(shape)
            .background(if (selected) Color(0x22FFFFFF) else Color(0x0DFFFFFF))
            .border(1.dp, if (selected) colors.gold.copy(alpha = 0.7f) else colors.panelBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label.uppercase(), fontFamily = font, color = if (selected) colors.textPrimary else colors.textDim,
            fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

/** One car in the roster grid. [modelId] resolves the thumbnail; [slot] sets the visual state. */
@Composable
private fun RosterCard(name: String, modelId: Int, slot: Slot, detail: String, onTap: () -> Unit) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val shape = RoundedCornerShape(10.dp)
    val accent = when (slot) {
        Slot.PLAYER -> colors.gold
        Slot.CONNECTED -> colors.success
        Slot.NEARBY -> colors.blue
        Slot.OFFLINE -> colors.panelBorder
    }
    val dim = slot == Slot.OFFLINE
    Column(
        Modifier.clip(shape)
            .background(colors.panel.copy(alpha = if (dim) 0.35f else 0.75f))
            .border(if (slot == Slot.PLAYER) 2.dp else 1.dp, accent.copy(alpha = if (dim) 0.4f else 0.8f), shape)
            .alpha(if (dim) 0.6f else 1f)
            .clickable(enabled = slot != Slot.OFFLINE, onClick = onTap)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CarThumb(modelId) }
            if (slot == Slot.PLAYER || slot == Slot.CONNECTED)
                Box(Modifier.size(9.dp).clip(CircleShape).background(colors.success))
            else if (slot == Slot.NEARBY)
                Box(Modifier.size(9.dp).clip(CircleShape).background(colors.blue))
        }
        Spacer(Modifier.height(4.dp))
        Text(name.uppercase(), fontFamily = font, color = if (dim) colors.textDim else colors.textPrimary,
            fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(detail, fontFamily = font, color = accent, fontSize = 9.sp, letterSpacing = 0.5.sp, maxLines = 1)
    }
}

/**
 * Real track-scan lap: drives every car slowly around the track once so the firmware maps/localizes
 * it before racing (skipping this is what made cars delocalize, spin and reverse). Shows per-car
 * progress; advances to the countdown when the track is mapped.
 */
@Composable
fun TrackScanScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val engine = remember { RaceEngineHolder.engine }
    remember { RoadPieces.load(ctx); 0 }     // curve map must be ready before driving
    LaunchedEffect(Unit) { engine.startScan() }
    val st = engine.state

    OverdriveBackground(heroImage = null) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(if (st.scanComplete) "TRACK MAPPED" else "SCANNING TRACK…",
                fontFamily = font, color = colors.gold, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            Text(if (st.scanComplete) "Every car has mapped a full lap."
                 else "Cars are driving a slow lap to map the track — keep them on the track.",
                fontFamily = font, color = colors.textDim, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            st.cars.forEach { c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${if (c.isPlayer) "▸ " else ""}${carName(c.modelId, c.name)}",
                        fontFamily = font, color = if (c.isPlayer) colors.gold else colors.textPrimary, fontSize = 15.sp)
                    Text(when {
                        c.laps >= 1 -> "mapped ✓"
                        c.offTrack -> "off track — replace it"
                        c.roadPieceId < 0 -> "place on track"
                        else -> "${c.transitions} segments…"
                    }, fontFamily = font, color = if (c.offTrack || c.roadPieceId < 0) colors.danger else colors.textDim, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
            if (st.scanComplete) {
                PrimaryButton("Continue — Countdown", { nav.go(Routes.Countdown) }, accent = ButtonAccent.Gold)
            } else {
                PrimaryButton("Skip scan → Countdown", { engine.stop(); nav.go(Routes.Countdown) }, accent = ButtonAccent.Outline)
            }
        }
    }
}

@Composable
fun CountdownScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val engine = remember { RaceEngineHolder.engine }
    remember { RoadPieces.load(ctx); 0 }   // ensure curve map is ready before driving

    // 4.0.4 behaviour: an automatic 3·2·1·GO countdown — the race starts on GO with no button press.
    // n: 3,2,1 then 0 = GO. engine.start() fires on GO, then we cross-fade into the HUD.
    var n by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) {
        for (i in 3 downTo 1) { n = i; delay(900) }
        n = 0                       // GO
        engine.start()
        delay(750)
        nav.go(Routes.InRaceHud)
    }
    // Each tick pops in (scale + fade), matching the original countdown punch.
    val pop = remember { Animatable(1.6f) }
    LaunchedEffect(n) { pop.snapTo(1.55f); pop.animateTo(1f, tween(380)) }

    val isGo = n == 0
    OverdriveBackground(heroImage = null) {
        Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (isGo) "" else "GET READY",
                    fontFamily = font, color = colors.textDim, fontSize = 18.sp, letterSpacing = 4.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (isGo) "GO!" else "$n",
                    fontFamily = font, color = if (isGo) colors.success else colors.gold,
                    fontSize = 150.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.graphicsLayer {
                        scaleX = pop.value; scaleY = pop.value
                        alpha = (2f - pop.value).coerceIn(0f, 1f)
                    },
                )
            }
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
    var throttle by remember { mutableFloatStateOf(0.55f) }  // ~495 mm/s of 900 max — safe-cornering default

    // Race over (a car reached the lap target) -> standings/loot.
    LaunchedEffect(st.finished) { if (st.finished) nav.go(Routes.RaceResults()) }

    // In-race view rebuilt 1:1 from 4.0.4's controller.scn: dark road ground + faint controller_overlay,
    // then three columns — vertical throttle (left) · car logo + position (center) · weapon buttons (right) —
    // with a full-width health bar across the top. Works for ALL modes: weapons + health show only in
    // combat modes (playerHud != null); Race / Time Trial get throttle + center + a "no weapons" note.
    val playerCar = player?.let { GameData.byModelId(it.modelId) }
    val logo = rememberAsset(playerCar?.logoAsset)
    val overlay = rememberAsset("ui/controller_overlay.webp")
    val hud = st.playerHud

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF16213A), Color(0xFF0A0D13))))) {
        if (overlay != null) Image(overlay, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.10f)

        // TOP: full-width Health bar (combat modes only) — the 4.0.4 Health TextureProgressBar.
        if (hud != null) {
            Box(Modifier.fillMaxWidth().height(10.dp).align(Alignment.TopCenter).background(colors.barEmpty)) {
                Box(Modifier.fillMaxWidth((hud.health / 100f).coerceIn(0f, 1f)).fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(colors.success, Color(0xFF9BE8B0)))))
            }
        }

        Row(Modifier.fillMaxSize().padding(top = if (hud != null) 20.dp else 10.dp, bottom = 14.dp).padding(horizontal = 10.dp)) {
            // LEFT ~38%: vertical throttle (drag to set speed)
            Box(Modifier.weight(0.38f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                VerticalThrottle(throttle, Modifier.fillMaxHeight(0.9f).width(92.dp)) { throttle = it; engine.setThrottle(it) }
            }
            // CENTER ~24%: position · car logo · lap/speed · lanes · END
            Column(Modifier.weight(0.24f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(ordinal(rank), fontFamily = font, color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                if (logo != null) {
                    Image(logo, player?.name, Modifier.fillMaxWidth(0.92f).heightIn(max = 46.dp).padding(vertical = 6.dp), contentScale = ContentScale.Fit)
                } else if (player != null) {
                    Text(carName(player.modelId, player.name).uppercase(), fontFamily = font, color = colors.blue, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Text("LAP ${player?.laps ?: 0}${if (st.lapTarget > 0) " / ${st.lapTarget}" else ""}  ·  ${player?.speedMmPerSec ?: 0} mm/s${if (player?.onCurve == true) " ↘" else ""}",
                    fontFamily = font, color = colors.textDim, fontSize = 12.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ControlButton("◄", colors.blue, Modifier.size(52.dp)) { engine.nudgeLane(-1) }
                    ControlButton("►", colors.blue, Modifier.size(52.dp)) { engine.nudgeLane(1) }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(colors.danger.copy(alpha = 0.85f))
                        .clickable { engine.stop(); nav.go(Routes.GameOver) }.padding(horizontal = 18.dp, vertical = 9.dp),
                ) { Text("END", color = Color.White, fontFamily = font, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                if (hud?.disabled == true) {
                    Spacer(Modifier.height(8.dp))
                    Text("DISABLED", color = colors.danger, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 3.sp)
                }
            }
            // RIGHT ~38%: weapon buttons (combat) — three stacked bays, art-filled, energy fill, hold-to-charge.
            Column(Modifier.weight(0.38f).fillMaxHeight().padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hud != null) {
                    hud.bays.forEach { bay ->
                        val accent = when (bay.bay) { "attack" -> colors.danger; "support" -> colors.blue; else -> colors.gold }
                        WeaponButton(
                            bay.bay.uppercase(), bay.itemId, bay.itemName, bay.ready, hud.energy / 100f, accent,
                            Modifier.fillMaxWidth().weight(1f),
                            onHold = { engine.holdBay(bay.bay) }, onRelease = { engine.fireBay(bay.bay) },
                        )
                    }
                } else {
                    Box(
                        Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(colors.panel.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("NO WEAPONS\nplay BATTLE to fight", color = colors.textDim, fontFamily = font, fontSize = 11.sp, textAlign = TextAlign.Center, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

/**
 * The 4.0.4 vertical throttle (ThrottleContainer / Trottle.gd): a tall rounded bar; drag anywhere on it
 * to set speed (top = full). Magenta fill, matching the original. Drives [onChange] live.
 */
@Composable
private fun VerticalThrottle(value: Float, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.clip(shape).background(Color(0x1EFFFFFF)).border(1.dp, colors.panelBorder, shape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ch = awaitPointerEvent().changes.firstOrNull() ?: continue
                        if (ch.pressed) onChange((1f - ch.position.y / size.height).coerceIn(0f, 1f))
                    }
                }
            },
    ) {
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(value.coerceIn(0f, 1f)).clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFFFF8AE0), Color(0xFFEC4FD0)))),
        )
        Text("THROTTLE", fontFamily = font, color = Color(0xFF27122E), fontSize = 9.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
    }
}

@Composable
private fun ControlButton(glyph: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(56.dp).clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.85f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
}

/**
 * The 4.0.4 weapon bay button (Action/Action2/Action3): a rounded-rect with the equipped weapon's art
 * filling it and an energy fill rising over it. Press-and-hold to fire continuously — the hold ticks the
 * charge (~80ms) and drains energy live; release fires the charged shot. Glows holo while engaged, dims
 * on cooldown.
 */
@Composable
private fun WeaponButton(
    label: String, itemId: String?, name: String, ready: Boolean, energyFrac: Float, accent: Color,
    modifier: Modifier = Modifier, onHold: () -> Unit, onRelease: () -> Unit,
) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val icon = rememberAsset(ItemRepository.imageAsset(itemId))
    val scope = rememberCoroutineScope()
    var held by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val a = if (ready) 1f else 0.4f
    Box(
        modifier.clip(shape).background(Color(0x14FFFFFF))
            .border(if (held) 3.dp else 2.5.dp, if (held) colors.blue else accent.copy(alpha = 0.6f), shape)
            .pointerInput(ready, itemId) {
                if (!ready || itemId == null) return@pointerInput
                detectTapGestures(onPress = {
                    held = true
                    val job = scope.launch { while (isActive) { onHold(); delay(80) } }
                    tryAwaitRelease()
                    job.cancel(); held = false; onRelease()
                })
            },
    ) {
        // energy fill rising from the bottom, behind the weapon art
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(energyFrac.coerceIn(0f, 1f))
                .background(Brush.verticalGradient(listOf(Color(0x554FB0FF), Color(0x224FB0FF)))),
        )
        if (icon != null) Image(icon, name, Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit, alpha = a)
        Text(label, fontFamily = font, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
        Text(name.take(14), fontFamily = font, color = colors.textPrimary.copy(alpha = 0.85f), fontSize = 9.sp,
            maxLines = 1, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
    }
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
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val engine = remember { RaceEngineHolder.engine }
    val standings = engine.state.standings
    val isCampaign = campaignMissionId.isNotBlank()
    // Evaluate + award campaign stars exactly once for this result.
    val summary = remember(campaignMissionId) {
        if (isCampaign) CampaignEngine.completeMission(ctx, campaignMissionId, engine.state) else null
    }

    OverdriveScaffold(
        title = when {
            !isCampaign -> "Results"
            summary?.won == true -> "Mission Complete"
            else -> "Mission Failed"
        },
        onBack = { nav.back() }, heroImage = null,
    ) { mod ->
        Column(mod.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (summary != null) {
                OverdrivePanel(Modifier.fillMaxWidth()) { inner ->
                    Column(inner, horizontalAlignment = Alignment.CenterHorizontally) {
                        StarRow(earned = summary.totalStarsForMission, size = 30)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (summary.newStars.isEmpty()) "No new stars this run"
                            else "+${summary.newStars.size}★  ·  +${summary.coinsAwarded} coins",
                            fontFamily = font, color = colors.gold, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        )
                        summary.newStars.forEach { Text("• ${it.description}", fontFamily = font, color = colors.textDim, fontSize = 12.sp) }
                    }
                }
            }
            if (standings.isEmpty()) {
                Text("No race data.", fontFamily = font, color = colors.textDim)
            } else {
                Text("FINISH ORDER", fontFamily = font, color = colors.gold, fontSize = 12.sp, letterSpacing = 2.sp)
                standings.forEachIndexed { i, c ->
                    OverdrivePanel(Modifier.fillMaxWidth()) { inner ->
                        Row(inner.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${ordinal(i + 1)}   ${if (c.isPlayer) "▸ " else ""}${carName(c.modelId, c.name)}", fontFamily = font, color = if (c.isPlayer) colors.gold else colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("${c.laps} laps · ${c.transitions} seg", fontFamily = font, color = colors.textDim, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Open Loot", {
                    val playerVehicle = engine.state.cars.firstOrNull { it.isPlayer }?.let { carName(it.modelId, it.name) }
                    val r = MetaGame.rollLoot(playerVehicle)   // car-eligible loot when we know the player's car
                    nav.showOverlay(Overlay.LootReveal(r.coins, r.itemId, r.itemName, r.rarity, r.rarityColor, r.badge))
                }, Modifier.weight(1f), ButtonAccent.Gold)
                if (isCampaign) PrimaryButton("Next Mission", { nav.go(Routes.CampaignGraph) }, Modifier.weight(1f), ButtonAccent.Blue)
                else PrimaryButton("Rematch", { nav.go(Routes.MatchSetup()) }, Modifier.weight(1f), ButtonAccent.Outline)
                PrimaryButton("Home", { nav.home() }, Modifier.weight(1f), ButtonAccent.Outline)
            }
        }
    }
}

@Composable
private fun CarThumb(modelId: Int) {
    val car = remember(modelId) { GameData.byModelId(modelId) }
    val sprite = rememberAsset(car?.spriteFile?.let { "cars/$it" })
    val colors = OverdriveTheme.colors
    Box(Modifier.size(width = 72.dp, height = 40.dp), contentAlignment = Alignment.Center) {
        if (sprite != null) Image(sprite, car?.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Box(Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(colors.barEmpty))
    }
}

private fun carName(modelId: Int, fallback: String): String =
    GameData.byModelId(modelId)?.name ?: fallback

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
