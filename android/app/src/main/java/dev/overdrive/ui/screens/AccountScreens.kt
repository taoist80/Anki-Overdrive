package dev.overdrive.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.overdrive.net.BackendClient
import dev.overdrive.nav.Overlay
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Routes
import dev.overdrive.profile.ProfileRepository
import dev.overdrive.ui.components.ButtonAccent
import dev.overdrive.ui.components.NavAction
import dev.overdrive.ui.components.OverdriveScaffold
import dev.overdrive.ui.components.OverdriveTextField
import dev.overdrive.ui.components.PrimaryButton
import dev.overdrive.ui.components.WireframeScreen
import dev.overdrive.ui.theme.OverdriveTheme
import kotlinx.coroutines.launch

@Composable
fun AccountHomeScreen(nav: OverdriveNav) {
    val signedIn = BackendClient.signedIn
    val profile = ProfileRepository.profile
    WireframeScreen(
        title = "Account",
        onBack = { nav.back() },
        subtitle = if (signedIn)
            "Signed in as ${profile.driverName}. Coins, stars, and progression sync to the local backend."
        else
            "Sign in to sync your profile to the local backend, or create a new driver. " +
                "(Dev: run server/ and `adb reverse tcp:8080 tcp:8080`.)",
        actions = if (signedIn) listOf(
            NavAction("Log Out", { BackendClient.logout(); nav.back() }, ButtonAccent.Outline),
        ) else listOf(
            NavAction("Log In", { nav.go(Routes.AccountLogin) }, ButtonAccent.Blue),
            NavAction("Create Account", { nav.go(Routes.AccountNewOrExisting) }, ButtonAccent.Gold),
        ),
    )
}

@Composable
fun AccountLoginScreen(nav: OverdriveNav) = AuthForm(nav, "Log In", isSignup = false)

@Composable
fun AccountNewOrExistingScreen(nav: OverdriveNav) = AuthForm(nav, "Create Account", isSignup = true)

@Composable
private fun AuthForm(nav: OverdriveNav, title: String, isSignup: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var driverName by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    OverdriveScaffold(title = title, onBack = { nav.back() }) { mod ->
        Column(
            mod.verticalScroll(rememberScrollState()).widthIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OverdriveTextField(username, { username = it }, "Username", Modifier.fillMaxWidth())
            if (isSignup) OverdriveTextField(driverName, { driverName = it }, "Driver name", Modifier.fillMaxWidth())
            OverdriveTextField(password, { password = it }, "Password", Modifier.fillMaxWidth(), isPassword = true)
            status?.let { Text(it, fontFamily = font, color = colors.textDim, fontSize = 13.sp) }
            PrimaryButton(
                if (isSignup) "Create Account" else "Log In",
                {
                    if (username.isBlank() || password.isBlank()) { status = "Enter a username and password."; return@PrimaryButton }
                    busy = true; status = "Connecting…"
                    scope.launch {
                        val res = if (isSignup) BackendClient.signup(username.trim(), password, driverName.ifBlank { username }.trim())
                        else BackendClient.login(username.trim(), password)
                        busy = false
                        res.onSuccess {
                            ProfileRepository.adoptRemote(ctx, it.profile)
                            nav.showOverlay(Overlay.CelebrationUnlock("Welcome, ${it.profile.driverName}"))
                            nav.home()
                        }.onFailure {
                            status = "Failed: ${it.message ?: "unknown"}.\nIs server/ running with `adb reverse tcp:8080 tcp:8080`?"
                        }
                    }
                },
                Modifier.fillMaxWidth(),
                ButtonAccent.Gold,
                enabled = !busy,
            )
        }
    }
}

// --- Remaining account stubs (kept in the nav graph; off the primary path) ---

@Composable
fun AccountForgotPasswordScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Reset Password", onBack = { nav.back() },
    subtitle = "Enter your email to receive a reset link.",
)

@Composable
fun SavedAccountsScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Saved Drivers", onBack = { nav.back() },
    subtitle = "Quick-switch between locally saved driver profiles.",
)

@Composable
fun AccountNameCheckScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Choose Name", onBack = { nav.back() }, subtitle = "Original multi-step signup step (use Create Account).",
)

@Composable
fun AccountDateOfBirthScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Date of Birth", onBack = { nav.back() }, subtitle = "Age gate from the original flow.",
)

@Composable
fun AccountSignupEmailScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Email", onBack = { nav.back() }, subtitle = "Email entry from the original flow.",
)

@Composable
fun AccountSignupPasswordScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Password", onBack = { nav.back() }, subtitle = "Password entry from the original flow.",
)

@Composable
fun AccountSignupConfirmationScreen(nav: OverdriveNav) = WireframeScreen(
    title = "Confirm", onBack = { nav.back() }, subtitle = "Confirmation step from the original flow.",
)
