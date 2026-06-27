package dev.overdrive.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.overdrive.data.ContentRepository
import dev.overdrive.net.BackendClient
import dev.overdrive.nav.Overlay
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Routes
import dev.overdrive.profile.ProfileRepository
import dev.overdrive.ui.components.ButtonAccent
import dev.overdrive.ui.components.NavAction
import dev.overdrive.ui.components.OverdrivePanel
import dev.overdrive.ui.components.OverdriveScaffold
import dev.overdrive.ui.components.OverdriveTextField
import dev.overdrive.ui.components.PrimaryButton
import dev.overdrive.ui.components.WireframeScreen
import dev.overdrive.ui.theme.OverdriveTheme
import kotlinx.coroutines.launch

/** Account hub — 3.4 AccountHome_ViewController: Log In (Login_Push) + Sign Up (Signup_Push). */
@Composable
fun AccountHomeScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    remember { ContentRepository.load(ctx); ProfileRepository.load(ctx); 0 }
    val s = ContentRepository.strings
    val signedIn = BackendClient.signedIn
    val profile = ProfileRepository.profile

    OverdriveScaffold(title = s.get("ankiButton.profile", "Account"), onBack = { nav.back() }) { mod ->
        Column(
            mod.verticalScroll(rememberScrollState()).widthIn(max = 460.dp).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (signedIn) "Signed in as ${profile.driverName}. Coins, stars and progression sync to the local backend."
                else "Sign in to sync your driver to the local backend, or create a new driver.",
                fontFamily = font, color = colors.textDim, fontSize = 14.sp,
            )
            if (signedIn) {
                PrimaryButton(s.get("ankiButton.logout", "Log Out"), { BackendClient.logout(); nav.back() }, Modifier.fillMaxWidth(), ButtonAccent.Outline)
            } else {
                PrimaryButton(s.get("ankiButton.login", "Log In"), { nav.go(Routes.AccountLogin) }, Modifier.fillMaxWidth(), ButtonAccent.Blue)
                PrimaryButton(s.get("ankiButton.signUp", "Create Account"), { nav.go(Routes.AccountNewOrExisting) }, Modifier.fillMaxWidth(), ButtonAccent.Gold)
            }
        }
    }
}

/**
 * Login screen — layout/behavior matched to 3.4 `AccountLogin_ViewController` (driver-name + password,
 * forgot-password link, saved-accounts, error text + spinner), copy from the bundled string table,
 * rendered in the 4.0.4 nebula skin and wired to the real local [BackendClient].
 */
