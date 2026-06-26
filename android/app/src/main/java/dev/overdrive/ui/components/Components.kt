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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
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
 * Full-bleed Overdrive backdrop — the 4.0.4 violet nebula, painted procedurally (layered radial
 * "clouds" + a vignette) so it needs no bundled art and scales to any size. `heroImage`, if given,
 * overlays carved nebula art (Background/base.png) on top once it's extracted; the procedural ground
 * stays underneath as a guaranteed fallback. Edges stay dark for legibility.
 */
@Composable
fun OverdriveBackground(
    modifier: Modifier = Modifier,
    heroImage: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = OverdriveTheme.colors
    val hero = rememberAsset(heroImage)
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                val span = maxOf(w, h)
                drawRect(colors.background)
                fun cloud(color: Color, cx: Float, cy: Float, rad: Float) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(color, Color.Transparent),
                            center = Offset(cx * w, cy * h),
                            radius = rad * span,
                        )
                    )
                }
                cloud(Color(0x7A5B2A82), 0.30f, 0.28f, 0.85f) // orchid
                cloud(Color(0x5C9B3FB8), 0.74f, 0.40f, 0.62f) // hot magenta
                cloud(Color(0x662A1240), 0.55f, 0.86f, 0.95f) // deep indigo, low
                cloud(Color(0x184FB0FF), 0.84f, 0.14f, 0.55f) // cool holo glow, top-right
                drawRect( // vignette: dark edges, keeps text legible over the bright center
                    Brush.radialGradient(
                        listOf(Color.Transparent, Color(0xCC0C0617)),
                        center = Offset(0.5f * w, 0.40f * h),
                        radius = 1.15f * span,
                    )
                )
            }
    ) {
        if (hero != null) Image(hero, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.6f)
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

/** 4.0.4 back affordance: a rounded translucent square chip with a chevron (replaces "‹ BACK"). */
@Composable
private fun BackChip(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OverdriveTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .width(40.dp)
            .height(36.dp)
            .clip(shape)
            .background(Color(0x14FFFFFF))
            .border(1.dp, colors.panelBorder, shape)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Text("‹", color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
    heroImage: String? = null,
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
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .then(if (outline) Modifier.border(1.5.dp, colors.blue.copy(alpha = 0.85f), shape) else Modifier)
            .background(if (outline) Color(0x14FFFFFF) else fill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            fontFamily = font,
            color = if (outline) colors.blue else Color.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Car/weapon name in the Overdrive racing style: uppercase heavy italic, two-tone split on the
 * camelCase seam (e.g. Ground|shock). [hlColor] tints the tail — defaults to the holo accent; pass a
 * brand color (red / gold / green) per car. Promoted from GarageScreens for reuse across the app.
 */
@Composable
fun RacingName(
    name: String,
    fontSize: Int,
    modifier: Modifier = Modifier,
    hlColor: Color = OverdriveTheme.colors.blue,
) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val seam = name.withIndex().firstOrNull { (i, c) -> i > 0 && c.isUpperCase() }?.index ?: -1
    val (head, tail) = if (seam > 0) name.substring(0, seam) to name.substring(seam) else name to ""
    Row(modifier) {
        Text(
            head.uppercase(), fontFamily = font, color = colors.textPrimary, fontSize = fontSize.sp,
            fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
        )
        if (tail.isNotEmpty()) Text(
            tail.uppercase(), fontFamily = font, color = hlColor, fontSize = fontSize.sp,
            fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
        )
    }
}

/**
 * The recurring 4.0.4 "card": a translucent dark-violet panel with a vertical gradient and a soft
 * light border. The single most-reused container across Overdrive screens.
 */
@Composable
fun OverdrivePanel(
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val colors = OverdriveTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(colors.panel.copy(alpha = 0.82f), colors.surface.copy(alpha = 0.82f))
                )
            )
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
    heroImage: String? = null,
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

/** Themed single-line text field for auth/forms. */
@Composable
fun OverdriveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
) {
    val colors = OverdriveTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.gold,
            unfocusedBorderColor = colors.panelBorder,
            focusedLabelColor = colors.gold,
            unfocusedLabelColor = colors.textDim,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            cursorColor = colors.gold,
        ),
        modifier = modifier,
    )
}

/** Pill showing the player's coin balance (top-right chrome). */
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
