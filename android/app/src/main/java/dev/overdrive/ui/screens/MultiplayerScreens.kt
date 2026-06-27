package dev.overdrive.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import dev.overdrive.data.ContentRepository
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Routes
import dev.overdrive.net.Mp
import dev.overdrive.net.MpLobby
import dev.overdrive.net.MpPlayer
import dev.overdrive.net.RoomClient
import dev.overdrive.profile.ProfileRepository
import dev.overdrive.ui.components.ButtonAccent
import dev.overdrive.ui.components.OverdrivePanel
import dev.overdrive.ui.components.OverdriveScaffold
import dev.overdrive.ui.components.OverdriveTextField
import dev.overdrive.ui.components.PrimaryButton
import dev.overdrive.ui.components.RacingName
import dev.overdrive.ui.theme.OverdriveTheme

// =============================================================================================
// Multiplayer (Phase 12) — local Wi-Fi. Host-authoritative; the Node server (server/) is the broker.
// Flow (see ARTIFACTS.md MP plan §02):  Multiplayer hub → [Host | Join] → shared Lobby → race spine.
// =============================================================================================

/** The MP hub: choose Host or Join. Connects to the broker on entry. (sb_Join_ViewController.) */
@Composable
fun MultiplayerScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    remember { ContentRepository.load(ctx); ProfileRepository.load(ctx); 0 }
    val driverName = ProfileRepository.profile.driverName

    LaunchedEffect(Unit) { RoomClient.connect() }
    // once a room exists (host created one), advance to the lobby
    LaunchedEffect(RoomClient.lobby) { if (RoomClient.lobby != null) nav.go(Routes.MpLobby) }

    OverdriveScaffold(title = "Multiplayer", onBack = { RoomClient.disconnect(); nav.back() }) { mod ->
        Column(mod, horizontalAlignment = Alignment.CenterHorizontally) {
            ConnBanner()
            Spacer(Modifier.height(10.dp))
            Text(
                "RACING AS  ${driverName.uppercase()}",
                fontFamily = OverdriveTheme.font, color = OverdriveTheme.colors.textDim,
                fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ChoiceCard(
                    "Host", "Run the race on this device — others join over Wi-Fi",
                    OverdriveTheme.colors.blue, "▸ HOST", Modifier.weight(1f),
                    enabled = RoomClient.conn == RoomClient.Conn.Connected,
                ) { RoomClient.createRoom(driverName) }
                ChoiceCard(
                    "Join", "Find a host on your network and race remotely",
                    OverdriveTheme.colors.gold, "▸ JOIN", Modifier.weight(1f),
                    enabled = RoomClient.conn == RoomClient.Conn.Connected,
                ) { nav.go(Routes.MpJoin) }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "One phone is the basestation — it holds the cars and runs the race. " +
                    "Everyone else drives and watches from their own screen.",
                fontFamily = OverdriveTheme.font, color = OverdriveTheme.colors.textDim,
                fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 460.dp),
            )
        }
    }
}

/** Host-discovery list + manual code entry. (ClientWait_VC_OD / join_game.tscn.) */
@Composable
fun MpJoinScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    remember { ProfileRepository.load(ctx); 0 }
    val driverName = ProfileRepository.profile.driverName
    var code by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { RoomClient.connect(); RoomClient.listRooms() }
    // joined a room → go to the lobby
    LaunchedEffect(RoomClient.lobby) { if (RoomClient.lobby != null) nav.go(Routes.MpLobby) }

    OverdriveScaffold(
        title = "Join a Game",
        onBack = { nav.back() },
        right = {
            Text(
                "↻ REFRESH", fontFamily = OverdriveTheme.font, color = OverdriveTheme.colors.blue,
                fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                modifier = Modifier.clickable { RoomClient.listRooms() }.padding(8.dp),
            )
        },
    ) { mod ->
        Column(mod) {
            ConnBanner()
            Spacer(Modifier.height(10.dp))

            // manual code entry
            OverdrivePanel(Modifier.fillMaxWidth()) { pm ->
                Row(pm, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OverdriveTextField(
                        value = code, onValueChange = { code = it.uppercase().take(4) },
                        label = "Room code", modifier = Modifier.weight(1f),
                    )
                    PrimaryButton("Join", { RoomClient.joinRoom(code, driverName) }, accent = ButtonAccent.Gold,
                        enabled = code.length == 4)
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("GAMES ON YOUR NETWORK")
            Spacer(Modifier.height(8.dp))

            val rooms = RoomClient.rooms
            if (rooms.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 28.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No open games found.\nMake sure the host is on the same Wi-Fi, then refresh.",
                        fontFamily = OverdriveTheme.font, color = OverdriveTheme.colors.textDim,
                        fontSize = 13.sp, textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(rooms, key = { it.code }) { r ->
                        RoomRow(r) { RoomClient.joinRoom(r.code, driverName) }
                    }
                }
            }
        }
    }
}

