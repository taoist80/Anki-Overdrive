package dev.overdrive.ui.screens

import androidx.compose.runtime.Composable
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Routes
import dev.overdrive.ui.components.ButtonAccent
import dev.overdrive.ui.components.NavAction
import dev.overdrive.ui.components.WireframeScreen

@Composable
fun ProfileHomeScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Profile",
    onBack = { nav.back() },
    subtitle = "Driver avatar, level/XP, medals, and account management.",
    actions = listOf(
        NavAction("Avatar", { nav.go(Routes.AvatarDetail) }, ButtonAccent.Blue),
        NavAction("Medals", { nav.go(Routes.ProfileMedals) }, ButtonAccent.Outline),
        NavAction("Account", { nav.go(Routes.AccountGraph) }, ButtonAccent.Outline),
    ),
)

@Composable
fun AvatarDetailScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Avatar",
    onBack = { nav.back() },
    subtitle = "Customize your avatar — character, color, equipment, and daily gear.",
    actions = listOf(NavAction("Choose Character", { nav.go(Routes.AvatarSelect) }, ButtonAccent.Blue)),
)

@Composable
fun AvatarSelectScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Choose Avatar",
    onBack = { nav.back() },
    subtitle = "Pick an avatar character.",
)

@Composable
fun ProfileMedalsScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Medals",
    onBack = { nav.back() },
    subtitle = "Achievement medals grid (medals config). Tap a medal for detail.",
)
