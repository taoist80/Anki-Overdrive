package dev.overdrive.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.overdrive.ui.components.PrimaryButton
import dev.overdrive.ui.theme.OverdriveTheme

/**
 * The Foreground modal layer from the original NavigationController (Background / NavStack /
 * Foreground). Overlays render on top of the whole nav stack and are dismissable. Phase 0 ships the
 * recurring ones as chrome stubs; each gets wired to real data in later phases.
 */
sealed interface Overlay {
    /** Loot-box reveal shown after a race (sb_LootDrop_ViewController); carries the rolled reward. */
    data class LootReveal(
        val coins: Int,
        val itemId: String,
        val itemName: String,
        val rarity: String,
        val rarityColor: Long,
    ) : Overlay

    /** Game mutator / rules picker (MutatorModalViewController). */
    object MutatorPicker : Overlay

    /** Celebration unlock (vehicle / commander) — CelebrationUnlockModal. */
    data class CelebrationUnlock(val what: String) : Overlay

    /** Mission narrative popup (MissionStoryPopup_ViewController). */
    data class MissionStory(val text: String) : Overlay

    /** Generic OK/Cancel alert (NavigationController.ShowAlert). */
    data class Alert(
        val title: String,
        val message: String,
        val confirmLabel: String = "OK",
        val onConfirm: () -> Unit = {},
    ) : Overlay
}

/** Holds the currently-presented overlay. One at a time, matching the original modal layer. */
@Stable
class OverlayController {
    var current by mutableStateOf<Overlay?>(null)
        private set

    fun show(overlay: Overlay) { current = overlay }
    fun dismiss() { current = null }
}

@Composable
fun rememberOverlayController(): OverlayController = remember { OverlayController() }

/** Renders the active overlay above everything else, with a dimming scrim. */
@Composable
fun OverlayHost(controller: OverlayController) {
    val overlay = controller.current
    AnimatedVisibility(
        visible = overlay != null,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xCC05070A))
                // Scrim swallows taps so the screen behind isn't interactive.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* tap-outside is a no-op; use the button to dismiss */ },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            when (val o = controller.current ?: overlay) {
                is Overlay.LootReveal -> OverlayCard(
                    title = "LOOT DROP",
                    accentTitle = "★ ${o.rarity} ★",
                    accentColor = o.rarityColor,
                    message = "+${o.coins} coins" + if (o.itemName.isNotBlank()) "\n${o.itemName}" else "",
                    primaryLabel = "COLLECT",
                    onPrimary = {
                        dev.overdrive.profile.ProfileRepository.addCoins(ctx, o.coins)
                        if (o.itemId.isNotBlank()) dev.overdrive.profile.ProfileRepository.addItem(ctx, o.itemId)
                        controller.dismiss()
                    },
                )
                Overlay.MutatorPicker -> OverlayCard(
                    title = "MUTATORS",
                    message = "Pick rule modifiers for this match (e.g. No Weapons, Triple Speed, " +
                        "Reverse Lap). Selection grid wires in Phase 2.",
                    primaryLabel = "CONFIRM",
                    onPrimary = controller::dismiss,
                )
                is Overlay.CelebrationUnlock -> OverlayCard(
                    title = "UNLOCKED!",
                    accentTitle = o.what.uppercase(),
                    message = "Celebration animation for the newly unlocked content.",
                    primaryLabel = "AWESOME",
                    onPrimary = controller::dismiss,
                )
                is Overlay.MissionStory -> OverlayCard(
                    title = "MISSION BRIEFING",
                    message = o.text,
                    primaryLabel = "CONTINUE",
                    onPrimary = controller::dismiss,
                )
                is Overlay.Alert -> OverlayCard(
                    title = o.title,
                    message = o.message,
                    primaryLabel = o.confirmLabel,
                    onPrimary = { o.onConfirm(); controller.dismiss() },
                )
                null -> Unit
            }
        }
    }
}

@Composable
private fun OverlayCard(
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    accentTitle: String? = null,
    accentColor: Long? = null,
) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val accent = accentColor?.let { Color(it) } ?: colors.gold
    Column(
        Modifier
            .widthIn(max = 460.dp)
            .padding(32.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.panel)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title.uppercase(), fontFamily = font, color = colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        if (accentTitle != null) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.size(width = 120.dp, height = 120.dp).clip(RoundedCornerShape(8.dp)).background(colors.barEmpty), contentAlignment = Alignment.Center) {
                Text("◆", color = accent, fontSize = 56.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(accentTitle, fontFamily = font, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Text(message, fontFamily = font, color = colors.textDim, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(primaryLabel, onPrimary, accent = dev.overdrive.ui.components.ButtonAccent.Gold)
        }
        Spacer(Modifier.height(2.dp))
    }
}