@Composable
fun AccountLoginScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    remember { ContentRepository.load(ctx); 0 }
    val s = ContentRepository.strings

    var driverName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    OverdriveScaffold(title = s.get("ankiButton.login", "Log In"), onBack = { nav.back() }) { mod ->
        Column(
            mod.verticalScroll(rememberScrollState()).widthIn(max = 460.dp).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OverdriveTextField(
                driverName,
                { driverName = it.uppercase() },   // 3.4 DriverNameSelected: names are forced uppercase
                s.get("account.loginScreen.driverNamePlaceholderText", "DRIVER NAME"),
                Modifier.fillMaxWidth(),
            )
            OverdriveTextField(
                password, { password = it },
                s.get("account.loginScreen.passwordPlaceholderText", "PASSWORD"),
                Modifier.fillMaxWidth(), isPassword = true,
            )
            // ForgotPasswordButton → "ForgotPassword_Push"
            Text(
                s.get("account.loginScreen.forgotPasswordText", "Forgot password?"),
                fontFamily = font, color = colors.blue, fontSize = 13.sp,
                modifier = Modifier.clickable { nav.go(Routes.AccountForgotPassword) },
            )
            error?.let { Text(it, fontFamily = font, color = colors.danger, fontSize = 13.sp) }
            PrimaryButton(
                s.get("ankiButton.login", "Log In"),
                onClick = {
                    val dn = driverName.trim()
                    when {
                        dn.length !in 3..12 ->
                            error = s.get("account.loginScreen.driverNameErrorText", "Driver names must be 3-12 characters")
                        password.length < 6 ->
                            error = s.get("account.loginScreen.passwordErrorText", "Passwords must be at least 6 characters")
                        else -> {
                            busy = true; error = null
                            scope.launch {
                                val res = BackendClient.login(dn, password)
                                busy = false
                                res.onSuccess {
                                    ProfileRepository.adoptRemote(ctx, it.profile)
                                    nav.showOverlay(Overlay.CelebrationUnlock("Welcome, ${it.profile.driverName}"))
                                    nav.home()
                                }.onFailure {
                                    error = s.get("account.loginScreen.errorMessage", "Login Failed") +
                                        " — is server/ running with `adb reverse tcp:8080 tcp:8080`?"
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                accent = ButtonAccent.Gold,
                enabled = !busy,
            )
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = colors.blue, strokeWidth = 2.dp)
                    Text("Connecting…", fontFamily = font, color = colors.textDim, fontSize = 13.sp)
                }
            }
            // SavedDriversButton → "SavedAccounts_Push"
            PrimaryButton(
                s.get("ankiButton.savedAccounts", "Saved Accounts"),
                onClick = { nav.go(Routes.SavedAccounts) },
                modifier = Modifier.fillMaxWidth(),
                accent = ButtonAccent.Outline,
            )
        }
    }
}

/** In-memory carrier for the multi-step signup (NameCheck → DOB → Email → Password → Confirm). */
object SignupDraft {
    var driverName by mutableStateOf("")
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var useExisting by mutableStateOf(false)
    fun reset() { driverName = ""; email = ""; password = ""; useExisting = false }
}

/** 3.4 sb_AccountNewOrExisting: create-new vs use-current-driver choice. */
@Composable
fun AccountNewOrExistingScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    remember { ContentRepository.load(ctx); ProfileRepository.load(ctx); 0 }
    val s = ContentRepository.strings
    val current = ProfileRepository.profile.driverName.ifBlank { "Guest" }

    OverdriveScaffold(title = s.get("account.signup.title", "Sign Up"), onBack = { nav.back() }) { mod ->
        Column(
            mod.verticalScroll(rememberScrollState()).widthIn(max = 460.dp).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                s.get("account.newOrExistingScreen.descriptionText",
                    "Create an Account with your current Driver {0}, or create a new Driver to start fresh.").replace("{0}", current),
                fontFamily = font, color = colors.textPrimary, fontSize = 15.sp,
            )
            PrimaryButton(
                s.get("account.newOrExistingScreen.newAccountButtonText", "Create New Driver"),
                { SignupDraft.reset(); nav.go(Routes.AccountNameCheck) },
                Modifier.fillMaxWidth(), ButtonAccent.Gold,
            )
            PrimaryButton(
                s.get("account.newOrExistingScreen.useGuestButtonText", "Use Current Driver"),
                { SignupDraft.reset(); SignupDraft.driverName = current; SignupDraft.useExisting = true; nav.go(Routes.AccountDateOfBirth) },
                Modifier.fillMaxWidth(), ButtonAccent.Outline,
            )
        }
    }
}

/** Shared single-step scaffold for the account flow: instruction + field(s) + status/error + CTA. */
@Composable
private fun AccountStep(
    nav: OverdriveNav,
    title: String,
    cta: String,
    onCta: () -> Unit,
    instruction: String? = null,
    status: String? = null,
    error: String? = null,
    busy: Boolean = false,
    ctaEnabled: Boolean = true,
    ctaAccent: ButtonAccent = ButtonAccent.Gold,
    fields: @Composable ColumnScope.() -> Unit,
) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    OverdriveScaffold(title = title, onBack = { nav.back() }) { mod ->
        Column(
            mod.verticalScroll(rememberScrollState()).widthIn(max = 460.dp).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (instruction != null) Text(instruction, fontFamily = font, color = colors.textPrimary, fontSize = 15.sp)
            fields()
            if (status != null) Text(status, fontFamily = font, color = colors.textDim, fontSize = 12.sp)
            if (error != null) Text(error, fontFamily = font, color = colors.danger, fontSize = 13.sp)
            PrimaryButton(cta, onCta, Modifier.fillMaxWidth(), ctaAccent, enabled = ctaEnabled && !busy)
            if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), color = colors.blue, strokeWidth = 2.dp)
                Text("Connecting…", fontFamily = font, color = colors.textDim, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontFamily = font, color = colors.textDim, fontSize = 12.sp, letterSpacing = 1.sp)
        Text(value.ifBlank { "—" }, fontFamily = font, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = colors.blue, checkedThumbColor = Color.White))
        Text(label, fontFamily = font, color = colors.textDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

/** 3.4 sb_AccountForgotPassword: email field + Send; shows the success message locally. */
@Composable
fun AccountForgotPasswordScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    remember { ContentRepository.load(ctx); 0 }
    val s = ContentRepository.strings
    var email by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    val valid = email.contains("@") && email.substringAfter("@").contains(".")
    AccountStep(
        nav,
        title = s.get("account.forgotPassword.title", "Recover Password"),
        instruction = s.get("account.forgotPasswordScreen.statusText", "Enter the email used at signup, and we'll send instructions."),
        status = if (sent) s.get("account.forgotPasswordScreen.successAlert.message",
            "A password recovery email has been sent to {0} with instructions.").replace("{0}", email) else null,
        cta = s.get("ankiButton.send", "Send"),
        ctaEnabled = valid && !sent,
        onCta = { sent = true },
    ) {
        OverdriveTextField(email, { email = it }, s.get("account.forgotPasswordScreen.emailPlaceholderText", "EMAIL ADDRESS"), Modifier.fillMaxWidth())
    }
}

