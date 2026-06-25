package dev.overdrive.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.overdrive.net.BackendClient
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Routes
import dev.overdrive.ui.components.ButtonAccent
import dev.overdrive.ui.components.NavAction
import dev.overdrive.ui.components.OverdrivePanel
import dev.overdrive.ui.components.OverdriveScaffold
import dev.overdrive.ui.components.WireframeScreen
import dev.overdrive.ui.theme.OverdriveTheme

@Composable
fun TracksHomeScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Tracks",
    onBack = { nav.back() },
    subtitle = "Browse known track layouts and best times.",
    actions = listOf(NavAction("Sample Track", { nav.go(Routes.TrackDetail("starter-kit")) }, ButtonAccent.Blue)),
)

@Composable
fun TrackDetailScreen(nav: OverdriveNav, trackId: String) = WireframeScreen(
    title = "Track",
    onBack = { nav.back() },
    subtitle = "Layout, segment map, and records for track '$trackId'.",
)

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

@Composable
fun NotificationPromptScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Notifications",
    onBack = { nav.back() },
    subtitle = "Opt in to push notifications.",
    actions = listOf(
        NavAction("Allow", { nav.back() }, ButtonAccent.Blue),
        NavAction("Not Now", { nav.back() }, ButtonAccent.Outline),
    ),
)

@Composable
fun AcknowledgementsScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Acknowledgements",
    onBack = { nav.back() },
    subtitle = "OverdriveX — a clean-room rebuild of Anki Overdrive for modern 64-bit Android. " +
        "Built on the Anki drive-sdk BLE protocol; assets/config from the original release. " +
        "Not affiliated with Anki or Digital Dream Labs.",
)

@Composable
fun AppSettingsScreen(nav: OverdriveNav) {
    val account = if (BackendClient.signedIn) "Signed in" else "Not signed in (local only)"
    return WireframeScreen(
        title = "Settings",
        onBack = { nav.back() },
        subtitle = "OverdriveX v0.1  ·  Account: $account  ·  Sound: coming soon",
        actions = listOf(
            NavAction("Account", { nav.go(Routes.AccountGraph) }, ButtonAccent.Blue),
            NavAction("BLE Lab (diagnostics)", { nav.go(Routes.BleLab) }, ButtonAccent.Outline),
            NavAction("Guide", { nav.go(Routes.Guide) }, ButtonAccent.Outline),
            NavAction("Developer Settings", { nav.go(Routes.DevSettings) }, ButtonAccent.Outline),
            NavAction("Acknowledgements", { nav.go(Routes.Acknowledgements) }, ButtonAccent.Outline),
        ),
    )
}

@Composable
fun DevSettingsScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Developer",
    onBack = { nav.back() },
    subtitle = "Backend: ${BackendClient.baseUrl}  ·  Debug toggles, BLE timing, content reload.",
    actions = listOf(NavAction("BLE Lab", { nav.go(Routes.BleLab) }, ButtonAccent.Blue)),
)
