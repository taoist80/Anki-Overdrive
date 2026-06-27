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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import dev.overdrive.CarManager
import dev.overdrive.CarState
import dev.overdrive.CarType
import dev.overdrive.GameData
import dev.overdrive.data.ContentRepository
import dev.overdrive.data.ItemRepository
import dev.overdrive.ui.theme.rememberAsset
import dev.overdrive.game.MetaGame
import dev.overdrive.game.campaign.CampaignEngine
import dev.overdrive.game.race.RaceEngineHolder
import dev.overdrive.game.race.Rivals
import dev.overdrive.game.race.RoadPieceGeometry
import dev.overdrive.game.race.TrackCache
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
import dev.overdrive.ui.components.CoinPill
import dev.overdrive.profile.ProfileRepository
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
    var chosenRivalId by remember { mutableStateOf<String?>(null) }   // Open Play: the AI commander to race (null = generic)

    val connected = mgr.cars.filter { it.state != CarState.Disconnected }
    val effectivePlayer = playerAddr ?: connected.firstOrNull()?.address
    // Connected cars whose model didn't resolve to a catalog car — kept selectable so none are lost.
    val unknownConnected = connected.filter { GameData.byModelId(it.modelId) == null }

    // Scan automatically while setting up: kick a scan on entry and keep re-discovering until the grid
    // is full, so cars powered on late just appear without tapping Scan. (Dropped cars are handled by the
    // CarManager's own desired-state reconnect loop.)
    LaunchedEffect(Unit) {
        while (true) {
            if (!mgr.scanning && mgr.connectedCars().size < mgr.maxCars) mgr.startScan()
            kotlinx.coroutines.delay(10_000)
        }
    }

    OverdriveScaffold(
        title = "Match Setup", onBack = { nav.back() },
        right = { Text(if (mode.isNotBlank()) mode.uppercase() else "RACE", fontFamily = font, color = colors.gold, fontSize = 12.sp, letterSpacing = 1.5.sp) },
    ) { mod ->
        Column(mod, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(if (mgr.scanning) "Scanning…" else "Scan", { mgr.startScan() }, accent = ButtonAccent.Blue)
                if (mgr.cars.any { it.state != CarState.Connected }) PrimaryButton("Reconnect", { mgr.reconnectDropped() }, accent = ButtonAccent.Gold)
                if (mgr.cars.isNotEmpty()) PrimaryButton("Disconnect", { mgr.disconnectAll() }, accent = ButtonAccent.Outline)
                Spacer(Modifier.weight(1f))
                Text("${connected.size}/${mgr.maxCars} connected", fontFamily = font, color = colors.textDim, fontSize = 12.sp)
            }
            Text(
                "Scanning automatically — power on your cars and they'll appear. Tap a nearby car to connect, " +
                    "tap a connected car to make it yours (P1)." +
                    if (campaignMissionId.isNotBlank()) "   ·   Mission: $campaignMissionId" else "",
                fontFamily = font, color = colors.textDim, fontSize = 12.sp,
            )

            // All three classes on one page, side by side. Each column scrolls independently (Supercars
            // is the longest at 10). Unrecognised connected cars ride along in the Supercars column so none
            // are lost. This takes the remaining height; the stepper + CTA pin to the bottom.
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CarCategory.values().forEach { cat ->
                    val catCars = GameData.cars.filter { it.category == cat }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Text(
                            "${cat.label.uppercase()}  ·  ${catCars.size}",
                            fontFamily = font, color = colors.gold, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        Column(
                            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            catCars.forEach { car ->
                                RosterCarCard(car, mgr, effectivePlayer) { playerAddr = it }
                            }
                            if (cat == CarCategory.SUPERCARS) unknownConnected.forEach { c ->
                                val slot = if (c.address == effectivePlayer) Slot.PLAYER else Slot.CONNECTED
                                RosterCard(c.name.ifBlank { "Unknown Car" }, c.modelId, slot,
                                    if (slot == Slot.PLAYER) "P1 · YOU" else "TAP TO SET P1") { playerAddr = c.address }
                            }
                        }
                    }
                }
            }

            // Open Play: choose an AI commander to race (campaign sets the opponent from the mission).
            if (campaignMissionId.isBlank()) {
                val pickRoster = remember { Rivals.roster().distinctBy { it.displayName } }
                Text("OPPONENT", fontFamily = font, color = colors.textDim, fontSize = 11.sp, letterSpacing = 1.5.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                    item { OpponentChip("Generic", null, chosenRivalId == null) { chosenRivalId = null } }
                    lazyItems(pickRoster, key = { it.commanderId ?: it.displayName ?: "" }) { rp ->
                        OpponentChip(rp.displayName ?: "?", ContentRepository.commander26(rp.commanderId)?.portraitAsset,
                            chosenRivalId == rp.commanderId) { chosenRivalId = rp.commanderId }
                    }
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
                    engine.campaignMissionId = campaignMissionId   // carry the Tournament mission to the results screen
                    // Tournament: the mission's opponent commander leads the AI rival field; other
                    // commanders fill any extra cars. Each is driven per its real vehicle_setup profile
                    // (purerace/race/battle × aggressive/defensive × tier). Open Play (no mission) = generic.
                    val primaryRivalId = (campaignMissionId.takeIf { it.isNotBlank() }
                        ?.let { ContentRepository.missionsById[it]?.opponent }) ?: chosenRivalId
                    engine.setRivals(if (primaryRivalId != null) Rivals.field(primaryRivalId, mgr.maxCars - 1) else emptyList())
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

/** An AI-commander choice in the Open Play opponent picker: portrait + name, gold when selected. */
@Composable
private fun OpponentChip(name: String, portrait: String?, selected: Boolean, onClick: () -> Unit) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val shape = RoundedCornerShape(8.dp)
    val img = rememberAsset(portrait)
    Column(
        Modifier.width(64.dp).clip(shape)
            .background(if (selected) colors.gold.copy(alpha = 0.18f) else Color(0x14FFFFFF))
            .border(1.dp, if (selected) colors.gold else colors.panelBorder, shape)
            .clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (img != null) Image(img, name, Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        else Box(Modifier.size(40.dp).clip(CircleShape).background(colors.panelBorder))
        Spacer(Modifier.height(4.dp))
        Text(name.uppercase(), fontFamily = font, color = if (selected) colors.gold else colors.textDim,
            fontSize = 8.sp, letterSpacing = 0.3.sp, maxLines = 1)
    }
}

/** One catalog car in a class column: resolves its live connection/found state → slot + tap behaviour. */
@Composable
private fun RosterCarCard(car: CarType, mgr: CarManager, effectivePlayer: String?, onPickPlayer: (String) -> Unit) {
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
            conn != null -> onPickPlayer(conn.address)
            found != null -> mgr.connect(found)
            else -> {}
        }
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
    remember { RoadPieceGeometry.load(ctx); 0 }     // piece geometry must be ready before driving
    val cachedTrack = remember { TrackCache.load(ctx); TrackCache.last }   // last player-confirmed ring, if any
    LaunchedEffect(Unit) { engine.startScan() }
    val st = engine.state

    OverdriveBackground(heroImage = null) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val mapped = st.trackMapped
            Text(
                when {
                    !st.scanComplete -> "SCANNING TRACK…"
                    mapped -> "TRACK MAPPED"
                    else -> "MAP INCOMPLETE"
                },
                fontFamily = font,
                color = if (st.scanComplete && !mapped) colors.danger else colors.gold,
                fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    !st.scanComplete -> "Cars are driving a slow lap to map the track — keep them on the track."
                    mapped -> "Closed a ${st.discoveredTrack.size}-piece loop. Confirm to race, or re-scan if the shape looks wrong."
                    else -> "Cars drove a lap but the loop didn't close cleanly — re-scan recommended before racing."
                },
                fontFamily = font, color = colors.textDim, fontSize = 13.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            // Live track view: the discovered pieces drawn from their real geometry, growing as the scan
            // maps them and closing into a loop when complete.
            if (st.discoveredTrack.size >= 2) {
                TrackMapView(st.discoveredTrack, st.scanComplete,
                    Modifier.fillMaxWidth().height(230.dp).padding(vertical = 4.dp))
            } else {
                Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
                    Text("Place cars on the track to begin mapping…", fontFamily = font, color = colors.textDim, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
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
            if (!st.scanComplete) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 5c: re-race a known track — pre-load its ring and skip the mapping lap (cars still
                    // localize + stage on the first finish crossing). Only offered before the ring is built.
                    if (cachedTrack != null && !mapped)
                        PrimaryButton("Use last track (${cachedTrack.ring.size}pc)",
                            { engine.useCachedTrack(cachedTrack.ring) }, accent = ButtonAccent.Blue)
                    PrimaryButton("Skip scan → Countdown", { engine.stop(); nav.go(Routes.Countdown) }, accent = ButtonAccent.Outline)
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 2.6 autoConfirmTrack=false: the player confirms the map (or re-scans) before racing.
                if (mapped) {
                    PrimaryButton("Confirm & Race",
                        { TrackCache.save(ctx, st.discoveredTrack); nav.go(Routes.Countdown) }, accent = ButtonAccent.Gold)
                    PrimaryButton("Re-scan", { engine.startScan() }, accent = ButtonAccent.Outline)
                } else {
                    PrimaryButton("Re-scan", { engine.startScan() }, accent = ButtonAccent.Gold)
                    PrimaryButton("Race anyway", { nav.go(Routes.Countdown) }, accent = ButtonAccent.Outline)
                }
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
    remember { RoadPieceGeometry.load(ctx); 0 }   // ensure piece geometry is ready before driving

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
    val mgr = remember { AnkiCarManagerHolder.require() }
    val st = engine.state
    val player = st.cars.firstOrNull { it.isPlayer }
    val rank = st.standings.indexOfFirst { it.isPlayer }.let { if (it >= 0) it + 1 else 1 }
    var throttle by remember { mutableFloatStateOf(0.62f) }  // ~560 mm/s of 900 max — lively but safe-cornering default

    // Race over (a car reached the lap target) -> victory flourish, then results.
    LaunchedEffect(st.finished) { if (st.finished) nav.go(Routes.GameOver) }

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

        // 2.6 "Vehicle Disconnected" pause: the engine holds every car while the player's link is down;
        // this modal (borrowed from 2.6's gamepad.alert pattern) covers the HUD until it reconnects,
        // with the same quit-or-resume choice (resume here is automatic on reconnect).
        if (!st.playerConnected) {
            Box(
                Modifier.fillMaxSize().zIndex(10f).background(Color(0xCC0A0610)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.widthIn(max = 460.dp).clip(RoundedCornerShape(16.dp)).background(colors.panel)
                        .border(1.dp, colors.danger.copy(alpha = 0.6f), RoundedCornerShape(16.dp)).padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("VEHICLE DISCONNECTED", fontFamily = font, color = colors.danger, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Your car lost its link. The race is paused and reconnects automatically the moment it's back. " +
                            "If it went off-track and powered down, get it to the charger to wake it up, then tap Reconnect to retry now.",
                        fontFamily = font, color = colors.textDim, fontSize = 13.sp, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(colors.blue)
                                .clickable { mgr.reconnectDropped() }.padding(horizontal = 22.dp, vertical = 10.dp),
                        ) { Text("RECONNECT", color = Color.White, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp) }
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(colors.danger)
                                .clickable { engine.stop(); nav.go(Routes.GameOver) }.padding(horizontal = 22.dp, vertical = 10.dp),
                        ) { Text("QUIT MATCH", color = Color.White, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp) }
                    }
                }
            }
        }

        // TOP: full-width Health bar (combat modes only) — the 4.0.4 Health TextureProgressBar.
        if (hud != null) {
            Box(Modifier.fillMaxWidth().height(10.dp).align(Alignment.TopCenter).background(colors.barEmpty)) {
                Box(Modifier.fillMaxWidth((hud.health / 100f).coerceIn(0f, 1f)).fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(colors.success, Color(0xFF9BE8B0)))))
            }
        }

        // RIVAL identity: who you're racing. A compact stack of the AI commanders' portrait + name + lap,
        // top-left in the gap above the centered throttle (portrait resolved like the results screen).
        val rivalCars = st.cars.filter { !it.isPlayer }
        if (rivalCars.isNotEmpty()) {
            Column(
                Modifier.align(Alignment.TopStart).padding(top = if (hud != null) 16.dp else 8.dp, start = 6.dp)
                    .widthIn(max = 150.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                rivalCars.forEach { rv ->
                    val portrait = ContentRepository.commanders26ById.values
                        .firstOrNull { it.name.equals(rv.name, true) }?.portraitAsset
                    val img = rememberAsset(portrait)
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x66000000)).padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (img != null) Image(img, rv.name, Modifier.size(24.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        else Box(Modifier.size(24.dp).clip(CircleShape).background(colors.panelBorder))
                        Column {
                            Text(rv.name.uppercase(), fontFamily = font, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, letterSpacing = 0.5.sp)
                            Text("LAP ${rv.laps}${if (rv.offTrack) " · OFF" else ""}", fontFamily = font, color = if (rv.offTrack) colors.danger else colors.textDim, fontSize = 8.sp)
                        }
                    }
                }
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
                    EnergyBar(hud.energy, Modifier.fillMaxWidth())
                    hud.bays.forEach { bay ->
                        val accent = when (bay.bay) { "attack" -> colors.danger; "support" -> colors.blue; else -> colors.gold }
                        WeaponButton(
                            bay.bay.uppercase(), bay.itemId, bay.itemName, bay.ready, bay.fillFrac, bay.interaction, bay.active, accent,
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
/** Slim global-energy bar above the weapon bays (per-bay fills now show charge/heat/cooldown). */
@Composable
private fun EnergyBar(energy: Float, modifier: Modifier = Modifier) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val shape = RoundedCornerShape(6.dp)
    Box(modifier.height(16.dp).clip(shape).background(Color(0x18FFFFFF))) {
        Box(
            Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth((energy / 100f).coerceIn(0f, 1f)).clip(shape)
                .background(Brush.horizontalGradient(listOf(Color(0xFF6CC0FF), colors.blue))),
        )
        Text("ENERGY", fontFamily = font, color = Color(0xFF0C1830), fontSize = 8.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp, modifier = Modifier.align(Alignment.Center))
    }
}

/**
 * The 4.0.4 weapon bay button (Action/Action2/Action3): a rounded-rect with the equipped weapon's art
 * filling it and a per-bay meter behind it that reflects the item's real [Interaction]:
 *  - HOLD       → orange→red HEAT rising while held (full = overheated, bay locks until it cools)
 *  - CHARGE     → holo CHARGE building while held (release fires scaled by how full)
 *  - SINGLESHOT → a draining cooldown bar after a tap
 * Press-and-hold drives the ~80ms hold loop; release fires (or stops, for HOLD).
 */
@Composable
private fun WeaponButton(
    label: String, itemId: String?, name: String, ready: Boolean, fillFrac: Float, interaction: String,
    active: Boolean, accent: Color, modifier: Modifier = Modifier, onHold: () -> Unit, onRelease: () -> Unit,
) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val icon = rememberAsset(ItemRepository.imageAsset(itemId))
    val scope = rememberCoroutineScope()
    var held by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val a = if (ready) 1f else 0.4f
    val onColor = colors.success                       // support active (shield/boost up)
    // fill colour by interaction: heat (orange→red) / charge (holo) / cooldown (dim accent)
    val fillBrush = when (interaction) {
        "HOLD" -> Brush.verticalGradient(listOf(
            (if (fillFrac > 0.85f) Color(0xFFFF4D4D) else Color(0xFFFFA23A)).copy(alpha = 0.55f),
            Color(0x22FF7A00)))
        "CHARGE" -> Brush.verticalGradient(listOf(Color(0x884FB0FF), Color(0x224FB0FF)))
        else -> Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.12f)))
    }
    val tag = when (interaction) { "HOLD" -> "HOLD"; "CHARGE" -> "CHARGE"; else -> "TAP" }
    Box(
        modifier.clip(shape).background(if (active) onColor.copy(alpha = 0.12f) else Color(0x14FFFFFF))
            .border(if (active || held) 3.dp else 2.5.dp,
                if (active) onColor else if (held) colors.blue else accent.copy(alpha = 0.6f), shape)
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
        // per-bay meter rising from the bottom, behind the weapon art
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(fillFrac.coerceIn(0f, 1f)).background(fillBrush))
        if (icon != null) Image(icon, name, Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit, alpha = a)
        Text(label, fontFamily = font, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
        Row(Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Text(name.take(12), fontFamily = font, color = colors.textPrimary.copy(alpha = 0.85f), fontSize = 9.sp, maxLines = 1)
        }
        Text(if (active) "● ON" else if (ready) tag else "···",
            fontFamily = font, color = if (active) onColor else accent.copy(alpha = 0.8f), fontSize = 8.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

/**
 * Victory flourish (the 2.6 end screen): "YOU WIN!" / "DEFEATED" over the gold laurel wreath with your
 * finishing place, plus the defeated opponent commander. Animates in, then Continue → the full results.
 */
@Composable
fun VictoryScreen(nav: OverdriveNav) {
    val engine = remember { RaceEngineHolder.engine }
    remember { engine.stop(); 0 }   // ensure cars are stopped
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val won = engine.playerWon
    val place = engine.state.standings.indexOfFirst { it.isPlayer }.let { if (it >= 0) it + 1 else 1 }
    val opponentName = engine.opponentName
    val oppPortrait = rememberAsset(
        opponentName?.let { nm -> ContentRepository.commanders26ById.values.firstOrNull { it.name.equals(nm, true) }?.portraitAsset },
    )
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(560)) }

    OverdriveBackground {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.graphicsLayer { alpha = p.value; val s = 0.9f + 0.1f * p.value; scaleX = s; scaleY = s },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (won) "YOU WIN!" else "DEFEATED",
                    fontFamily = font, color = if (won) colors.gold else colors.danger,
                    fontSize = 46.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(8.dp))
                // real 3.4.0 results medallion (place in the glossy circle); greyed on a loss
                ResultsMedallion(place, won)
                if (opponentName != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (oppPortrait != null) Image(oppPortrait, opponentName, Modifier.size(44.dp).clip(RoundedCornerShape(percent = 50)), contentScale = ContentScale.Crop)
                        Text((if (won) "DEFEATED  ·  " else "BEATEN BY  ·  ") + opponentName.uppercase(),
                            fontFamily = font, color = colors.textDim, fontSize = 13.sp, letterSpacing = 1.sp)
                    }
                }
                Spacer(Modifier.height(28.dp))
                PrimaryButton("Continue", { nav.go(Routes.RaceResults()) }, Modifier.widthIn(min = 220.dp), ButtonAccent.Gold)
            }
        }
    }
}