/** 3.4 AccountSavedAccounts: quick-switch list of locally saved drivers. */
@Composable
fun SavedAccountsScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    remember { ContentRepository.load(ctx); ProfileRepository.load(ctx); 0 }
    val s = ContentRepository.strings
    val driver = ProfileRepository.profile.driverName
    val signedIn = BackendClient.signedIn

    OverdriveScaffold(title = s.get("account.savedAccounts.title", "Saved Accounts"), onBack = { nav.back() }) { mod ->
        Column(
            mod.verticalScroll(rememberScrollState()).widthIn(max = 460.dp).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (signedIn && driver.isNotBlank()) {
                OverdrivePanel(Modifier.fillMaxWidth().clickable { nav.back() }) { inner ->
                    Row(inner, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(colors.blue.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                            Text(driver.take(1).uppercase(), fontFamily = font, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(driver, fontFamily = font, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Active driver · tap to switch", fontFamily = font, color = colors.textDim, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                Text("No saved drivers on this device yet. Sign in to save one.", fontFamily = font, color = colors.textDim, fontSize = 14.sp)
            }
        }
    }
}

/** 3.4 sb_AccountNameCheck: reserve a unique driver name (3–12 chars). */
@Composable
fun AccountNameCheckScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    remember { ContentRepository.load(ctx); 0 }
    val s = ContentRepository.strings
    var name by remember { mutableStateOf(SignupDraft.driverName) }
    val valid = name.trim().length in 3..12
    AccountStep(
        nav,
        title = "Choose Name",
        instruction = s.get("account.nameCheckScreen.instructionText", "First, you need to reserve a unique Driver name."),
        status = when {
            name.isEmpty() -> s.get("account.nameCheckScreen.statusText", "Do not use your real name")
            valid -> s.get("account.nameCheckScreen.nameAvailableText", "This name is available! Tap Continue to proceed.")
            else -> s.get("account.nameCheckScreen.userNameHintText", "The username must be between 3 to 12 characters")
        },
        cta = s.get("ankiButton.continue", "Continue"),
        ctaEnabled = valid,
        onCta = { SignupDraft.driverName = name.trim(); nav.go(Routes.AccountDateOfBirth) },
    ) {
        OverdriveTextField(name, { name = it.uppercase() },
            s.get("account.nameCheckScreen.driverNameInputField.placeholderText", "DRIVER NAME"), Modifier.fillMaxWidth())
    }
}

/** 3.4 AccountDateOfBirth: age gate. */
@Composable
fun AccountDateOfBirthScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    remember { ContentRepository.load(ctx); 0 }
    val s = ContentRepository.strings
    var dob by remember { mutableStateOf("") }
    AccountStep(
        nav,
        title = "Date of Birth",
        instruction = s.get("account.dateOfBirth.instructionText", "Please select your date of birth:"),
        cta = s.get("ankiButton.continue", "Continue"),
        ctaEnabled = dob.filter { it.isDigit() }.length >= 8,
        onCta = { nav.go(Routes.AccountSignupEmail) },
    ) {
        OverdriveTextField(dob, { dob = it }, "MM / DD / YYYY", Modifier.fillMaxWidth())
    }
}

/** 3.4 AccountSignupEmail: email entry. */
@Composable
fun AccountSignupEmailScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    remember { ContentRepository.load(ctx); 0 }
    val s = ContentRepository.strings
    var email by remember { mutableStateOf(SignupDraft.email) }
    val valid = email.contains("@") && email.substringAfter("@").contains(".")
    AccountStep(
        nav,
        title = "Email",
        instruction = s.get("account.signupEmailScreen.instructionText", "Please enter your email address"),
        status = s.get("account.signupEmailScreen.statusText", "Valid email address required"),
        cta = s.get("ankiButton.continue", "Continue"),
        ctaEnabled = valid,
        onCta = { SignupDraft.email = email.trim(); nav.go(Routes.AccountSignupPassword) },
    ) {
        OverdriveTextField(email, { email = it }, s.get("account.signupEmailScreen.emailPlaceholderText", "EMAIL ADDRESS"), Modifier.fillMaxWidth())
    }
}

/** 3.4 AccountSignupPassword: create a password (≥6 chars, not the driver name). */
@Composable
fun AccountSignupPasswordScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    remember { ContentRepository.load(ctx); 0 }
    val s = ContentRepository.strings
    var pw by remember { mutableStateOf("") }
    val dn = SignupDraft.driverName
    val valid = pw.length >= 6 && (dn.isBlank() || !pw.contains(dn, ignoreCase = true))
    AccountStep(
        nav,
        title = "Password",
        instruction = s.get("account.signupPasswordScreen.instructionText", "Please create a password"),
        status = s.get("account.signupPasswordScreen.statusText", "Must be at least 6 characters, and not include your driver name"),
        cta = s.get("ankiButton.continue", "Continue"),
        ctaEnabled = valid,
        onCta = { SignupDraft.password = pw; nav.go(Routes.AccountSignupConfirmation) },
    ) {
        OverdriveTextField(pw, { pw = it }, s.get("account.signupPasswordScreen.placeholderText", "CREATE A PASSWORD"), Modifier.fillMaxWidth(), isPassword = true)
    }
}

