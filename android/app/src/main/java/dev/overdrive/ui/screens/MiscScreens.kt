package dev.overdrive.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.overdrive.data.ContentRepository
import dev.overdrive.net.BackendClient
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Routes
import dev.overdrive.ui.components.ButtonAccent
import dev.overdrive.ui.components.NavAction
import dev.overdrive.ui.components.OverdrivePanel
import dev.overdrive.ui.components.OverdriveScaffold
import dev.overdrive.ui.components.PrimaryButton
import dev.overdrive.ui.components.WireframeScreen
import dev.overdrive.ui.theme.OverdriveTheme
import dev.overdrive.ui.theme.rememberAsset

// The 8 named Overdrive tracks (string keys → "The Wedge", "Capsule", …).
private val TRACK_IDS = listOf("track_01", "track_02", "track_03", "track_04", "track_05", "track_06", "track_07", "track_08")

// Authentic 2.6 track-piece sprites (ui/track/) keyed to the piece taxonomy in tracks.json.
private val TRACK_PIECES = listOf(
    "track-piece-start.png" to "Start",
    "track-piece-straight.png" to "Straight",
    "track-piece-left.png" to "Curve",
    "track-piece-intersection.png" to "Cross",
    "track-piece-jump.png" to "Jump",
    "track-piece-landing.png" to "Landing",
)
private val TRACK_ZONES = listOf(
    "track-piece-zone-race.png" to "Race Zone",
    "track-piece-zone-combat.png" to "Combat Zone",
    "track-piece-zone-ff.png" to "FastFurious Zone",
)

// Parsed-layout piece type → sprite (track_layouts.json uses these canonical types).
private val PIECE_SPRITE = mapOf(
    "start" to "track-piece-start.png",
    "straight" to "track-piece-straight.png",
    "curve" to "track-piece-left.png",
    "intersection" to "track-piece-intersection.png",
    "jump" to "track-piece-jump.png",
    "landing" to "track-piece-landing.png",
    "zone" to "track-piece-zone-race.png",
    "special" to "track-piece-intersection.png",
)

