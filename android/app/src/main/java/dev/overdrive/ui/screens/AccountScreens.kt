package dev.overdrive.ui.screens

import androidx.compose.runtime.Composable
import dev.overdrive.nav.Overlay
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Routes
import dev.overdrive.ui.components.ButtonAccent
import dev.overdrive.ui.components.NavAction
import dev.overdrive.ui.components.WireframeScreen

@Composable
fun AccountHomeScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Account",
    onBack = { nav.back() },
    subtitle = "Sign in to sync your profile, coins, and progression to the local backend (Phase 4), " +
        "or create a new driver account.",
    actions = listOf(
        NavAction("Log In", { nav.go(Routes.AccountLogin) }, ButtonAccent.Blue),
        NavAction("Create Account", { nav.go(Routes.AccountNewOrExisting) }, ButtonAccent.Gold),
    ),
)

@Composable
fun AccountLoginScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Log In",
    onBack = { nav.back() },
    subtitle = "Username + password. Authenticates against the local backend.",
    actions = listOf(
        NavAction("Saved Accounts", { nav.go(Routes.SavedAccounts) }, ButtonAccent.Outline),
        NavAction("Forgot Password", { nav.go(Routes.AccountForgotPassword) }, ButtonAccent.Outline),
        NavAction("Log In", { nav.home() }, ButtonAccent.Blue),
    ),
)

@Composable
fun AccountForgotPasswordScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Reset Password",
    onBack = { nav.back() },
    subtitle = "Enter your email to receive a reset link.",
    actions = listOf(NavAction("Send", { nav.back() }, ButtonAccent.Blue)),
)

@Composable
fun SavedAccountsScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Saved Drivers",
    onBack = { nav.back() },
    subtitle = "Quick-switch between locally saved driver profiles.",
    actions = listOf(NavAction("Use Driver 01", { nav.home() }, ButtonAccent.Blue)),
)

@Composable
fun AccountNewOrExistingScreen(nav: OverdriveNav) = WireframeScreen(
    title = "New Driver",
    onBack = { nav.back() },
    subtitle = "Create a new account or link an existing one.",
    actions = listOf(NavAction("Create New", { nav.go(Routes.AccountNameCheck) }, ButtonAccent.Gold)),
)

@Composable
fun AccountNameCheckScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Choose Name",
    onBack = { nav.back() },
    subtitle = "Pick a driver name; availability checked against the backend.",
    actions = listOf(NavAction("Next", { nav.go(Routes.AccountDateOfBirth) }, ButtonAccent.Blue)),
)

@Composable
fun AccountDateOfBirthScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Date of Birth",
    onBack = { nav.back() },
    subtitle = "Age gate (COPPA-style flow from the original).",
    actions = listOf(NavAction("Next", { nav.go(Routes.AccountSignupEmail) }, ButtonAccent.Blue)),
)

@Composable
fun AccountSignupEmailScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Email",
    onBack = { nav.back() },
    subtitle = "Email entry for the new account.",
    actions = listOf(NavAction("Next", { nav.go(Routes.AccountSignupPassword) }, ButtonAccent.Blue)),
)

@Composable
fun AccountSignupPasswordScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Password",
    onBack = { nav.back() },
    subtitle = "Create a password.",
    actions = listOf(NavAction("Next", { nav.go(Routes.AccountSignupConfirmation) }, ButtonAccent.Blue)),
)

@Composable
fun AccountSignupConfirmationScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Confirm",
    onBack = { nav.back() },
    subtitle = "Review and create your account.",
    actions = listOf(
        NavAction("Create Account", {
            nav.showOverlay(Overlay.CelebrationUnlock("Welcome, Driver!"))
            nav.home()
        }, ButtonAccent.Gold),
    ),
)