/** 3.4 AccountSignupConfirmation: review + terms toggles + Create Account (wired to BackendClient). */
@Composable
fun AccountSignupConfirmationScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    remember { ContentRepository.load(ctx); 0 }
    val s = ContentRepository.strings
    var keepUpdated by remember { mutableStateOf(false) }
    var agree by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    OverdriveScaffold(title = s.get("account.signupReview.title", "Sign Up Review"), onBack = { nav.back() }) { mod ->
        Column(
            mod.verticalScroll(rememberScrollState()).widthIn(max = 460.dp).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OverdrivePanel(Modifier.fillMaxWidth()) { inner ->
                Column(inner, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryRow(s.get("account.signupConfirmationScreen.driverNameTitle", "DRIVERNAME:"), SignupDraft.driverName)
                    SummaryRow(s.get("account.signupConfirmationScreen.emailTitle", "EMAIL:"), SignupDraft.email)
                }
            }
            ToggleRow(s.get("account.signupConfirmationScreen.keepMeUpdatedText", "Keep me up to date on all things Anki"), keepUpdated) { keepUpdated = it }
            ToggleRow(s.get("account.signupConfirmationScreen.agreeText", "I agree to the Policy and Terms linked below"), agree) { agree = it }
            error?.let { Text(it, fontFamily = font, color = colors.danger, fontSize = 13.sp) }
            PrimaryButton(
                s.get("ankiButton.createAccount", "Create Account"),
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        val res = BackendClient.signup(SignupDraft.driverName, SignupDraft.password, SignupDraft.driverName)
                        busy = false
                        res.onSuccess {
                            ProfileRepository.adoptRemote(ctx, it.profile)
                            SignupDraft.reset()
                            nav.showOverlay(Overlay.CelebrationUnlock("Welcome, ${it.profile.driverName}"))
                            nav.home()
                        }.onFailure {
                            error = s.get("account.signupConfirmationScreen.errorAlert.userNameTakenMessage", "Your driver name is taken") +
                                " — is server/ running with `adb reverse tcp:8080 tcp:8080`?"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                accent = ButtonAccent.Gold,
                enabled = agree && !busy,
            )
            if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), color = colors.blue, strokeWidth = 2.dp)
                Text("Creating account…", fontFamily = font, color = colors.textDim, fontSize = 13.sp)
            }
        }
    }
}