@Composable
fun TracksHomeScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    remember { ContentRepository.load(ctx); 0 }
    val s = ContentRepository.strings

    OverdriveScaffold(title = s.get("ankiButton.testTrack", "Tracks"), onBack = { nav.back() }) { mod ->
        Column(mod.verticalScroll(rememberScrollState()).widthIn(max = 560.dp).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TRACK_IDS.forEach { id ->
                val name = s.get(id, id)
                OverdrivePanel(Modifier.fillMaxWidth().clickable { nav.go(Routes.TrackDetail(id)) }) { inner ->
                    Row(inner, verticalAlignment = Alignment.CenterVertically) {
                        val layout = ContentRepository.trackLayouts[id]
                        Column(Modifier.weight(1f)) {
                            Text(name.uppercase(), fontFamily = font, color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                            if (layout != null) Text("${layout.size} pieces", fontFamily = font, color = colors.textDim, fontSize = 11.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val motif = layout?.take(6) ?: listOf("start", "straight", "curve", "jump")
                                motif.forEach { PieceThumb(PIECE_SPRITE[it] ?: "track-piece-straight.png", 26.dp) }
                            }
                        }
                        Text("›", fontFamily = font, color = colors.blue, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TrackDetailScreen(nav: OverdriveNav, trackId: String) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    remember { ContentRepository.load(ctx); 0 }
    val s = ContentRepository.strings
    val name = s.get(trackId, trackId)
    val layout = ContentRepository.trackLayouts[trackId]   // exact piece sequence from the 3.4 modular map

    OverdriveScaffold(title = name, onBack = { nav.back() }) { mod ->
        Column(mod.verticalScroll(rememberScrollState()).widthIn(max = 560.dp).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (layout != null) {
                SectionLabel("LAYOUT · ${layout.size} PIECES")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    layout.forEach { PieceThumb(PIECE_SPRITE[it] ?: "track-piece-straight.png", 48.dp) }
                }
            } else {
                SectionLabel("LAYOUT")
                Text("Exact layout unavailable for this track — representative pieces shown.", fontFamily = font, color = colors.textDim, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("track-piece-start.png", "track-piece-straight.png", "track-piece-left.png", "track-piece-jump.png",
                        "track-piece-landing.png", "track-piece-straight.png", "track-piece-left.png").forEach { PieceThumb(it, 48.dp) }
                }
            }
            SectionLabel("PIECE KEY")
            FlowChips(TRACK_PIECES)
            SectionLabel("ZONES")
            FlowChips(TRACK_ZONES)
            PrimaryButton(s.get("ankiButton.beginScan", "Use This Track"), { nav.go(Routes.MatchSetup()) }, Modifier.fillMaxWidth(), ButtonAccent.Gold)
        }
    }
}

@Composable
private fun PieceThumb(asset: String, sz: androidx.compose.ui.unit.Dp) {
    val art = rememberAsset("ui/track/$asset")
    val colors = OverdriveTheme.colors
    if (art != null) Image(art, null, Modifier.size(sz), contentScale = ContentScale.Fit)
    else Box(Modifier.size(sz).clip(RoundedCornerShape(4.dp)).background(colors.barEmpty))
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontFamily = OverdriveTheme.font, color = OverdriveTheme.colors.textDim, fontSize = 12.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun FlowChips(items: List<Pair<String, String>>) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (asset, label) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
                PieceThumb(asset, 44.dp)
                Spacer(Modifier.height(4.dp))
                Text(label, fontFamily = font, color = colors.textDim, fontSize = 10.sp, maxLines = 2)
            }
        }
    }
}

private data class GuideSection(val title: String, val body: String)

private val GUIDE = listOf(
    GuideSection(
        "1 · Build your track",
        "Snap the track pieces into a closed loop with the Start/Finish piece included. Make sure " +
            "there are no gaps — the cars read codes printed on the road to know where they are.",
    ),
    GuideSection(
        "2 · Charge & power on your cars",
        "Charge each car on its dock, then power it on. In Match Setup, tap Scan — cars appear by " +
            "name and picture (Nuke, Skull, Mammoth…). Tap up to 4 to connect; tap one to make it your car (P1).",
    ),
    GuideSection(
        "3 · Drive",
        "After the countdown, drag THROTTLE to set your speed and tap ◄ / ► to change lanes. The game " +
            "auto-slows your car through curves so full throttle stays controllable on the straights. " +
            "If a car leaves the track it shows OFF TRACK and stops — set it back down to resume.",
    ),
    GuideSection(
        "4 · Race & rivals",
        "Your P1 car is yours to drive; the other connected cars race as AI rivals. Cross the finish " +
            "line to bank laps — most laps wins.",
    ),
    GuideSection(
        "5 · Campaign, coins & loot",
        "Play Campaign missions to earn stars (win / beat the time / win by a margin). Stars unlock the " +
            "next missions and chapters and award coins. Open loot boxes after races for coins and items, " +
            "and spend coins on vehicle upgrades in the Garage.",
    ),
)

@Composable
fun GuideScreen(nav: OverdriveNav) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    OverdriveScaffold(title = "Guide", onBack = { nav.back() }) { mod ->
        Column(mod.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GUIDE.forEach { s ->
                OverdrivePanel(Modifier.fillMaxWidth()) { inner ->
                    Column(inner) {
                        Text(s.title.uppercase(), fontFamily = font, color = colors.gold, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(s.body, fontFamily = font, color = colors.textPrimary, fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 3.4 NotificationPrompt: opt-in prompt with allow / not-now / never. */
@Composable
fun NotificationPromptScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    remember { ContentRepository.load(ctx); 0 }
    val s = ContentRepository.strings
    OverdriveScaffold(title = "Notifications", onBack = { nav.back() }) { mod ->
        Column(mod.verticalScroll(rememberScrollState()).widthIn(max = 460.dp).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Get notified when your cars finish charging, daily specials drop, and new campaigns unlock.",
                fontFamily = font, color = colors.textPrimary, fontSize = 15.sp)
            PrimaryButton("Allow", { nav.back() }, Modifier.fillMaxWidth(), ButtonAccent.Blue)
            PrimaryButton(s.get("ankiButton.notNow", "Not Now"), { nav.back() }, Modifier.fillMaxWidth(), ButtonAccent.Outline)
            PrimaryButton(s.get("ankiButton.neverAgain", "Never Send Notifications"), { nav.back() }, Modifier.fillMaxWidth(), ButtonAccent.Outline)
        }
    }
}

@Composable
fun AcknowledgementsScreen(nav: OverdriveNav) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    OverdriveScaffold(title = "Acknowledgements", onBack = { nav.back() }) { mod ->
        Column(mod.verticalScroll(rememberScrollState()).widthIn(max = 560.dp).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OverdrivePanel(Modifier.fillMaxWidth()) { inner ->
                Column(inner, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("OVERDRIVEX", fontFamily = font, color = colors.rose, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, letterSpacing = 2.sp)
                    Text("A clean-room rebuild of Anki Overdrive for modern 64-bit Android. Built on the Anki " +
                        "drive-sdk BLE protocol; UI, assets and config reverse-engineered from the original 2.6 / 3.4 / 4.0.4 releases.",
                        fontFamily = font, color = colors.textPrimary, fontSize = 14.sp)
                    Text("Not affiliated with Anki or Digital Dream Labs. Trademarks belong to their owners.",
                        fontFamily = font, color = colors.textDim, fontSize = 12.sp)
                }
            }
        }
    }
}

/** Real settings list (replaces the wireframe). */
@Composable
fun AppSettingsScreen(nav: OverdriveNav) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val account = if (BackendClient.signedIn) "Signed in" else "Not signed in (local only)"
    OverdriveScaffold(title = "Settings", onBack = { nav.back() }) { mod ->
        Column(mod.verticalScroll(rememberScrollState()).widthIn(max = 460.dp).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OverdrivePanel(Modifier.fillMaxWidth()) { inner ->
                Column(inner, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("OVERDRIVEX v0.1", fontFamily = font, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Account: $account", fontFamily = font, color = colors.textDim, fontSize = 13.sp)
                    Text("Sound · Haptics: coming soon", fontFamily = font, color = colors.textDim, fontSize = 13.sp)
                }
            }
            PrimaryButton("Anki Account", { nav.go(Routes.AccountGraph) }, Modifier.fillMaxWidth(), ButtonAccent.Blue)
            PrimaryButton("Guide", { nav.go(Routes.Guide) }, Modifier.fillMaxWidth(), ButtonAccent.Outline)
            PrimaryButton("BLE Lab (diagnostics)", { nav.go(Routes.BleLab) }, Modifier.fillMaxWidth(), ButtonAccent.Outline)
            PrimaryButton("Developer Settings", { nav.go(Routes.DevSettings) }, Modifier.fillMaxWidth(), ButtonAccent.Outline)
            PrimaryButton("Acknowledgements", { nav.go(Routes.Acknowledgements) }, Modifier.fillMaxWidth(), ButtonAccent.Outline)
        }
    }
}

@Composable
fun DevSettingsScreen(nav: OverdriveNav) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    OverdriveScaffold(title = "Developer", onBack = { nav.back() }) { mod ->
        Column(mod.verticalScroll(rememberScrollState()).widthIn(max = 460.dp).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OverdrivePanel(Modifier.fillMaxWidth()) { inner ->
                Column(inner, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("BACKEND", fontFamily = font, color = colors.textDim, fontSize = 12.sp, letterSpacing = 1.sp)
                    Text(BackendClient.baseUrl, fontFamily = font, color = colors.textPrimary, fontSize = 14.sp)
                }
            }
            PrimaryButton("BLE Lab", { nav.go(Routes.BleLab) }, Modifier.fillMaxWidth(), ButtonAccent.Blue)
        }
    }
}
