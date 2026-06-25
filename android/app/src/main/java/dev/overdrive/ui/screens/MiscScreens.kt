package dev.overdrive.ui.screens

import androidx.compose.runtime.Composable
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Routes
import dev.overdrive.ui.components.ButtonAccent
import dev.overdrive.ui.components.NavAction
import dev.overdrive.ui.components.WireframeScreen

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

@Composable
fun GuideScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Guide",
    onBack = { nav.back() },
    subtitle = "How to set up your track, pair cars, and play each mode.",
)

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
    subtitle = "Credits and open-source licenses.",
)

@Composable
fun AppSettingsScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Settings",
    onBack = { nav.back() },
    subtitle = "Sound, graphics, notifications, and account options.",
    actions = listOf(
        NavAction("BLE Lab (diagnostics)", { nav.go(Routes.BleLab) }, ButtonAccent.Blue),
        NavAction("Guide", { nav.go(Routes.Guide) }, ButtonAccent.Outline),
        NavAction("Notifications", { nav.go(Routes.NotificationPrompt) }, ButtonAccent.Outline),
        NavAction("Developer Settings", { nav.go(Routes.DevSettings) }, ButtonAccent.Outline),
        NavAction("Acknowledgements", { nav.go(Routes.Acknowledgements) }, ButtonAccent.Outline),
    ),
)

@Composable
fun DevSettingsScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Developer",
    onBack = { nav.back() },
    subtitle = "Debug toggles, force-FTUE, BLE timing, and content reload.",
    actions = listOf(NavAction("BLE Lab", { nav.go(Routes.BleLab) }, ButtonAccent.Blue)),
)
