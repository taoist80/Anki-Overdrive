package dev.overdrive.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.overdrive.data.ContentRepository
import dev.overdrive.data.model.Chapter
import dev.overdrive.game.campaign.CampaignEngine
import dev.overdrive.nav.Overlay
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Routes
import dev.overdrive.profile.ProfileRepository
import dev.overdrive.ui.components.ButtonAccent
import dev.overdrive.ui.components.OverdrivePanel
import dev.overdrive.ui.components.OverdriveScaffold
import dev.overdrive.ui.components.PrimaryButton
import dev.overdrive.ui.components.StarRow
import dev.overdrive.ui.theme.OverdriveTheme

@Composable
fun ChapterSelectScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    remember { CampaignEngine.ensureLoaded(ctx); 0 }
    val profile = ProfileRepository.profile          // observe progression changes
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val chapters = ContentRepository.chapters

    OverdriveScaffold(
        title = "Campaign",
        onBack = { nav.back() },
        right = { Text("${profile.totalStars}/${CampaignEngine.maxStars()} ★", fontFamily = font, color = colors.gold, fontSize = 14.sp) },
    ) { mod ->
        Column(mod.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            chapters.forEach { ch ->
                val unlocked = CampaignEngine.isChapterUnlocked(ch)
                ChapterRow(ch, unlocked, CampaignEngine.chapterStars(ch), CampaignEngine.chapterMaxStars(ch)) {
                    if (unlocked) nav.go(Routes.MissionSelect(ch.id.toIntOrNull() ?: 0))
                    else nav.showOverlay(Overlay.Alert("Locked", "Earn more stars in earlier chapters to unlock this one."))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ChapterRow(ch: Chapter, unlocked: Boolean, stars: Int, maxStars: Int, onClick: () -> Unit) {
    val font = OverdriveTheme.font
    val colors = OverdriveTheme.colors
    val tint = remember(ch.tint) { runCatching { Color(android.graphics.Color.parseColor(ch.tint)) }.getOrDefault(colors.gold) }
    OverdrivePanel(Modifier.fillMaxWidth().alpha(if (unlocked) 1f else 0.5f).clickable(onClick = onClick)) { inner ->
        Row(inner.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 6.dp, height = 44.dp).clip(RoundedCornerShape(3.dp)).background(tint))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("CHAPTER ${ch.id}", fontFamily = font, color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(if (unlocked) "$stars / $maxStars stars" else "🔒 Locked", fontFamily = font, color = colors.textDim, fontSize = 12.sp)
                }
            }
            Text("${ch.missions.size} ▸", fontFamily = font, color = colors.textDim, fontSize = 14.sp)
        }
    }
}

@Composable
fun MissionSelectScreen(nav: OverdriveNav, chapterId: Int) {
    val ctx = LocalContext.current
    remember { CampaignEngine.ensureLoaded(ctx); 0 }
    ProfileRepository.profile                          // observe
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val chapter = ContentRepository.chapters.firstOrNull { it.id == chapterId.toString() }
    val missionIds = chapter?.missions.orEmpty()

    OverdriveScaffold(title = "Chapter $chapterId", onBack = { nav.back() }) { mod ->
        Column(mod.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (missionIds.isEmpty()) Text("No missions", color = colors.textDim, fontFamily = font)
            missionIds.forEachIndexed { i, mid ->
                val mission = ContentRepository.missionsById[mid]
                val cmdr = mission?.let { ContentRepository.commander(it.opponent) }
                val unlocked = CampaignEngine.isMissionUnlocked(mid)
                val stars = CampaignEngine.starsFor(mid)
                OverdrivePanel(
                    Modifier.fillMaxWidth().alpha(if (unlocked) 1f else 0.45f)
                        .clickable { if (unlocked) nav.go(Routes.MissionDetail(mid)) },
                ) { inner ->
                    Row(inner.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("MISSION ${i + 1}", fontFamily = font, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (unlocked) "vs ${cmdr?.friendlyName ?: mission?.opponent}  ·  ${mission?.gameType}" else "🔒 Locked",
                                fontFamily = font, color = colors.textDim, fontSize = 12.sp,
                            )
                        }
                        StarRow(earned = stars)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun MissionDetailScreen(nav: OverdriveNav, missionId: String) {
    val ctx = LocalContext.current
    remember { CampaignEngine.ensureLoaded(ctx); 0 }
    ProfileRepository.profile                          // observe
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val mission = ContentRepository.missionsById[missionId]
    val cmdr = mission?.let { ContentRepository.commander(it.opponent) }
    val objectives = CampaignEngine.objectivesFor(missionId)

    OverdriveScaffold(title = cmdr?.friendlyName ?: "Mission", onBack = { nav.back() }) { mod ->
        Column(mod.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OverdrivePanel(Modifier.fillMaxWidth()) { inner ->
                Column(inner) {
                    Text("OPPONENT", fontFamily = font, color = colors.gold, fontSize = 11.sp, letterSpacing = 2.sp)
                    Text(cmdr?.friendlyName ?: mission?.opponent ?: "?", fontFamily = font, color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Mode: ${mission?.gameType ?: "?"}   ·   Track: ${mission?.preferenceTrack ?: "?"}" +
                            (cmdr?.preferenceVehicle?.takeIf { it.isNotBlank() }?.let { "   ·   Prefers: $it" } ?: ""),
                        fontFamily = font, color = colors.textDim, fontSize = 12.sp,
                    )
                }
            }

            Text("STAR OBJECTIVES", fontFamily = font, color = colors.gold, fontSize = 12.sp, letterSpacing = 2.sp)
            objectives.forEachIndexed { i, o ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (o.earned) "★" else "☆", color = if (o.earned) colors.gold else colors.barEmpty, fontSize = 22.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(o.description, fontFamily = font, color = colors.textPrimary, fontSize = 15.sp)
                        Text(
                            (if (o.evaluable) "+${o.rewardPoints} coins" else "+${o.rewardPoints} coins · needs items/vehicle tracking (Phase 4)"),
                            fontFamily = font, color = colors.textDim, fontSize = 11.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(
                    "Briefing", { nav.showOverlay(Overlay.MissionStory("Defeat ${cmdr?.friendlyName ?: "the rival"} on ${mission?.preferenceTrack ?: "the track"}.")) },
                    Modifier.fillMaxWidth(), ButtonAccent.Outline,
                )
                PrimaryButton(
                    "Start Mission", { nav.go(Routes.MatchSetup(mode = mission?.gameType ?: "", campaignMissionId = missionId)) },
                    Modifier.fillMaxWidth(), ButtonAccent.Gold,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
