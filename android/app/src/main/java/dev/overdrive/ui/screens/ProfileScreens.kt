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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.overdrive.data.ContentRepository
import dev.overdrive.data.model.Commander26
import dev.overdrive.data.model.Medal
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Routes
import dev.overdrive.net.BackendClient
import dev.overdrive.profile.ProfileRepository
import dev.overdrive.ui.components.ButtonAccent
import dev.overdrive.ui.components.CoinPill
import dev.overdrive.ui.components.OverdrivePanel
import dev.overdrive.ui.components.OverdriveScaffold
import dev.overdrive.ui.components.PrimaryButton
import dev.overdrive.ui.theme.OverdriveTheme
import dev.overdrive.ui.theme.rememberAsset

/** 3.4 sb_Profile_ViewController: avatar head, rank/stars, Anki-account status, and sub-screens. */
@Composable
fun ProfileHomeScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    remember { ContentRepository.load(ctx); ProfileRepository.load(ctx); 0 }
    val s = ContentRepository.strings
    val p = ProfileRepository.profile
    val avatar = ContentRepository.commanders26ById[p.avatar] ?: ContentRepository.commanders26ById.values.firstOrNull()
    val portrait = rememberAsset(avatar?.portraitAsset)
    val frame = rememberAsset("ui/avatars/ui_framing_avatarHead.png")
    val signedIn = BackendClient.signedIn

    OverdriveScaffold(
        title = s.get("profileScreen.viewTitle", "Profile"),
        onBack = { nav.back() },
        right = { CoinPill(p.coins) },
    ) { mod ->
        Column(
            mod.verticalScroll(rememberScrollState()).widthIn(max = 520.dp).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                if (portrait != null) Image(portrait, "Avatar", Modifier.size(104.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                else Box(Modifier.size(104.dp).clip(CircleShape).background(colors.blue.copy(alpha = 0.22f)))
                if (frame != null) Image(frame, null, Modifier.fillMaxSize())
            }
            Text(p.driverName, fontFamily = font, color = colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                StatChip(s.get("general.rank", "Rank"), "${p.level}")
                StatChip(s.get("general.stars", "Stars"), "${p.totalStars}")
            }
            OverdrivePanel(Modifier.fillMaxWidth()) { inner ->
                Column(inner, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.get("profileScreen.ankiAccount", "ANKI ACCOUNT"), fontFamily = font, color = colors.textDim, fontSize = 12.sp, letterSpacing = 1.sp)
                    Text(
                        if (signedIn) s.get("profileScreen.accountStatus.synced", "STATUS: CONNECTED, SYNCED")
                        else s.get("profileScreen.accountStatus.notBackedUp", "STATUS: NOT BACKED UP"),
                        fontFamily = font, color = if (signedIn) colors.success else colors.orange, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    )
                    if (!signedIn) Text(
                        s.get("profileScreen.accountStatus.notBackedUp.subText", "Sign up for an Anki Account to save your Upgrades, Coins, and Campaign progress!"),
                        fontFamily = font, color = colors.textDim, fontSize = 13.sp,
                    )
                }
            }
            PrimaryButton(s.get("ankiButton.customizeCharacter", "Select Character"), { nav.go(Routes.AvatarDetail) }, Modifier.fillMaxWidth(), ButtonAccent.Blue)
            PrimaryButton(s.get("profileMedals.viewTitle", "Medals"), { nav.go(Routes.ProfileMedals) }, Modifier.fillMaxWidth(), ButtonAccent.Outline)
            PrimaryButton(s.get("profileScreen.ankiAccount", "Anki Account"), { nav.go(Routes.AccountGraph) }, Modifier.fillMaxWidth(), ButtonAccent.Outline)
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = font, color = colors.blue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label.uppercase(), fontFamily = font, color = colors.textDim, fontSize = 11.sp, letterSpacing = 1.sp)
    }
}