@Composable
fun RaceResultsScreen(nav: OverdriveNav, campaignMissionId: String) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val engine = remember { RaceEngineHolder.engine }
    val standings = engine.state.standings
    // The mission id survives MatchSetup on the engine (the finish→Victory→Results hops drop the route arg).
    val missionId = campaignMissionId.ifBlank { engine.campaignMissionId }
    val isCampaign = missionId.isNotBlank()
    // Evaluate + award campaign stars exactly once for this result.
    val summary = remember(missionId) {
        if (isCampaign) CampaignEngine.completeMission(ctx, missionId, engine.state) else null
    }
    val profile = ProfileRepository.profile                                  // observe coin balance
    val won = standings.firstOrNull()?.isPlayer == true
    val playerVehicle = standings.firstOrNull { it.isPlayer }?.let { carName(it.modelId, it.name) }
    // Loot is earned by WINNING (2.6: "win races to earn Loot Crates"). Roll once; auto-open the reveal,
    // which credits the coins + item to the profile on dismiss. Shown exactly once (no manual re-open to
    // avoid double-crediting).
    val loot = remember(missionId, won) { if (won) MetaGame.rollLoot(playerVehicle) else null }
    LaunchedEffect(loot) {
        if (loot != null) {
            delay(800)
            nav.showOverlay(Overlay.LootReveal(loot.coins, loot.itemId, loot.itemName, loot.rarity, loot.rarityColor, loot.badge))
        }
    }

    OverdriveScaffold(
        title = when {
            !isCampaign -> if (won) "Victory" else "Results"
            summary?.won == true -> "Mission Complete"
            else -> "Mission Failed"
        },
        onBack = { nav.back() }, heroImage = null,
        right = { CoinPill(profile.coins, font) },
    ) { mod ->
        val playerPlace = standings.indexOfFirst { it.isPlayer }.let { if (it >= 0) it + 1 else 1 }
        Column(mod.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // real 3.4.0 results medallion header (greyed on a loss) — the standings screen's hero
            ResultsMedallion(playerPlace, won)
            Text(
                if (won) "🎁  LOOT CRATE EARNED" else "DEFEATED  ·  no reward — try again",
                fontFamily = font, color = if (won) colors.gold else colors.textDim,
                fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            )
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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CarThumb(c.modelId)
                                Text("${ordinal(i + 1)}   ${if (c.isPlayer) "▸ " else ""}${carName(c.modelId, c.name)}", fontFamily = font, color = if (c.isPlayer) colors.gold else colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("${c.laps} laps · ${c.transitions} seg", fontFamily = font, color = colors.textDim, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isCampaign) PrimaryButton("Next Mission", { nav.go(Routes.CampaignGraph) }, Modifier.weight(1f), ButtonAccent.Blue)
                else PrimaryButton("Rematch", { nav.go(Routes.MatchSetup()) }, Modifier.weight(1f), ButtonAccent.Outline)
                PrimaryButton("Home", { nav.home() }, Modifier.weight(1f), ButtonAccent.Outline)
            }
        }
    }
}

