package dev.overdrive.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.overdrive.data.ContentRepository
import dev.overdrive.data.model.Chapter
import dev.overdrive.data.model.Mission
import dev.overdrive.game.campaign.CampaignEngine
import dev.overdrive.game.race.Rivals
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
import dev.overdrive.ui.theme.rememberAsset

/** Parse a chapter/commander tint hex (#RRGGBB) into a Compose Color, falling back to gold. */
@Composable
private fun tintOf(hex: String): Color {
    val gold = OverdriveTheme.colors.gold
    return remember(hex) { runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(gold) }
}

/**
 * CHAPTER SELECT — the 2.6 Tournament chapter grid. 3×2 of the authentic 2.6 crew banners
 * (`ui/campaign/chapterNN.png`), each tinted to its chapter colour, with star progress and a lock.
 * (Menu entry stays "Campaign"; screen chrome uses 2.6's real labels.)
 */
@Composable
fun ChapterSelectScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    remember { CampaignEngine.ensureLoaded(ctx); 0 }
    val profile = ProfileRepository.profile          // observe progression changes
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val chapters = ContentRepository.chapters

    OverdriveScaffold(
        title = "CHAPTER SELECT",
        onBack = { nav.back() },
        right = { Text("TOURNAMENT  ★ ${profile.totalStars}/${CampaignEngine.maxStars()}", fontFamily = font, color = colors.gold, fontSize = 13.sp) },
    ) { mod ->
        Column(mod.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            chapters.chunked(3).forEach { rowChapters ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowChapters.forEach { ch ->
                        val unlocked = CampaignEngine.isChapterUnlocked(ch)
                        ChapterCard(
                            ch, unlocked, CampaignEngine.chapterStars(ch), CampaignEngine.chapterMaxStars(ch),
                            Modifier.weight(1f),
                        ) {
                            if (unlocked) nav.go(Routes.MissionSelect(ch.id.toIntOrNull() ?: 0))
                            else nav.showOverlay(Overlay.Alert("Locked", "Earn more stars in earlier chapters to unlock this one."))
                        }
                    }
                    repeat(3 - rowChapters.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ChapterCard(ch: Chapter, unlocked: Boolean, stars: Int, maxStars: Int, modifier: Modifier, onClick: () -> Unit) {
    val font = OverdriveTheme.font
    val colors = OverdriveTheme.colors
    val tint = tintOf(ch.tint)
    val n = ch.id.toIntOrNull() ?: 0
    val banner = rememberAsset("ui/campaign/chapter%02d.png".format(n))
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(colors.panel.copy(alpha = 0.65f))
            .background(tint.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .alpha(if (unlocked) 1f else 0.6f),
    ) {
        // crew banner
        Box(Modifier.fillMaxWidth().aspectRatio(1.45f).background(tint.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            if (banner != null) Image(banner, "Chapter $n crew", Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit, alpha = if (unlocked) 1f else 0.5f)
            if (!unlocked) Text("🔒", fontSize = 30.sp)
        }
        // footer
        Column(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("CHAPTER %02d".format(n), fontFamily = font, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(if (unlocked) "★ $stars/$maxStars" else "LOCKED", fontFamily = font, color = if (unlocked) colors.gold else colors.textDim, fontSize = 11.sp)
            }
            // progress bar
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp)).background(Color(0x22FFFFFF))) {
                val frac = if (maxStars > 0) stars.toFloat() / maxStars else 0f
                Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(5.dp).clip(RoundedCornerShape(99.dp)).background(tint))
            }
            Text("${ch.missions.size} MISSIONS", fontFamily = font, color = colors.textDim, fontSize = 10.sp)
        }
    }
}

/**
 * MISSION SELECT — the 2.6 Tournament road-spline rail. Missions are grouped by their real
 * `mission_select_scroll_pos` into rows down a winding road (the chapter tint); missions sharing a
 * scroll position render side-by-side as a branch. Each node is the authentic 2.6 opponent portrait.
 */
@Composable
fun MissionSelectScreen(nav: OverdriveNav, chapterId: Int) {
    val ctx = LocalContext.current
    remember { CampaignEngine.ensureLoaded(ctx); 0 }
    ProfileRepository.profile                          // observe
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val chapter = ContentRepository.chapters.firstOrNull { it.id == chapterId.toString() }
    val tint = tintOf(chapter?.tint ?: "#FFFFFF")
    val missions = chapter?.missions.orEmpty().mapNotNull { ContentRepository.missionsById[it] }
    // group into rows by scroll position (missions at the same position = a branch), ordered
    val rows: List<List<Mission>> = missions.groupBy { (it.scrollPos * 1000).toInt() }
        .toSortedMap().values.toList()

    OverdriveScaffold(
        title = "TOURNAMENT",
        onBack = { nav.back() },
        right = {
            val cs = chapter?.let { CampaignEngine.chapterStars(it) } ?: 0
            val cm = chapter?.let { CampaignEngine.chapterMaxStars(it) } ?: 0
            Text("★ $cs/$cm", fontFamily = font, color = colors.gold, fontSize = 14.sp)
        },
    ) { mod ->
        Column(mod.verticalScroll(rememberScrollState())) {
            Text("CHAPTER %02d".format(chapterId), fontFamily = font, color = tint, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
            if (rows.isEmpty()) Text("No missions", color = colors.textDim, fontFamily = font)
            // the road: a tinted vertical bar with a dashed centerline, drawn behind the node rows
            Box(
                Modifier.fillMaxWidth().drawBehind {
                    val cx = size.width / 2f
                    val w = 46.dp.toPx()
                    drawRoundRect(
                        color = tint.copy(alpha = 0.14f),
                        topLeft = Offset(cx - w / 2, 0f),
                        size = androidx.compose.ui.geometry.Size(w, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2, w / 2),
                    )
                    // tint edges
                    listOf(cx - w / 2, cx + w / 2).forEach { x ->
                        drawLine(tint.copy(alpha = 0.7f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
                    }
                    // dashed white centerline
                    drawLine(
                        Color(0x88FFFFFF), Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16.dp.toPx(), 14.dp.toPx())),
                    )
                },
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    rows.forEachIndexed { i, row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = if (row.size > 1) Arrangement.SpaceEvenly else Arrangement.Center,
                        ) {
                            row.forEach { m -> MissionNode(m, isBoss = i == rows.lastIndex && row.size == 1) { mid ->
                                if (CampaignEngine.isMissionUnlocked(mid)) nav.go(Routes.MissionDetail(mid))
                            } }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MissionNode(mission: Mission, isBoss: Boolean, onClick: (String) -> Unit) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val unlocked = CampaignEngine.isMissionUnlocked(mission.id)
    val stars = CampaignEngine.starsFor(mission.id)
    val c26 = ContentRepository.commander26(mission.opponent)
    val portrait = rememberAsset(c26?.portraitAsset)
    val ring = when {
        !unlocked -> colors.textDim.copy(alpha = 0.4f)
        stars > 0 -> colors.success
        else -> colors.blue
    }
    val sz = if (isBoss) 92.dp else 74.dp
    Column(
        Modifier.clickable(enabled = unlocked) { onClick(mission.id) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(sz).clip(RoundedCornerShape(percent = 50))
                .background(Color(0xFF0D0719))
                .drawBehind { drawCircle(ring, style = Stroke(width = 3.dp.toPx())) }
                .alpha(if (unlocked) 1f else 0.5f),
            contentAlignment = Alignment.Center,
        ) {
            if (unlocked && portrait != null) Image(portrait, c26?.name, Modifier.fillMaxSize().clip(RoundedCornerShape(percent = 50)), contentScale = ContentScale.Crop)
            else Text(if (unlocked) "?" else "🔒", color = colors.textDim, fontSize = 22.sp)
        }
        Spacer(Modifier.height(5.dp))
        Text(if (unlocked) (c26?.name ?: mission.opponent) else "LOCKED", fontFamily = font,
            color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (unlocked) {
            val mode = mission.gameType.uppercase()
            Text(mode, fontFamily = font, color = if ("BATTLE" in mode) Color(0xFFFF9BB0) else colors.blue, fontSize = 9.sp, letterSpacing = 1.sp)
            StarRow(earned = stars)
        }
    }
}

/**
 * MISSION DETAIL — opponent portrait + the real 2.6 "ABOUT OPPONENT" bio + "FAVORITE SUPERCAR",
 * mode/track, star objectives, and the briefing / start actions.
 */
@Composable
fun MissionDetailScreen(nav: OverdriveNav, missionId: String) {
    val ctx = LocalContext.current
    remember { CampaignEngine.ensureLoaded(ctx); 0 }
    ProfileRepository.profile                          // observe
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val mission = ContentRepository.missionsById[missionId]
    val c26 = ContentRepository.commander26(mission?.opponent)
    val legacy = mission?.let { ContentRepository.commander(it.opponent) }   // for FAVORITE SUPERCAR
    val name = c26?.name ?: legacy?.friendlyName ?: mission?.opponent ?: "?"
    val portrait = rememberAsset(c26?.portraitAsset)
    val objectives = CampaignEngine.objectivesFor(missionId)
    val favCar = legacy?.preferenceVehicle?.takeIf { it.isNotBlank() }

    OverdriveScaffold(title = name.uppercase(), onBack = { nav.back() }) { mod ->
        Column(mod.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OverdrivePanel(Modifier.fillMaxWidth()) { inner ->
                Row(inner.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(110.dp).clip(RoundedCornerShape(12.dp)).background(colors.blue.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                        if (portrait != null) Image(portrait, "Opponent", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("ABOUT OPPONENT", fontFamily = font, color = colors.gold, fontSize = 11.sp, letterSpacing = 2.sp)
                        Text(name, fontFamily = font, color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        if (!c26?.bio.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(c26!!.bio, fontFamily = font, color = colors.textDim, fontSize = 12.sp)
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                InfoCol("MODE", mission?.gameType?.uppercase() ?: "?")
                InfoCol("TRACK", mission?.preferenceTrack?.substringAfterLast('_')?.let { "TRACK $it" } ?: "?")
                if (favCar != null) InfoCol("FAVORITE SUPERCAR", favCar.uppercase())
            }

            Text("STAR OBJECTIVES", fontFamily = font, color = colors.gold, fontSize = 12.sp, letterSpacing = 2.sp)
            objectives.forEachIndexed { _, o ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (o.earned) "★" else "☆", color = if (o.earned) colors.gold else colors.barEmpty, fontSize = 22.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(o.description, fontFamily = font, color = colors.textPrimary, fontSize = 15.sp)
                        Text("+${o.rewardPoints} coins", fontFamily = font, color = colors.textDim, fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(
                    "About", { nav.showOverlay(Overlay.MissionStory(c26?.bio?.ifBlank { null } ?: "Defeat $name on ${mission?.preferenceTrack ?: "the track"}.")) },
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

@Composable
private fun InfoCol(label: String, value: String) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    Column {
        Text(label, fontFamily = font, color = colors.textDim, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Text(value, fontFamily = font, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * TOURNAMENT LADDER (Feature 4) — a standalone bracket of AI commanders to beat in sequence, distinct from
 * the campaign missions. Each rung is one commander; difficulty ramps by tier. Beating the frontier rung
 * advances [ProfileRepository.profile.ladderRung] (in the race results), unlocking the next. Cleared rungs
 * are checked, the frontier rung is playable, later rungs are locked.
 */
@Composable
fun TournamentLadderScreen(nav: OverdriveNav) {
    val ctx = LocalContext.current
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    remember { CampaignEngine.ensureLoaded(ctx); ProfileRepository.load(ctx); 0 }
    val rungs = remember { Rivals.ladder() }
    val progress = ProfileRepository.profile.ladderRung

    OverdriveScaffold(
        title = "Tournament Ladder", onBack = { nav.back() },
        right = { Text("${progress.coerceAtMost(rungs.size)} / ${rungs.size}", fontFamily = font, color = colors.gold, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
    ) { mod ->
        if (rungs.isEmpty()) {
            Box(mod.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No commanders found.", fontFamily = font, color = colors.textDim)
            }
            return@OverdriveScaffold
        }
        Column(mod.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (progress >= rungs.size) "Ladder complete — you've beaten every commander."
                else "Beat each commander to climb the ladder. Next up: rung ${progress + 1}.",
                fontFamily = font, color = colors.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp),
            )
            rungs.forEachIndexed { i, p ->
                val cleared = i < progress
                val current = i == progress
                val locked = i > progress
                val accent = when { current -> colors.gold; cleared -> colors.success; else -> colors.panelBorder }
                val portrait = rememberAsset(ContentRepository.commander26(p.commanderId)?.portraitAsset)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(colors.panel.copy(alpha = if (locked) 0.3f else 0.7f))
                        .border(if (current) 2.dp else 1.dp, accent.copy(alpha = if (locked) 0.4f else 0.85f), RoundedCornerShape(12.dp))
                        .alpha(if (locked) 0.55f else 1f)
                        .clickable(enabled = current) { nav.go(Routes.MatchSetup(mode = "race", ladderRung = i)) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("${i + 1}", fontFamily = font, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                    if (portrait != null) Image(portrait, p.displayName, Modifier.size(48.dp).clip(RoundedCornerShape(percent = 50)), contentScale = ContentScale.Crop)
                    else Box(Modifier.size(48.dp).clip(RoundedCornerShape(percent = 50)).background(colors.panelBorder))
                    Column(Modifier.weight(1f)) {
                        Text((p.displayName ?: "?").uppercase(), fontFamily = font, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("TIER ${p.tier}", fontFamily = font, color = colors.textDim, fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                    Text(when { cleared -> "✓ BEATEN"; current -> "▶ RACE"; else -> "LOCKED" },
                        fontFamily = font, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
    }
}