/** Selected-avatar detail; routes to the character picker. */
@Composable
fun AvatarDetailScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    remember { ContentRepository.load(ctx); ProfileRepository.load(ctx); 0 }
    val s = ContentRepository.strings
    val avatar = ContentRepository.commanders26ById[ProfileRepository.profile.avatar] ?: ContentRepository.commanders26ById.values.firstOrNull()
    val portrait = rememberAsset(avatar?.portraitAsset)

    OverdriveScaffold(title = "Avatar", onBack = { nav.back() }) { mod ->
        Column(
            mod.verticalScroll(rememberScrollState()).widthIn(max = 460.dp).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                if (portrait != null) Image(portrait, "Avatar", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
            Text(avatar?.name ?: "—", fontFamily = font, color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            if (!avatar?.bio.isNullOrBlank()) Text(avatar!!.bio, fontFamily = font, color = colors.textDim, fontSize = 13.sp, textAlign = TextAlign.Center)
            PrimaryButton(s.get("ankiButton.customizeCharacter", "Select Character"), { nav.go(Routes.AvatarSelect) }, Modifier.fillMaxWidth(), ButtonAccent.Gold)
        }
    }
}

/** 3.4 sb_AvatarSelect: pick a character. Avatars in Overdrive are the commander roster (authentic 2.6 art). */
@Composable
fun AvatarSelectScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    remember { ContentRepository.load(ctx); ProfileRepository.load(ctx); 0 }
    val all = ContentRepository.commanders26ById.values.filter { it.portraitAsset != null }.sortedBy { it.name }
    val selected = ProfileRepository.profile.avatar

    OverdriveScaffold(title = "Choose Character", onBack = { nav.back() }) { mod ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            modifier = mod,
            contentPadding = PaddingValues(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(all) { c -> AvatarCell(c, selected == c.id) { ProfileRepository.setAvatar(ctx, c.id); nav.back() } }
        }
    }
}

@Composable
private fun AvatarCell(c: Commander26, selected: Boolean, onClick: () -> Unit) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val art = rememberAsset(c.portraitAsset)
    Column(Modifier.clickable(onClick = onClick).padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(colors.panel.copy(alpha = 0.6f))
                .border(2.dp, if (selected) colors.blue else colors.panelBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (art != null) Image(art, c.name, Modifier.size(78.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.height(6.dp))
        Text(c.name, fontFamily = font, color = if (selected) colors.blue else colors.textDim, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** 3.4 MedalView + sb_MedalsDetail_Modal: the authentic 2.6 medal collection (icon + name + reward). */
@Composable
fun ProfileMedalsScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    remember { ContentRepository.load(ctx); 0 }
    val s = ContentRepository.strings
    val medals = ContentRepository.medals
    var sel by remember { mutableStateOf<Medal?>(null) }

    OverdriveScaffold(title = s.get("profileMedals.viewTitle", "Medals"), onBack = { nav.back() }) { mod ->
        Column(mod) {
            sel?.let { MedalDetail(it) }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(84.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(medals) { m -> MedalCell(m, sel?.id == m.id) { sel = m } }
            }
        }
    }
}

@Composable
private fun MedalCell(m: Medal, selected: Boolean, onClick: () -> Unit) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val art = rememberAsset(m.iconAsset)
    Column(Modifier.clickable(onClick = onClick).padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            if (art != null) Image(art, m.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else Box(Modifier.size(54.dp).clip(CircleShape).background(colors.barEmpty))
        }
        Spacer(Modifier.height(4.dp))
        Text(m.name, fontFamily = font, color = if (selected) colors.blue else colors.textDim, fontSize = 10.sp, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 12.sp)
    }
}

@Composable
private fun MedalDetail(m: Medal) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val art = rememberAsset(m.iconAsset)
    OverdrivePanel(Modifier.fillMaxWidth().padding(bottom = 12.dp)) { inner ->
        Row(inner, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (art != null) Image(art, m.name, Modifier.size(56.dp), contentScale = ContentScale.Fit)
            Column(Modifier.weight(1f)) {
                Text(m.name, fontFamily = font, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (m.description.isNotBlank()) Text(m.description, fontFamily = font, color = colors.textDim, fontSize = 13.sp)
                if (m.reward > 0) Text("+${m.reward} XP", fontFamily = font, color = colors.gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