/** The shared lobby — renders for host and client off the same [RoomClient.lobby]. */
@Composable
fun MpLobbyScreen(nav: OverdriveNav) {
    val lobby = RoomClient.lobby
    val you = RoomClient.you
    val host = RoomClient.isHost
    var leaving by remember { mutableStateOf(false) }

    // unsolicited closure (host left / kicked) → bounce to the hub
    LaunchedEffect(lobby) { if (lobby == null && !leaving) nav.back() }
    if (lobby == null) return

    val onBack: () -> Unit = { leaving = true; RoomClient.leaveRoom(); nav.back() }

    OverdriveScaffold(title = "Lobby", onBack = onBack) { mod ->
        Column(mod.verticalScroll(rememberScrollState())) {
            ConnBanner()
            Spacer(Modifier.height(8.dp))

            // room code + mode/laps
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("ROOM CODE")
                    RacingName(lobby.code, fontSize = 38, hlColor = OverdriveTheme.colors.gold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    SectionLabel("MODE")
                    Text(
                        Mp.modeName(lobby.mode).uppercase(), fontFamily = OverdriveTheme.font,
                        color = OverdriveTheme.colors.blue, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${lobby.valueToReach} LAPS", fontFamily = OverdriveTheme.font,
                        color = OverdriveTheme.colors.textDim, fontSize = 12.sp,
                    )
                }
            }

            if (host && lobby.state == "Lobby") {
                Spacer(Modifier.height(12.dp))
                HostControls(lobby)
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("DRIVERS  ${lobby.occupied.size}/${lobby.maxPlayers}")
            Spacer(Modifier.height(8.dp))
            lobby.players.sortedBy { it.slotId }.forEach { p ->
                SlotRow(p, isYou = p.gamePlayerId == you?.gamePlayerId)
                Spacer(Modifier.height(8.dp))
            }

            if (lobby.state == "Lobby") {
                Spacer(Modifier.height(8.dp))
                YourCarPicker(you)
                Spacer(Modifier.height(18.dp))
                ReadyAndStart(lobby, you, host)
            } else {
                Spacer(Modifier.height(24.dp))
                StartingPlaceholder(host)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---- pieces ----------------------------------------------------------------------------------

@Composable
private fun ReadyAndStart(lobby: MpLobby, you: MpPlayer?, host: Boolean) {
    val ready = you?.ready == true
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryButton(
            if (ready) "Ready ✓" else "Ready up",
            { RoomClient.setReady(!ready) },
            accent = if (ready) ButtonAccent.Outline else ButtonAccent.Blue,
            modifier = Modifier.weight(1f),
        )
        if (host) {
            PrimaryButton(
                "Start race",
                { RoomClient.startGame() },
                accent = ButtonAccent.Gold,
                enabled = lobby.allReady,
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (host && !lobby.allReady) {
        Spacer(Modifier.height(8.dp))
        Text(
            if (lobby.occupied.size < 2) "Waiting for another driver to join…" else "Waiting for everyone to ready up…",
            fontFamily = OverdriveTheme.font, color = OverdriveTheme.colors.textDim, fontSize = 12.sp,
        )
    }
}

@Composable
private fun HostControls(lobby: MpLobby) {
    OverdrivePanel(Modifier.fillMaxWidth()) { pm ->
        Column(pm, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("GAME MODE")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("Race", lobby.mode == Mp.MODE_RACE) { RoomClient.setMode(Mp.MODE_RACE) }
                ModeChip("Battle", lobby.mode == Mp.MODE_BATTLE) { RoomClient.setMode(Mp.MODE_BATTLE) }
                ModeChip("KOTH", lobby.mode == Mp.MODE_KOTH) { RoomClient.setMode(Mp.MODE_KOTH) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionLabel("LAPS")
                Stepper(lobby.valueToReach) { v -> RoomClient.setTrack(lobby.roadMapFileName, v.coerceIn(1, 20)) }
            }
        }
    }
}

@Composable
private fun YourCarPicker(you: MpPlayer?) {
    val cars = remember { ContentRepository.cars }
    Column {
        SectionLabel("YOUR CAR")
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cars, key = { it.id }) { car ->
                val selected = you?.vehicleId == car.id
                val ct = if (you?.isHost == true) "BLE" else "Wifi"
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) OverdriveTheme.colors.blue.copy(alpha = 0.22f) else Color(0x14FFFFFF))
                        .border(
                            1.dp,
                            if (selected) OverdriveTheme.colors.blue else OverdriveTheme.colors.panelBorder,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { RoomClient.selectVehicle(car.id, connectionType = ct) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        car.name, fontFamily = OverdriveTheme.font,
                        color = if (selected) OverdriveTheme.colors.textPrimary else OverdriveTheme.colors.textDim,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotRow(p: MpPlayer, isYou: Boolean) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val carName = p.vehicleId?.let { id -> ContentRepository.cars.firstOrNull { it.id == id }?.name } ?: "—"
    val accent = if (isYou) colors.blue else colors.panelBorder
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.panel.copy(alpha = if (p.emptySlot) 0.4f else 0.82f))
            .border(1.dp, accent, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "P${p.slotId + 1}", fontFamily = font, color = colors.textDim, fontSize = 14.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp),
        )
        if (p.emptySlot) {
            Text("Open slot", fontFamily = font, color = colors.textDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
        } else {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        p.displayName ?: "Driver", fontFamily = font, color = colors.textPrimary,
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    )
                    if (p.isHost) Badge("HOST", colors.gold)
                    if (isYou) Badge("YOU", colors.blue)
                }
                Text(
                    "$carName · ${p.connectionType}", fontFamily = font, color = colors.textDim, fontSize = 12.sp,
                )
            }
            Text(
                if (p.ready) "READY" else "NOT READY",
                fontFamily = font, color = if (p.ready) colors.success else colors.textDim,
                fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun StartingPlaceholder(host: Boolean) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        RacingName("Race Starting", fontSize = 30)
        Spacer(Modifier.height(10.dp))
        Text(
            "All drivers locked in. The live race hands off to the basestation next " +
                "(remote driving lands in the next build step).",
            fontFamily = OverdriveTheme.font, color = OverdriveTheme.colors.textDim,
            fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp),
        )
        if (host) {
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Back to lobby", { RoomClient.backToLobby() }, accent = ButtonAccent.Outline)
        }
    }
}

// ---- small building blocks ------------------------------------------------------------------

@Composable
private fun ConnBanner() {
    val err = RoomClient.lastError
    val conn = RoomClient.conn
    val (msg, color) = when {
        err != null -> err to OverdriveTheme.colors.danger
        conn == RoomClient.Conn.Connecting -> "Connecting to server…" to OverdriveTheme.colors.textDim
        conn == RoomClient.Conn.Disconnected -> "Not connected — check the server address in Settings" to OverdriveTheme.colors.danger
        else -> return
    }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(msg, fontFamily = OverdriveTheme.font, color = color, fontSize = 12.sp)
    }
}

@Composable
private fun RoomRow(r: dev.overdrive.net.MpRoomSummary, onJoin: () -> Unit) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val full = r.playerCount >= r.maxPlayers
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.panel.copy(alpha = 0.82f))
            .border(1.dp, colors.panelBorder, RoundedCornerShape(12.dp))
            .clickable(enabled = !full, onClick = onJoin).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RacingName(r.code, fontSize = 22, hlColor = colors.gold)
                Text(r.hostName, fontFamily = font, color = colors.textPrimary, fontSize = 14.sp)
            }
            Text(
                "${Mp.modeName(r.mode)} · ${r.playerCount}/${r.maxPlayers} drivers",
                fontFamily = font, color = colors.textDim, fontSize = 12.sp,
            )
        }
        Text(
            if (full) "FULL" else "JOIN ▸",
            fontFamily = font, color = if (full) colors.textDim else colors.blue,
            fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun ChoiceCard(
    title: String, desc: String, accent: Color, cta: String, modifier: Modifier,
    enabled: Boolean, onClick: () -> Unit,
) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .height(190.dp)
            .clip(shape)
            .background(colors.panel.copy(alpha = if (enabled) 0.82f else 0.4f))
            .border(1.dp, if (enabled) accent.copy(alpha = 0.6f) else colors.panelBorder, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        RacingName(title, fontSize = 28, hlColor = accent)
        Spacer(Modifier.height(8.dp))
        Text(desc, fontFamily = font, color = colors.textDim, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Text(cta, fontFamily = font, color = accent, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = OverdriveTheme.colors
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.blue.copy(alpha = 0.25f) else Color(0x14FFFFFF))
            .border(1.dp, if (selected) colors.blue else colors.panelBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label.uppercase(), fontFamily = OverdriveTheme.font,
            color = if (selected) colors.textPrimary else colors.textDim,
            fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        StepBtn("–") { onChange(value - 1) }
        Text("$value", fontFamily = font, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        StepBtn("+") { onChange(value + 1) }
    }
}

@Composable
private fun StepBtn(label: String, onClick: () -> Unit) {
    val colors = OverdriveTheme.colors
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x1FFFFFFF))
            .border(1.dp, colors.panelBorder, RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = OverdriveTheme.font, color = colors.blue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(5.dp)).background(color.copy(alpha = 0.2f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, fontFamily = OverdriveTheme.font, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontFamily = OverdriveTheme.font, color = OverdriveTheme.colors.textDim,
        fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold,
    )
}
