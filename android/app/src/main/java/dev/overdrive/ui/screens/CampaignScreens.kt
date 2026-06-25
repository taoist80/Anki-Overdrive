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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.overdrive.data.ContentRepository
import dev.overdrive.data.model.Chapter
import dev.overdrive.nav.Overlay
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Routes
import dev.overdrive.ui.components.ButtonAccent
import dev.overdrive.ui.components.NavAction
import dev.overdrive.ui.components.OverdrivePanel
import dev.overdrive.ui.components.OverdriveScaffold
import dev.overdrive.ui.components.StarRow
import dev.overdrive.ui.components.WireframeScreen
import dev.overdrive.ui.theme.OverdriveTheme

@Composable
fun ChapterSelectScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    remember { ContentRepository.load(ctx); 0 }
    val chapters = ContentRepository.chapters
    OverdriveScaffold(title = "Campaign", onBack = { nav.back() }) { mod ->
        Column(mod, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            chapters.forEach { ch ->
                ChapterRow(ch) { nav.go(Routes.MissionSelect(ch.id.toIntOrNull() ?: 0)) }
            }
        }
    }
}

@Composable
private fun ChapterRow(ch: Chapter, onClick: () -> Unit) {
    val font = OverdriveTheme.font
    val colors = OverdriveTheme.colors
    val tint = remember(ch.tint) { runCatching { Color(android.graphics.Color.parseColor(ch.tint)) }.getOrDefault(colors.gold) }
    OverdrivePanel(Modifier.fillMaxWidth().clickable(onClick = onClick)) { inner ->
        Row(inner.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 6.dp, height = 44.dp).clip(RoundedCornerShape(3.dp)).background(tint))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("CHAPTER ${ch.id}", fontFamily = font, color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${ch.missions.size} missions", fontFamily = font, color = colors.textDim, fontSize = 12.sp)
                }
            }
            StarRow(earned = 0)
        }
    }
}

@Composable
fun MissionSelectScreen(nav: OverdriveNav, chapterId: Int) {
    val ctx = LocalContext.current
    remember { ContentRepository.load(ctx); 0 }
    val chapter = ContentRepository.chapters.firstOrNull { it.id == chapterId.toString() }
    val missions = chapter?.let { ContentRepository.missionsFor(it) }.orEmpty()
    OverdriveScaffold(title = "Chapter $chapterId", onBack = { nav.back() }) { mod ->
        Column(mod, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (missions.isEmpty()) {
                Text("No missions", color = OverdriveTheme.colors.textDim, fontFamily = OverdriveTheme.font)
            }
            missions.forEachIndexed { i, m ->
                val cmdr = ContentRepository.commander(m.opponent)
                OverdrivePanel(Modifier.fillMaxWidth().clickable { nav.go(Routes.MissionDetail(m.id)) }) { inner ->
                    Row(inner.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("MISSION ${i + 1}", fontFamily = OverdriveTheme.font, color = OverdriveTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "vs ${cmdr?.friendlyName ?: m.opponent}  ·  ${m.gameType}",
                                fontFamily = OverdriveTheme.font, color = OverdriveTheme.colors.textDim, fontSize = 12.sp,
                            )
                        }
                        StarRow(earned = 0)
                    }
                }
            }
        }
    }
}

@Composable
fun MissionDetailScreen(nav: OverdriveNav, missionId: String) {
    val ctx = LocalContext.current
    remember { ContentRepository.load(ctx); 0 }
    val mission = ContentRepository.missionsById[missionId]
    val cmdr = mission?.let { ContentRepository.commander(it.opponent) }
    val detail = buildString {
        append("Opponent: ${cmdr?.friendlyName ?: mission?.opponent ?: "?"}")
        cmdr?.let { append("  (tier ${it.tier})") }
        append("\nMode: ${mission?.gameType ?: "?"}")
        append("\nTrack: ${mission?.preferenceTrack ?: "?"}")
        cmdr?.preferenceVehicle?.takeIf { it.isNotBlank() }?.let { append("\nPrefers: $it") }
        append("\n\n0–3 star objectives (directed tasks) + commander bio wire in Phase 3.")
    }
    WireframeScreen(
        title = "Mission",
        onBack = { nav.back() },
        subtitle = detail,
        actions = listOf(
            NavAction("Briefing", { nav.showOverlay(Overlay.MissionStory("Intel: defeat ${cmdr?.friendlyName ?: "the rival"} on ${mission?.preferenceTrack ?: "the track"}.")) }, ButtonAccent.Outline),
            NavAction("Start Mission", { nav.go(Routes.MatchSetup(mode = mission?.gameType ?: "", campaignMissionId = missionId)) }, ButtonAccent.Gold),
        ),
    )
}