/**
 * Metrically-accurate centerline: lay the discovered pieces head-to-tail using each piece's REAL geometry
 * ([RoadPieceGeometry] length + signed radius → arc angle `length/|radius|`), so the rendered shape matches
 * the physical track (an oval reads as an oval) rather than 4.0.4's 90°-per-curve grid schematic.
 */
private fun trackCenterline(pieces: List<Int>): List<Offset> {
    if (pieces.isEmpty()) return emptyList()
    var x = 0.0; var y = 0.0; var hd = 0.0
    val pts = ArrayList<Offset>(); pts.add(Offset(0f, 0f))
    for (pid in pieces) {
        val g = RoadPieceGeometry.of(pid)
        val len = (g?.lengthMm ?: RoadPieceGeometry.DEFAULT_LENGTH_MM).toDouble()
        val r = (g?.radiusMm ?: 0).toDouble()
        if (g?.isCurve != true || r == 0.0) {                       // straight
            x += len * kotlin.math.cos(hd); y += len * kotlin.math.sin(hd); pts.add(Offset(x.toFloat(), y.toFloat()))
        } else {                                                     // arc of angle len/|r|, sign = turn direction
            val total = len / kotlin.math.abs(r); val sign = if (r > 0) 1.0 else -1.0
            val k = (Math.toDegrees(total) / 8.0).toInt().coerceAtLeast(2); val dA = total / k * sign; val seg = len / k
            repeat(k) { hd += dA; x += seg * kotlin.math.cos(hd); y += seg * kotlin.math.sin(hd); pts.add(Offset(x.toFloat(), y.toFloat())) }
        }
    }
    return pts
}

