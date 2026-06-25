package dev.overdrive.ui.components

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.overdrive.ui.theme.OverdriveTheme
import dev.overdrive.ui.theme.rememberAsset

/** Action shown as a button on a wireframe screen (label → navigation lambda). */
data class NavAction(
    val label: String,
    val onClick: () -> Unit,
    val accent: ButtonAccent = ButtonAccent.Outline,
)

enum class ButtonAccent { Blue, Gold, Orange, Outline }

/**
 * Full-bleed Overdrive backdrop. `heroImage` paints the global background art (e.g.
 * "ui/global-background.png"); otherwise a dark base with the hex grid tiled accent on top.
 */
@Composable
fun OverdriveBackground(
    modifier: Modifier = Modifier,
    heroImage: String? = "ui/global-background.png",
    content: @Composable () -> Unit,
) {
    val colors = OverdriveTheme.colors
    val hero = rememberAsset(heroImage)
    val hex = rememberAsset("ui/ui_BackgroundHex.png")
    Box(modifier.fillMaxSize().background(colors.background)) {
        when {
            hero != null -> Image(hero, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            hex != null -> Image(hex, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.20f)
        }
        // Darkening scrim so foreground text/panels stay legible over busy art.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xAA000000), Color(0x55000000), Color(0xCC000000)))
            )
        )
        content()
    }
}

/** Top bar: back chevron (optional), centered title, optional right slot (coins / actions). */
@Composable
fun OverdriveTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    right: (@Composable () -> Unit)? = null,
) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    Box(Modifier.fillMaxWidth().height(56.dp)) {
        if (onBack != null) {
            BackChip(onBack, Modifier.align(Alignment.CenterStart))
        }
        Text(
            title.uppercase(),
            fontFamily = font,
            color = colors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.Center),
        )
        if (right != null) Box(Modifier.align(Alignment.CenterEnd)) { right() }
    }
}

@Composable
private fun BackChip(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OverdriveTheme.colors
    Row(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onBack)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("‹", color = colors.gold, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Text("BACK", color = colors.textDim, fontSize = 13.sp, letterSpacing = 1.sp)
    }
}

/**
 * Standard screen scaffold: backdrop + top bar + edge-to-edge-aware content. Respects the status
 * bar inset so titles don't collide with the system bar when not in immersive mode.
 */
@Composable
fun OverdriveScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    heroImage: String? = "ui/global-background.png",
    right: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 28.dp),
    content: @Composable (Modifier) -> Unit,
) {
    OverdriveBackground(heroImage = heroImage) {
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Column(Modifier.fillMaxSize().padding(top = top)) {
            Box(Modifier.padding(horizontal = 12.dp)) {
                OverdriveTopBar(title, onBack, right)
            }
            content(Modifier.fillMaxSize().padding(contentPadding))
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: ButtonAccent = ButtonAccent.Blue,
    enabled: Boolean = true,
) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val fill = when (accent) {
        ButtonAccent.Blue -> colors.blue
        ButtonAccent.Gold -> colors.gold
        ButtonAccent.Orange -> colors.orange
        ButtonAccent.Outline -> Color.Transparent
    }
    val outline = accent == ButtonAccent.Outline
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .then(if (outline) Modifier.border(1.5.dp, colors.gold.copy(alpha = 0.8f), shape) else Modifier)
            .background(if (outline) Color(0x22FFFFFF) else fill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            fontFamily = font,
            color = if (outline) colors.gold else Color.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** A bordered translucent panel — the recurring "card" container across Overdrive screens. */
@Composable
fun OverdrivePanel(
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val colors = OverdriveTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .clip(shape)
            .background(colors.panel.copy(alpha = 0.85f))
            .border(1.dp, colors.panelBorder, shape)
    ) {
        content(Modifier.padding(18.dp))
    }
}

/** ATTACK / SUPPORT style 3-pip stat bar (promoted from the original CarSelectScreen). */
@Composable
fun StatBars(label: String, value: Int, max: Int = 3) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), fontFamily = font, color = colors.textDim, fontSize = 12.sp, letterSpacing = 1.sp)
        Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(max) { i ->
                Box(
                    Modifier.width(26.dp).height(12.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (i < value) colors.gold else colors.barEmpty)
                )
            }
        }
    }
}

/** 0–3 star row for campaign missions. */
@Composable
fun StarRow(earned: Int, total: Int = 3, size: Int = 22) {
    val colors = OverdriveTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total) { i ->
            Text(
                "★",
                color = if (i < earned) colors.gold else colors.barEmpty,
                fontSize = size.sp,
            )
        }
    }
}

/** Mode tile backed by the extracted ui_selectMode_* art, with a caption. */
@Composable
fun ModeTile(
    title: String,
    image: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accent: Color? = null,
) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val art = rememberAsset(image)
    val edge = accent ?: colors.panelBorder
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .clip(shape)
            .border(1.dp, edge.copy(alpha = 0.8f), shape)
            .background(colors.panel.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (accent != null) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Spacer(Modifier.height(10.dp))
        }
        if (art != null) {
            Image(art, title, Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 150.dp), contentScale = ContentScale.Fit)
        } else {
            Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(6.dp)).background(colors.barEmpty))
        }
        Spacer(Modifier.height(10.dp))
        Text(title.uppercase(), fontFamily = font, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        if (subtitle != null) {
            Text(subtitle, fontFamily = font, color = colors.textDim, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

/** Small "WIREFRAME" badge so it's obvious which screens are not yet wired to real data. */
@Composable
fun WireframeBadge(modifier: Modifier = Modifier) {
    val colors = OverdriveTheme.colors
    Box(
        modifier
            .clip(RoundedCornerShape(3.dp))
            .background(colors.orange.copy(alpha = 0.18f))
            .border(1.dp, colors.orange.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text("WIREFRAME", color = colors.orange, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * The workhorse for Phase 0: a chrome-complete stub screen. Renders the Overdrive backdrop + top
 * bar, an optional description, an optional custom body, then a column of navigation actions that
 * walk to child screens — so the whole app is navigable before any real data is wired in.
 */
@Composable
fun WireframeScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    heroImage: String? = "ui/global-background.png",
    actions: List<NavAction> = emptyList(),
    body: (@Composable () -> Unit)? = null,
) {
    OverdriveScaffold(title = title, onBack = onBack, heroImage = heroImage) { mod ->
        Column(
            mod.verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WireframeBadge()
            if (subtitle != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    subtitle,
                    fontFamily = OverdriveTheme.font,
                    color = OverdriveTheme.colors.textDim,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 520.dp),
                )
            }
            if (body != null) {
                Spacer(Modifier.height(20.dp))
                body()
            }
            if (actions.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Column(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    actions.forEach { a ->
                        PrimaryButton(a.label, a.onClick, Modifier.fillMaxWidth(), accent = a.accent)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Pill showing the player's coin balance (top-right chrome). Stubbed value for now. */
@Composable
fun CoinPill(amount: Int = 0, font: FontFamily = OverdriveTheme.font) {
    val colors = OverdriveTheme.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x33000000))
            .border(1.dp, colors.gold.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(7.dp)).background(colors.gold))
        Text("$amount", fontFamily = font, color = colors.gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
