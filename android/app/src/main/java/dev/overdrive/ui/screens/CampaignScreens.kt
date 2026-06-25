package dev.overdrive.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private data class ChapterStub(val id: Int, val name: String, val tier: String, val stars: Int, val total: Int)

private val CHAPTERS = listOf(
    ChapterStub(0, "Origins", "BRONZE", 6, 9),
    ChapterStub(1, "The Gauntlet", "BRONZE", 3, 9),
    ChapterStub(2, "Rival Run", "SILVER", 0, 12),
    ChapterStub(3, "Commander's Trial", "GOLD", 0, 15),
)

@Composable
fun ChapterSelectScreen(nav: OverdriveNav) {
    OverdriveScaffold(title = "Campaign", onBack = { nav.back() }) { mod ->
        Column(mod, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CHAPTERS.forEach { ch ->
                OverdrivePanel(Modifier.fillMaxWidth().clickable { nav.go(Routes.MissionSelect(ch.id)) }) { inner ->
                    Row(inner.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(ch.tier, fontFamily = OverdriveTheme.font, color = OverdriveTheme.colors.gold, fontSize = 11.sp, letterSpacing = 2.sp)
                            Text(ch.name.uppercase(), fontFamily = OverdriveTheme.font, color = OverdriveTheme.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("${ch.stars} / ${ch.total} stars", fontFamily = OverdriveTheme.font, color = OverdriveTheme.colors.textDim, fontSize = 12.sp)
                        }
                        StarRow(earned = if (ch.stars > 0) 3 else 0)
                    }
                }
            }
        }
    }
}

@Composable
fun MissionSelectScreen(nav: OverdriveNav, chapterId: Int) {
    val chapter = CHAPTERS.firstOrNull { it.id == chapterId }
    OverdriveScaffold(title = chapter?.name ?: "Missions", onBack = { nav.back() }) { mod ->
        Column(mod, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (1..3).forEach { m ->
                val missionId = "ch${chapterId}_m$m"
                OverdrivePanel(Modifier.fillMaxWidth().clickable { nav.go(Routes.MissionDetail(missionId)) }) { inner ->
                    Row(inner.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("MISSION $m", fontFamily = OverdriveTheme.font, color = OverdriveTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Commander stub · preferred vehicle · KOTH", fontFamily = OverdriveTheme.font, color = OverdriveTheme.colors.textDim, fontSize = 12.sp)
                        }
                        StarRow(earned = if (m == 1) 2 else 0)
                    }
                }
            }
        }
    }
}

@Composable
fun MissionDetailScreen(nav: OverdriveNav, missionId: String) = WireframeScreen(
    title = "Mission",
    onBack = { nav.back() },
    subtitle = "Mission $missionId — commander portrait/bio, the 0–3 star objectives (directed tasks), " +
        "preferred track & vehicle. CampaignEngine wires this in Phase 3.",
    actions = listOf(
        NavAction("Briefing", { nav.showOverlay(Overlay.MissionStory("Intel: hold the lead zone for 20s while fending off two rivals.")) }, ButtonAccent.Outline),
        NavAction("Start Mission", { nav.go(Routes.MatchSetup(mode = "King of the Hill", campaignMissionId = missionId)) }, ButtonAccent.Gold),
    ),
)