/**
 * Accurate top-down track view: a road ribbon stroked along the real-geometry centerline — dark road with
 * orange edges + a faint centre line (the 4.0.4 piece look), growing as the scan maps pieces and closing
 * into a loop when complete.
 */
@Composable
private fun TrackMapView(pieces: List<Int>, mapped: Boolean, modifier: Modifier = Modifier) {
    val orange = OverdriveTheme.colors.orange
    val gold = OverdriveTheme.colors.gold
    val road = Color(0xFF13101F)
    val pts = remember(pieces) { trackCenterline(pieces) }
    Canvas(modifier) {
        if (pts.size < 2) return@Canvas
        val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }; val maxY = pts.maxOf { it.y }
        val roadMm = 170f                                            // real Anki track width
        val w = (maxX - minX + roadMm).coerceAtLeast(1f); val h = (maxY - minY + roadMm).coerceAtLeast(1f)
        val pad = 18f
        val scale = kotlin.math.min((size.width - 2 * pad) / w, (size.height - 2 * pad) / h)
        val ox = (size.width - (maxX - minX) * scale) / 2f - minX * scale
        val oy = (size.height - (maxY - minY) * scale) / 2f - minY * scale
        fun tx(o: Offset) = Offset(ox + o.x * scale, oy + o.y * scale)
        val path = Path().apply {
            val p0 = tx(pts[0]); moveTo(p0.x, p0.y)
            for (i in 1 until pts.size) { val p = tx(pts[i]); lineTo(p.x, p.y) }
            if (mapped) close()
        }
        val roadPx = (roadMm * scale).coerceAtLeast(10f)
        // glow → orange edges → dark road surface → faint centre line
        drawPath(path, orange.copy(alpha = 0.22f), style = Stroke(width = roadPx + 12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, orange, style = Stroke(width = roadPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, road, style = Stroke(width = (roadPx - 8f).coerceAtLeast(2f), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, orange.copy(alpha = 0.45f), style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(gold, (roadPx * 0.42f).coerceAtLeast(5f), tx(pts[0]))   // start/finish
    }
}

/**
 * The shared end-screen medallion (real 3.4.0 `UI_ResultsWreathBanner`): the finishing [place] in the
 * glossy circle, "VICTORY"/"FINISHED" on the ribbon. Greyed (desaturated) on a loss so it never reads as
 * a win. Used by both the [VictoryScreen] flourish and the [RaceResultsScreen] standings header.
 */
@Composable
private fun ResultsMedallion(place: Int, won: Boolean, modifier: Modifier = Modifier) {
    val font = OverdriveTheme.font
    val banner = rememberAsset("ui/ui_results_banner.png")
    val filter = if (won) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    Box(modifier.width(300.dp).height(243.dp), contentAlignment = Alignment.Center) {
        if (banner != null) Image(banner, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, colorFilter = filter)
        Text(ordinal(place).uppercase(), fontFamily = font, color = Color.White,
            fontSize = 50.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center).padding(bottom = 48.dp))
        Text(if (won) "VICTORY" else "FINISHED", fontFamily = font, color = Color(0xFF09384A),
            fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp))
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
